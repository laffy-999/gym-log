package org.gymlog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Lime = Color(0xFFCCF58A)
private val Ink = Color(0xFF111412)

private val DarkColors = darkColorScheme(
    primary = Lime, onPrimary = Ink, primaryContainer = Lime, onPrimaryContainer = Ink,
    secondaryContainer = Color(0xFF2F3B33), onSecondaryContainer = Color(0xFFE6EBE7),
    background = Color(0xFF111412), onBackground = Color(0xFFE6EBE7), surface = Color(0xFF1C211E), onSurface = Color(0xFFE6EBE7),
    onSurfaceVariant = Color(0xFF9AA79F), outline = Color(0xFF5B665F), outlineVariant = Color(0xFF2E3631),
    surfaceContainerLowest = Color(0xFF111412), surfaceContainerLow = Color(0xFF1C211E), surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF1C211E), surfaceContainerHighest = Color(0xFF262C28),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF173F35), onPrimary = Color.White, primaryContainer = Lime, onPrimaryContainer = Ink,
    secondaryContainer = Color(0xFFE5EDDE), onSecondaryContainer = Color(0xFF192E28),
    background = Color(0xFFF5F5EF), onBackground = Color(0xFF192E28), surface = Color.White, onSurface = Color(0xFF192E28),
    onSurfaceVariant = Color(0xFF596A61), outline = Color(0xFF8A968F), outlineVariant = Color(0xFFE3E8DF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F5EF), surfaceContainer = Color(0xFFF5F5EF),
    surfaceContainerHigh = Color(0xFFEFF1EA), surfaceContainerHighest = Color(0xFFE8EBE3),
)

