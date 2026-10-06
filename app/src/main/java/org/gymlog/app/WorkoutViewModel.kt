package org.gymlog.app

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class LoggerState(
    val data: TrainingData = TrainingData(),
    val settings: Settings = Settings(),
    val loading: Boolean = true,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val savingSet: Boolean = false,
    val weight: String = "",
    val reps: String = "",
    val error: String? = null,
    /** A completed action worth confirming, such as a written backup. */
    val notice: String? = null,
    val undoId: Long? = null,
    val detailId: Long? = null,
    val historyBefore: Long? = null,
    /** When on, the next saves are drop segments of the latest set of this exercise. */
    val dropMode: Boolean = false,
    val progress: Progress? = null,
) {
    val unit: WeightUnit get() = settings.unit
    val selectedEquipment: Equipment? get() = data.equipment.find { it.id == data.active?.selectedEquipment }
    val selectedExercise: Exercise? get() = data.exercises.find { it.id == selectedEquipment?.exerciseId }
    val partnerEquipment: Equipment? get() = data.equipment.find { it.id == data.active?.partnerEquipment }
    val validSet: Boolean get() = selectedEquipment != null && unit.parse(weight) != null && reps.toIntOrNull() in 1..MAX_REPS
}

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WorkoutStore(application)
    private val settingsStore = SettingsStore(application)
    // Preferences are a few bytes and are needed before the first frame to pick the theme.
    private val mutableState = MutableStateFlow(LoggerState(settings = settingsStore.load()))
    val state = mutableState.asStateFlow()
    private val drafts = mutableMapOf<Long, Pair<String, String>>()
    /** Kept apart from [state] so a rest change never races with a database operation's result. */
    private val mutableRestEndsAt = MutableStateFlow(RestTimer.endsAt(application))
    val restEndsAt = mutableRestEndsAt.asStateFlow()

    init { reload() }

    fun startRest() {
        val settings = state.value.settings
        if (!settings.restEnabled) return
        scheduleRest(System.currentTimeMillis() + settings.restSeconds * 1000L)
    }
    fun extendRest(seconds: Int) {
        val endsAt = mutableRestEndsAt.value ?: return
        scheduleRest(maxOf(endsAt, System.currentTimeMillis()) + seconds * 1000L)
    }
    fun stopRest() {
        RestTimer.cancel(getApplication())
        mutableRestEndsAt.value = null
    }
    /** Called by the visible countdown when it reaches zero; the alarm may already have signalled. */
    fun restElapsed(endsAt: Long) {
        RestTimer.expire(getApplication(), endsAt)
        mutableRestEndsAt.compareAndSet(endsAt, null)
    }
    private fun scheduleRest(endsAt: Long) {
        RestTimer.schedule(getApplication(), endsAt)
        mutableRestEndsAt.value = endsAt
    }

    fun weight(value: String) { if (!state.value.busy || state.value.savingSet) mutableState.update { it.copy(weight = value.take(12)) } }
    fun reps(value: String) { if (!state.value.busy || state.value.savingSet) mutableState.update { it.copy(reps = value.take(3)) } }
    fun stepLoad(steps: Int) {
        val current = state.value
        steppedLoad(current.weight, current.settings.loadStep, steps)?.let(::weight)
    }
    fun dismissError() { if (state.value.ready) mutableState.update { it.copy(error = null) } }
    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }
    fun toggleDrop() { if (!state.value.busy) mutableState.update { it.copy(dropMode = !it.dropMode) } }

    fun updateSettings(settings: Settings) {
        val current = state.value
        if (current.busy) return
        settingsStore.save(settings)
        if (!settings.restEnabled) stopRest()
        if (settings.unit == current.unit) {
            mutableState.value = current.copy(settings = settings)
            return
        }
        // Unsaved text was typed in the previous unit, so convert the visible entry and drop other drafts.
        drafts.clear()
        val grams = current.unit.parse(current.weight)
        mutableState.value = current.copy(settings = settings, weight = grams?.let(settings.unit::format) ?: current.weight)
    }

    fun reload() = mutate(allowUnready = true) { current ->
        val data = store.load(current.historyBefore, current.detailId)
        val next = current.copy(data = data, ready = true, loading = false)
        if (current.data.active?.id == data.active?.id && current.selectedEquipment?.id == next.selectedEquipment?.id && current.data.active != null) next
        else next.prefilled()
    }

    fun start(splitDayId: Long? = null) = mutate {
        store.start(splitDayId)
        it.copy(data = store.load(), detailId = null, historyBefore = null, weight = "", reps = "", undoId = null, dropMode = false).prefilled()
    }

    fun finish() = mutate { current ->
        val active = requireNotNull(current.data.active)
        store.finish(active.id)
        stopRest()
        current.copy(data = store.load(detailId = active.id), detailId = active.id, historyBefore = null, weight = "", reps = "", undoId = null, dropMode = false)
    }

    fun addSplitTemplate(template: SplitTemplate) = catalogChange { store.addSplitDays(template.days) }
    fun addSplitDay(name: String, muscles: List<String>, exerciseIds: List<Long>) = catalogChange { store.addSplitDay(name, muscles, exerciseIds) }
    fun updateSplitDay(id: Long, name: String, muscles: List<String>, exerciseIds: List<Long>) =
        catalogChange { store.updateSplitDay(id, name, muscles, exerciseIds) }
    fun deleteSplitDay(id: Long) = catalogChange { store.deleteSplitDay(id) }
    fun moveSplitDay(id: Long, offset: Int) = catalogChange { store.moveSplitDay(id, offset) }
    fun clearSplit() = catalogChange { store.clearSplit() }

    fun openWorkout(id: Long) = mutate { it.copy(data = store.load(it.historyBefore, id), detailId = id) }
    fun closeWorkout() = mutate { it.copy(data = store.load(it.historyBefore), detailId = null) }
    fun deleteWorkout(id: Long) = mutate {
        store.deleteWorkout(id)
        it.copy(data = store.load(it.historyBefore), detailId = null)
    }
    fun olderHistory() {
        val before = state.value.data.workouts.filter { it.endedAt != null }.minOfOrNull { it.id } ?: return
        mutate { it.copy(data = store.load(before), historyBefore = before, detailId = null) }
    }
    fun newestHistory() = mutate { it.copy(data = store.load(), historyBefore = null, detailId = null) }
    fun feedback(feeling: String?, note: String) = mutate {
        store.feedback(requireNotNull(it.detailId), feeling, note)
        it.copy(data = store.load(it.historyBefore, it.detailId))
    }

    fun openProgress(equipmentId: Long) = mutate { it.copy(progress = store.progress(equipmentId)) }
    fun closeProgress() { mutableState.update { it.copy(progress = null) } }

    fun exportBackup(target: Uri) = mutate {
        val resolver = getApplication<Application>().contentResolver
        val out = resolver.openOutputStream(target, "wt") ?: throw InputConflict("Couldn't write to that location. Nothing was exported.")
        try { out.use { stream -> Backup.export(store, it.settings, stream) } }
        catch (_: IOException) { throw InputConflict("Couldn't finish writing the backup. Try another location.") }
        it.copy(notice = "Backup saved.")
    }

    fun importBackup(source: Uri) = mutate {
        val resolver = getApplication<Application>().contentResolver
        val input = try { resolver.openInputStream(source) } catch (_: IOException) { null }
            ?: throw InputConflict("Couldn't open that file. Nothing was changed.")
        val settings = input.use { stream -> Backup.import(store, stream) }
        settingsStore.save(settings)
        stopRest()
        drafts.clear()
        it.copy(data = store.load(), settings = settings, detailId = null, historyBefore = null, undoId = null, dropMode = false, progress = null,
            notice = "Backup restored.").prefilled()
    }

    fun toggleFavorite(exercise: Exercise) = updateExercise(exercise.id, exercise.name, !exercise.favorite, exercise.hidden)
    fun updateExercise(id: Long, name: String, favorite: Boolean, hidden: Boolean) = catalogChange { store.updateExercise(id, name, favorite, hidden) }
    fun deleteExercise(id: Long) = catalogChange { store.deleteExercise(id) }
    fun updateEquipment(id: Long, name: String, hidden: Boolean) = catalogChange { store.updateEquipment(id, name, hidden) }
    fun deleteEquipment(id: Long) = catalogChange { store.deleteEquipment(id) }

    private fun catalogChange(change: () -> Unit) = mutate {
        change()
        val next = it.copy(data = store.load(it.historyBefore, it.detailId))
        if (next.selectedEquipment?.id == it.selectedEquipment?.id) next else next.prefilled()
    }

    /** The equipment last used for [exercise], else its first visible option. */
    private fun preferredEquipment(data: TrainingData, exercise: Exercise): Long {
        val all = data.equipment.filter { it.exerciseId == exercise.id }
        val options = all.filterNot { it.hidden }.ifEmpty { all }
        val optionIds = options.mapTo(HashSet()) { it.id }
        return data.latestSets.filter { it.equipmentId in optionIds }.maxByOrNull { it.id }?.equipmentId ?: options.first().id
    }

    fun chooseExercise(exercise: Exercise) = select(preferredEquipment(state.value.data, exercise))

    fun select(equipmentId: Long) = changeSelection { equipmentId }
    fun addExercise(name: String, muscle: String) = changeSelection { store.addExercise(name, muscle) }
    fun addEquipment(name: String, kind: LoadKind, implements: Int) = changeSelection {
        store.addEquipment(requireNotNull(it.selectedExercise).id, name, kind, implements)
    }

    /** Starts a superset of the current exercise with [exercise]; logging then alternates between the two. */
    fun pairWith(exercise: Exercise) {
        val current = state.value
        val workout = current.data.active ?: return
        val partner = preferredEquipment(current.data, exercise)
        mutate { store.pair(workout.id, partner); it.copy(data = store.load(it.historyBefore, it.detailId)) }
    }

    fun endSuperset() {
        val workout = state.value.data.active ?: return
        mutate { store.pair(workout.id, null); it.copy(data = store.load(it.historyBefore, it.detailId)) }
    }

    private fun changeSelection(equipment: (LoggerState) -> Long) {
        val current = state.value
        if (current.busy || !current.ready || current.data.active == null) return
        current.selectedEquipment?.let { drafts[it.id] = current.weight to current.reps }
        mutate {
            val id = equipment(it)
            store.select(requireNotNull(it.data.active).id, id)
            val next = it.copy(data = store.load(it.historyBefore, it.detailId), dropMode = false).prefilled()
            drafts[id]?.let { draft -> next.copy(weight = draft.first, reps = draft.second) } ?: next
        }
    }

    fun save() {
        val current = state.value
        val grams = current.unit.parse(current.weight) ?: return
        val reps = current.reps.toIntOrNull()?.takeIf { it in 1..MAX_REPS } ?: return
        val workout = current.data.active ?: return
        val equipment = current.selectedEquipment ?: return
        mutate(savingSet = true) {
            val saved = store.saveSet(workout.id, equipment.id, grams, reps, drop = it.dropMode)
            // In a superset, rest after the round (both halves), not between its two sets.
            if (it.settings.restEnabled && it.settings.restAutoStart && (workout.partnerEquipment == null || saved.completedRound || it.dropMode)) {
                scheduleRest(System.currentTimeMillis() + it.settings.restSeconds * 1000L)
            }
            val next = it.copy(data = store.load(it.historyBefore, it.detailId), undoId = saved.id)
            if (next.selectedEquipment?.id == equipment.id) next
            else drafts[next.selectedEquipment?.id]?.let { draft -> next.copy(weight = draft.first, reps = draft.second) } ?: next.prefilled()
        }
    }

    fun edit(id: Long, grams: Int, reps: Int) = mutate {
        store.editSet(id, grams, reps)
        it.copy(data = store.load(it.historyBefore, it.detailId))
    }

    fun delete(id: Long) = removeSet(id, undo = false)
    fun undo(id: Long) = removeSet(id, undo = true)

    private fun removeSet(id: Long, undo: Boolean) = mutate {
        store.deleteSet(id, undo)
        val next = it.copy(data = store.load(it.historyBefore, it.detailId), undoId = null)
        if (next.selectedEquipment?.id == it.selectedEquipment?.id) next else next.prefilled()
    }

    private fun LoggerState.prefilled(): LoggerState {
        val last = data.latestSets.find { it.equipmentId == selectedEquipment?.id }
        return copy(weight = last?.let { unit.format(it.grams) } ?: "", reps = last?.reps?.toString() ?: "")
    }

    private fun mutate(allowUnready: Boolean = false, savingSet: Boolean = false, operation: (LoggerState) -> LoggerState) {
        val current = state.value
        if (current.busy || (!current.ready && !allowUnready)) return
        mutableState.value = current.copy(busy = true, savingSet = savingSet, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    operation(current).also { GymWidget.publish(getApplication(), GymWidget.snapshot(it.data)) }
                }
                if (current.data.active?.id != result.data.active?.id) drafts.clear()
                val latest = mutableState.value
                // Values typed while a save was committing are kept, unless the save moved on to the other half of a superset.
                val keepTyped = savingSet && result.selectedEquipment?.id == current.selectedEquipment?.id
                mutableState.value = result.copy(busy = false, savingSet = false, error = null,
                    weight = if (keepTyped) latest.weight else result.weight,
                    reps = if (keepTyped) latest.reps else result.reps,
                    dropMode = if (savingSet) latest.dropMode else result.dropMode)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is InputConflict) {
                    mutableState.update { it.copy(busy = false, savingSet = false, error = error.message) }
                    return@launch
                }
                // Do not put workout content or raw database exceptions into logs or dialogs.
                Log.e("GymLog", "Workout operation failed: ${error.javaClass.simpleName}")
                mutableState.update { it.copy(busy = false, savingSet = false, loading = false, ready = false,
                    error = "Couldn't access your workout. Check available storage, then retry loading. Your saved data has not been reset.") }
            }
        }
    }

    override fun onCleared() {
        // SQLite must not close underneath a transaction completing during cancellation.
        viewModelScope.coroutineContext[Job]?.invokeOnCompletion { store.close() }
    }
}