private val Muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(dark = true)
        setContent { GymLogApp() }
    }

    override fun onStart() { super.onStart(); RestTimer.appVisible = true }
    override fun onStop() { RestTimer.appVisible = false; super.onStop() }

    fun applySystemBars(dark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

@Composable
private fun GymLogApp(model: WorkoutViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val dark = when (state.settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val activity = LocalActivity.current as? MainActivity
    LaunchedEffect(dark) { activity?.applySystemBars(dark) }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) { GymLog(model, state) }
}

@Composable
private fun GymLog(model: WorkoutViewModel, state: LoggerState) {
    val active = state.data.active
    val unit = state.unit
    var picker by rememberSaveable { mutableStateOf(false) }
    var pairPicker by rememberSaveable { mutableStateOf(false) }
    var equipmentPicker by rememberSaveable { mutableStateOf(false) }
    var finishConfirm by rememberSaveable { mutableStateOf(false) }
    var deleteWorkoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPlates by rememberSaveable { mutableStateOf(false) }
    var settingsMuscle by rememberSaveable { mutableStateOf(muscleGroups.first()) }
    var managedExercise by rememberSaveable { mutableStateOf<Long?>(null) }
    var managedEquipment by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val restEndsAt by model.restEndsAt.collectAsStateWithLifecycle()
    LaunchedEffect(restEndsAt) {
        val end = restEndsAt ?: return@LaunchedEffect
        delay((end - System.currentTimeMillis()).coerceAtLeast(0))
        model.restElapsed(end)
    }
    val suggestedDay = state.data.nextDay
    var chosenDayId by rememberSaveable(suggestedDay?.id) { mutableStateOf(suggestedDay?.id) }
    val detailId = state.detailId
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val focus = LocalFocusManager.current
    val detail = state.data.workouts.find { it.id == detailId }
    val progress = state.progress
    val listState = rememberLazyListState()
    LaunchedEffect(active?.id, detailId, showSettings, progress?.equipmentId) { listState.scrollToItem(0) }
    BackHandler(showSettings) { showSettings = false }
    BackHandler(!showSettings && progress != null) { model.closeProgress() }
    BackHandler(!showSettings && progress == null && detailId != null) { model.closeWorkout() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        if (!showSettings && progress == null && state.ready && detail == null && active != null && state.selectedEquipment != null) {
            Surface(shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(onClick = model::save, enabled = state.validSet && !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) {
                        Text(if (state.dropMode) "Save drop" else "Save set", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (state.busy) Text("Saving…", color = Muted, style = MaterialTheme.typography.bodySmall)
                        else RestControls(restEndsAt, state.settings, onStart = model::startRest, onExtend = { model.extendRest(15) }, onStop = model::stopRest)
                        TextButton(onClick = { state.undoId?.let(model::undo) }, enabled = !state.busy && state.undoId != null) { Text("Undo") }
                    }
                }
            }
        }
    }) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (!state.ready && state.data.exercises.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Couldn't load data. Nothing was deleted.", Modifier.padding(bottom = 16.dp))
                Button(onClick = model::reload, enabled = !state.busy) { Text("Retry") }
            }
        } else {
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize().padding(padding)
                    .then(if (detail != null || showSettings) Modifier.imePadding() else Modifier),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showSettings) {
                    item { TextButton(onClick = { showSettings = false }) { Text("‹ Back") } }
                    settingsItems(state, settingsMuscle, onMuscle = { settingsMuscle = it }, onSettings = model::updateSettings,
                        onToggleFavorite = model::toggleFavorite, onOpenExercise = { managedExercise = it },
                        onAddTemplate = model::addSplitTemplate, onEditDay = { editingDay = it }, onMoveDay = model::moveSplitDay, onClearSplit = model::clearSplit,
                        onExport = model::exportBackup, onImport = model::importBackup)
                } else if (progress != null) {
                    item { TextButton(onClick = model::closeProgress) { Text("‹ Back") } }
                    progressItems(progress, state.data, unit, enabled = !state.busy, onEquipment = model::openProgress)
                } else if (detail != null) {
                    val sets = state.data.sets.filter { it.workoutId == detail.id }
                    item { TextButton(onClick = model::closeWorkout, enabled = !state.busy) { Text("‹ Back") } }
                    item { WorkoutReview(detail, state.data.totals[detail.id] ?: WorkoutTotals(), unit, state.busy, model::feedback) }
                    item { SectionTitle("Sets") }
                    items(setRows(sets, newestFirst = false), key = { it.set.id }) { row ->
                        SetRow(row, state.data, unit, enabled = !state.busy) { editingId = row.set.id }
                    }
                    if (sets.isEmpty()) item { Text("No sets", color = Muted) }
                    item {
                        TextButton(onClick = { deleteWorkoutConfirm = true }, enabled = !state.busy) {
                            Text("Delete workout", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else if (active == null) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showSettings = true }, enabled = !state.busy) { Text("Settings") }
                        }
                    }
                    val days = state.data.splitDays
                    val chosenDay = days.find { it.id == chosenDayId }
                    if (days.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(days, key = { it.id }) { day ->
                                    FilterChip(selected = chosenDayId == day.id, onClick = { chosenDayId = day.id },
                                        label = { Text(if (day.id == suggestedDay?.id) "${day.name} (next)" else day.name) })
                                }
                                item { FilterChip(selected = chosenDay == null, onClick = { chosenDayId = null }, label = { Text("Free") }) }
                            }
                            Text(chosenDay?.let { day -> dayExerciseNames(day, state.data).ifEmpty { day.muscles }.joinToString(" · ") } ?: "Any muscle group",
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        Button(onClick = { model.start(chosenDay?.id) }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) {
                            Text(chosenDay?.let { "Start ${it.name}" } ?: "Start workout", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                    }
                    if (state.data.weekSets.isNotEmpty() || state.data.lastWeekSets.isNotEmpty()) item { WeekCard(state.data) }
                    item { SectionTitle("History") }
                    val history = state.data.workouts.filter { it.endedAt != null }
                    if (history.isEmpty()) item { Text("No workouts yet", color = Muted) }
                    items(history, key = { it.id }) { workout ->
                        HistoryCard(workout, state.data, enabled = !state.busy) { model.openWorkout(workout.id) }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.historyBefore != null) TextButton(onClick = model::newestHistory, enabled = !state.busy) { Text("Newest") }
                            if (state.data.hasOlder) TextButton(onClick = model::olderHistory, enabled = !state.busy) { Text("Older") }
                        }
                    }
                } else {
                    val sessionSets = state.data.sets.filter { it.workoutId == active.id }
                    val day = state.data.splitDays.find { it.id == active.splitDayId }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ElapsedTime(active.startedAt, listOfNotNull(active.dayName, setCountText(sessionSets.count { it.parentSetId == null }))
                                .joinToString(" · ", prefix = " · "), Modifier.weight(1f))
                            TextButton(onClick = { showSettings = true }, enabled = !state.busy) { Text("Settings") }
                            TextButton(onClick = { finishConfirm = true }, enabled = !state.busy) { Text("Finish") }
                        }
                    }
                    if (day != null && day.exerciseIds.isNotEmpty()) item {
                        DayPlan(day, state, sessionSets, onSelect = model::chooseExercise)
                    }
                    if (state.selectedExercise == null) {
                        item { MuscleGrid(day?.muscles.orEmpty()) { muscle -> PickerMemory.muscle = muscle; picker = true } }
                    } else {
                        item {
                            Logger(state, model, onChangeExercise = { picker = true }, onChangeEquipment = { equipmentPicker = true },
                                onPair = { pairPicker = true }, onPlates = { showPlates = true })
                        }
                    }
                    if (sessionSets.isNotEmpty()) {
                        item { SectionTitle("Sets") }
                        items(setRows(sessionSets, newestFirst = true), key = { it.set.id }) { row ->
                            SetRow(row, state.data, unit, enabled = !state.busy) { editingId = row.set.id }
                        }
                    }
                }
            }
        }
    }

    if (picker && active != null) ExercisePicker(state, "Exercise", onDismiss = { picker = false }, onSelect = {
        model.chooseExercise(it); picker = false
    }, onAdd = { name, muscle -> model.addExercise(name, muscle); picker = false }, onToggleFavorite = model::toggleFavorite)
    if (pairPicker && active != null) ExercisePicker(state, "Superset with", onDismiss = { pairPicker = false }, onSelect = {
        model.pairWith(it); pairPicker = false
    }, onAdd = null, onToggleFavorite = model::toggleFavorite)
    if (equipmentPicker && state.selectedExercise != null) EquipmentPicker(state, onDismiss = { equipmentPicker = false }, onSelect = {
        model.select(it); equipmentPicker = false
    }, onAdd = { name, kind, count -> model.addEquipment(name, kind, count); equipmentPicker = false })
    if (showPlates) PlatesDialog(state.weight, state.settings, onDismiss = { showPlates = false })
    if (finishConfirm) AlertDialog(onDismissRequest = { finishConfirm = false }, title = { Text("Finish workout?") },
        confirmButton = { TextButton(onClick = { focus.clearFocus(); finishConfirm = false; model.finish() }, enabled = !state.busy) { Text("Finish") } },
        dismissButton = { TextButton(onClick = { finishConfirm = false }) { Text("Cancel") } })
    if (deleteWorkoutConfirm && detail != null) AlertDialog(onDismissRequest = { deleteWorkoutConfirm = false }, title = { Text("Delete workout?") },
        text = { Text("${setCountText(state.data.totals[detail.id]?.setCount ?: 0)} will be removed. This can't be undone.") },
        confirmButton = {
            TextButton(onClick = { deleteWorkoutConfirm = false; model.deleteWorkout(detail.id) }, enabled = !state.busy) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { deleteWorkoutConfirm = false }) { Text("Cancel") } })
    state.data.sets.find { it.id == editingId }?.let { set ->
        EditSetDialog(set, state.data.sets.count { it.parentSetId == set.id }, unit, state.busy, onDismiss = { editingId = null },
            onSave = { grams, reps -> model.edit(set.id, grams, reps); editingId = null },
            onDelete = { model.delete(set.id); editingId = null })
    }
    state.data.exercises.find { it.id == managedExercise }?.let { exercise ->
        ManageExerciseDialog(exercise, state.data.equipment.filter { it.exerciseId == exercise.id }, state.busy,
            onDismiss = { managedExercise = null },
            onSave = { name, favorite, hidden -> model.updateExercise(exercise.id, name, favorite, hidden); managedExercise = null },
            onDelete = { model.deleteExercise(exercise.id); managedExercise = null },
            onEquipment = { managedEquipment = it })
    }
    editingDay?.let { id ->
        SplitDayDialog(state.data.splitDays.find { it.id == id }, state.data.exercises, state.busy,
            onDismiss = { editingDay = null },
            onSave = { name, muscles, exerciseIds ->
                if (id == NEW_DAY) model.addSplitDay(name, muscles, exerciseIds) else model.updateSplitDay(id, name, muscles, exerciseIds)
                editingDay = null
            },
            onDelete = { model.deleteSplitDay(id); editingDay = null })
    }
    state.data.equipment.find { it.id == managedEquipment }?.let { equipment ->
        ManageEquipmentDialog(equipment, state.busy,
            onDismiss = { managedEquipment = null },
            onSave = { name, hidden -> model.updateEquipment(equipment.id, name, hidden); managedEquipment = null },
            onDelete = { model.deleteEquipment(equipment.id); managedEquipment = null })
    }
    state.notice?.let { message ->
        AlertDialog(onDismissRequest = model::dismissNotice, text = { Text(message) },
            confirmButton = { TextButton(onClick = model::dismissNotice) { Text("OK") } })
    }
    state.error?.let { message ->
        AlertDialog(onDismissRequest = model::dismissError, title = { Text("Error") }, text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { if (state.ready) model.dismissError() else model.reload() }, enabled = !state.busy) {
                    Text(if (state.ready) "OK" else "Retry")
                }
            })
    }
}

@Composable
private fun Logger(state: LoggerState, model: WorkoutViewModel, onChangeExercise: () -> Unit, onChangeEquipment: () -> Unit, onPair: () -> Unit, onPlates: () -> Unit) {
    val exercise = state.selectedExercise ?: return
    val equipment = state.selectedEquipment ?: return
    val partner = state.partnerEquipment
    val unit = state.unit
    val step = state.settings.loadStep
    Surface(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (partner != null) {
                val partnerExercise = state.data.exercises.first { it.id == partner.exerciseId }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Superset", color = Muted, style = MaterialTheme.typography.labelLarge)
                    FilterChip(selected = true, onClick = {}, label = { Text(exercise.name) })
                    FilterChip(selected = false, onClick = { model.select(partner.id) }, enabled = !state.busy, label = { Text(partnerExercise.name) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onChangeExercise, enabled = !state.busy) { Text("Change") }
            }
            OutlinedButton(onClick = onChangeEquipment, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("${equipment.name}  ▾")
            }
            val previous = state.data.latestSets.find { it.equipmentId == equipment.id }
            Text(listOfNotNull(previous?.let { "Last: ${unit.format(it.grams)} ${unit.label} × ${it.reps}" },
                equipment.loadKind.description(equipment.implements)).joinToString(" · "),
                color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = state.weight, onValueChange = model::weight,
                    modifier = Modifier.weight(1f), label = { Text("${if (equipment.loadKind == LoadKind.PER_IMPLEMENT) "Each" else "Load"} (${unit.label})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, enabled = !state.busy || state.savingSet,
                    isError = state.weight.isNotBlank() && unit.parse(state.weight) == null)
                OutlinedTextField(value = state.reps, onValueChange = model::reps,
                    modifier = Modifier.weight(1f), label = { Text("Reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, enabled = !state.busy || state.savingSet,
                    isError = state.reps.isNotBlank() && state.reps.toIntOrNull() !in 1..MAX_REPS)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { model.stepLoad(-1) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("− $step ${unit.label}") }
                OutlinedButton(onClick = { model.stepLoad(1) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("+ $step ${unit.label}") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { model.reps(((state.reps.toIntOrNull() ?: 1) - 1).coerceIn(1, MAX_REPS).toString()) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("− rep") }
                OutlinedButton(onClick = { model.reps(((state.reps.toIntOrNull() ?: 0) + 1).coerceIn(1, MAX_REPS).toString()) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("+ rep") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.dropMode, onClick = model::toggleDrop, enabled = !state.busy, label = { Text("Drop set") })
                if (partner == null) AssistChip(onClick = onPair, enabled = !state.busy, label = { Text("Superset") })
                else AssistChip(onClick = model::endSuperset, enabled = !state.busy, label = { Text("End superset") })
                AssistChip(onClick = { model.openProgress(equipment.id) }, enabled = !state.busy, label = { Text("Progress") })
                if (equipment.loadKind == LoadKind.TOTAL) AssistChip(onClick = onPlates, label = { Text("Plates") })
            }
        }
    }
}

@Composable
private fun HistoryCard(workout: Workout, data: TrainingData, enabled: Boolean, onClick: () -> Unit) {
    val totals = data.totals[workout.id] ?: WorkoutTotals()
    val exercises = data.exerciseSets[workout.id].orEmpty().mapNotNull { entry -> data.exercises.find { it.id == entry.exerciseId }?.let { it to entry.sets } }
    val muscles = exercises.map { it.first.muscle }.distinct()
    Surface(shape = RoundedCornerShape(12.dp), onClick = onClick, enabled = enabled) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(workout.dayName ?: muscles.joinToString(" · ").ifEmpty { "No sets" }, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(dateText(workout.startedAt), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (workout.dayName != null && muscles.isNotEmpty()) Text(muscles.joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodySmall)
            exercises.forEach { (exercise, sets) ->
                Row {
                    Text(exercise.name, Modifier.weight(1f))
                    Text(setCountText(sets), color = Muted)
                }
            }
            Text("${setCountText(totals.setCount)} · ${repCountText(totals.reps)} · ${durationText((workout.endedAt ?: workout.startedAt) - workout.startedAt)}",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private object PickerMemory { var muscle = "Chest" }

@Composable
private fun MuscleGrid(focus: List<String>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (focus + muscleGroups.filterNot { it in focus }).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { muscle ->
                    val modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    if (muscle in focus) FilledTonalButton(onClick = { onSelect(muscle) }, modifier = modifier) { Text(muscle, fontWeight = FontWeight.Bold) }
                    else OutlinedButton(onClick = { onSelect(muscle) }, modifier = modifier) { Text(muscle) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RestControls(endsAt: Long?, settings: Settings, onStart: () -> Unit, onExtend: () -> Unit, onStop: () -> Unit) {
    if (!settings.restEnabled) {
        Spacer(Modifier)
        return
    }
    if (endsAt == null) {
        TextButton(onClick = onStart) { Text("Rest ${clockText(settings.restSeconds * 1000L)}") }
        return
    }
    var now by remember(endsAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Rest ${clockText(endsAt - now)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp))
        TextButton(onClick = onExtend) { Text("+15s") }
        TextButton(onClick = onStop) { Text("Stop") }
    }
}

/** Rounds up, so a running countdown shows 0:01 until it actually ends. */
private fun clockText(milliseconds: Long): String {
    val seconds = ((milliseconds.coerceAtLeast(0) + 999) / 1000)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePicker(state: LoggerState, title: String, onDismiss: () -> Unit, onSelect: (Exercise) -> Unit, onAdd: ((String, String) -> Unit)?, onToggleFavorite: (Exercise) -> Unit) {
    var muscle by rememberSaveable { mutableStateOf(PickerMemory.muscle) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val exercises = remember(state.data.exercises, state.data.latestSets, muscle) {
        val equipmentToExercise = state.data.equipment.associate { it.id to it.exerciseId }
        val recent = state.data.latestSets.groupBy { equipmentToExercise[it.equipmentId] }.mapValues { (_, sets) -> sets.maxOf { it.loggedAt } }
        state.data.exercises.filter { it.muscle == muscle && !it.hidden }
            .sortedWith(compareByDescending<Exercise> { it.favorite }.thenByDescending { recent[it.id] ?: 0L }.thenBy { it.name })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            item {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(muscleGroups) { group -> FilterChip(selected = muscle == group, onClick = { muscle = group; PickerMemory.muscle = group }, label = { Text(group) }) }
                }
                if (onAdd != null && adding) {
                    OutlinedTextField(name, { name = it.take(MAX_NAME_LENGTH) }, modifier = Modifier.fillMaxWidth(), label = { Text("Exercise name") }, singleLine = true)
                    Row {
                        TextButton(onClick = { onAdd(name.trim(), muscle) }, enabled = name.isNotBlank() && !state.busy) { Text("Add") }
                        TextButton(onClick = { adding = false }) { Text("Cancel") }
                    }
                } else if (onAdd != null) TextButton(onClick = { adding = true }) { Text("+ Add exercise") }
            }
            items(exercises, key = { it.id }) { exercise ->
                val equipmentIds = state.data.equipment.filter { it.exerciseId == exercise.id }.map { it.id }.toSet()
                val last = state.data.latestSets.filter { it.equipmentId in equipmentIds }.maxByOrNull { it.id }
                val equipment = state.data.equipment.find { it.id == last?.equipmentId }
                ListItem(headlineContent = { Text(exercise.name, fontWeight = FontWeight.Medium) },
                    supportingContent = equipment?.let { { Text(it.name) } },
                    trailingContent = { FavoriteButton(exercise, enabled = !state.busy) { onToggleFavorite(exercise) } },
                    modifier = Modifier.clickable(enabled = !state.busy) { onSelect(exercise) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentPicker(state: LoggerState, onDismiss: () -> Unit, onSelect: (Long) -> Unit, onAdd: (String, LoadKind, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(LoadKind.TOTAL) }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var chooseKind by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Equipment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!adding) {
                state.data.equipment.filter { it.exerciseId == state.selectedExercise?.id && (!it.hidden || it.id == state.selectedEquipment?.id) }.forEach { equipment ->
                    OutlinedButton(onClick = { onSelect(equipment.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(equipment.name + if (equipment.id == state.selectedEquipment?.id) " ✓" else "", fontWeight = FontWeight.Bold)
                            Text(equipment.loadKind.description(equipment.implements), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                TextButton(onClick = { adding = true }) { Text("+ Add equipment") }
            } else {
                OutlinedTextField(name, { name = it.take(MAX_NAME_LENGTH) }, modifier = Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { chooseKind = true }) { Text("Load: ${kind.label} ▾") }
                    DropdownMenu(expanded = chooseKind, onDismissRequest = { chooseKind = false }) {
                        LoadKind.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { kind = option; count = 1; chooseKind = false })
                        }
                    }
                }
                if (kind == LoadKind.PER_IMPLEMENT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = count == 1, onClick = { count = 1 }, label = { Text("1") })
                        FilterChip(selected = count == 2, onClick = { count = 2 }, label = { Text("2") })
                    }
                }
                Text(kind.description(count), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onAdd(name.trim(), kind, count) }, enabled = name.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Add") }
                TextButton(onClick = { adding = false }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun EditSetDialog(set: LoggedSet, drops: Int, unit: WeightUnit, busy: Boolean, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit, onDelete: () -> Unit) {
    var weight by rememberSaveable(set.id, unit) { mutableStateOf(unit.format(set.grams)) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.reps.toString()) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val grams = unit.parse(weight)
    if (deleting) {
        AlertDialog(onDismissRequest = { deleting = false }, title = { Text(if (set.parentSetId != null) "Delete drop?" else "Delete set?") },
            text = { Text("${unit.format(set.grams)} ${unit.label} × ${set.reps}" + if (drops > 0) " and its ${drops} drop${if (drops == 1) "" else "s"}" else "") },
            confirmButton = { TextButton(onClick = onDelete, enabled = !busy) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
    } else AlertDialog(onDismissRequest = onDismiss, title = { Text(if (set.parentSetId != null) "Edit drop" else "Edit set") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(weight, { weight = it }, label = { Text("Load (${unit.label})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(reps, { reps = it }, label = { Text("Reps") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            TextButton(onClick = { deleting = true }) { Text(if (set.parentSetId != null) "Delete drop" else "Delete set", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { onSave(grams!!, reps.toInt()) }, enabled = !busy && grams != null && reps.toIntOrNull() in 1..MAX_REPS) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SetRow(row: SetLine, data: TrainingData, unit: WeightUnit, enabled: Boolean, onClick: () -> Unit) {
    val set = row.set
    val equipment = data.equipment.first { it.id == set.equipmentId }
    val exercise = data.exercises.first { it.id == equipment.exerciseId }
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp),
        modifier = if (row.number == null) Modifier.padding(start = 28.dp) else Modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (row.number == null) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(row.number?.toString() ?: "↳", color = Muted, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                if (row.number == null) Text("Drop", color = Muted)
                else {
                    Text(exercise.name, fontWeight = FontWeight.Medium)
                    Text(listOfNotNull(equipment.name, if (set.supersetGroup != null) "superset" else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            Text("${unit.format(set.grams)} ${unit.label} × ${set.reps}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun FavoriteButton(exercise: Exercise, enabled: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, enabled = enabled) {
        Text(if (exercise.favorite) "★" else "☆", fontSize = 22.sp, color = if (exercise.favorite) MaterialTheme.colorScheme.primary else Muted)
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Muted)
}

@Composable
private fun ElapsedTime(startedAt: Long, suffix: String, modifier: Modifier) {
    var now by remember(startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Text(durationText(now - startedAt) + suffix, modifier, color = Muted, style = MaterialTheme.typography.bodyMedium)
}

private fun durationText(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
private fun dateText(milliseconds: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(milliseconds))
