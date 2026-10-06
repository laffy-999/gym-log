package org.gymlog.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/** One row in a set list: a set with its number, or a drop segment ([number] null) shown under its set. */
data class SetLine(val set: LoggedSet, val number: Int?)

/** Numbers sets in training order and places each set's drop segments directly beneath it. */
fun setRows(sets: List<LoggedSet>, newestFirst: Boolean): List<SetLine> {
    val tops = sets.filter { it.parentSetId == null }.sortedBy { it.id }
    val drops = sets.filter { it.parentSetId != null }.sortedBy { it.id }.groupBy { it.parentSetId }
    val numbered = tops.mapIndexed { index, set -> SetLine(set, index + 1) }
    return (if (newestFirst) numbered.asReversed() else numbered).flatMap { line ->
        listOf(line) + drops[line.set.id].orEmpty().map { SetLine(it, null) }
    }
}

fun dayExerciseNames(day: SplitDay, data: TrainingData): List<String> =
    day.exerciseIds.mapNotNull { id -> data.exercises.find { it.id == id }?.name }

private val muted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** The day's planned exercises with sets done so far; one tap selects an exercise. Nothing is prescribed. */
@Composable
fun DayPlan(day: SplitDay, state: LoggerState, sessionSets: List<LoggedSet>, onSelect: (Exercise) -> Unit) {
    val exerciseOfEquipment = state.data.equipment.associate { it.id to it.exerciseId }
    val done = sessionSets.filter { it.parentSetId == null }.groupingBy { exerciseOfEquipment[it.equipmentId] }.eachCount()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        day.exerciseIds.forEach { id ->
            val exercise = state.data.exercises.find { it.id == id } ?: return@forEach
            val count = done[id] ?: 0
            FilterChip(selected = state.selectedExercise?.id == id, enabled = !state.busy, onClick = { onSelect(exercise) },
                label = { Text(if (count > 0) "${exercise.name} · $count" else exercise.name) })
        }
    }
}

/** Sets per muscle group this week (since Monday) next to last week. Drop segments are not extra sets. */
@Composable
fun WeekCard(data: TrainingData) {
    val muscles = muscleGroups.filter { (data.weekSets[it] ?: 0) > 0 || (data.lastWeekSets[it] ?: 0) > 0 }
    Surface(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("Sets per muscle", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("This week", Modifier.width(80.dp), color = muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                Text("Last week", Modifier.width(80.dp), color = muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            }
            muscles.forEach { muscle ->
                Row {
                    Text(muscle, Modifier.weight(1f))
                    Text("${data.weekSets[muscle] ?: 0}", Modifier.width(80.dp), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    Text("${data.lastWeekSets[muscle] ?: 0}", Modifier.width(80.dp), color = muted, textAlign = TextAlign.End)
                }
            }
        }
    }
}

fun LazyListScope.progressItems(progress: Progress, data: TrainingData, unit: WeightUnit, enabled: Boolean, onEquipment: (Long) -> Unit) {
    val equipment = data.equipment.find { it.id == progress.equipmentId } ?: return
    val exercise = data.exercises.find { it.id == equipment.exerciseId } ?: return
    val load = { set: LoggedSet -> "${unit.format(set.grams)} × ${set.reps}" }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.equipment.filter { it.exerciseId == exercise.id && (!it.hidden || it.id == equipment.id) }.forEach { option ->
                    FilterChip(selected = option.id == equipment.id, enabled = enabled, onClick = { onEquipment(option.id) }, label = { Text(option.name) })
                }
            }
            Text("Each machine keeps its own history. Loads in ${unit.label}.", color = muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    item {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Heaviest set", color = muted, style = MaterialTheme.typography.bodySmall)
                Text(progress.best?.let { "${load(it)} · ${shortDate(it.loggedAt)}" } ?: "No sets on this equipment yet",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
    items(progress.sessions, key = { it.first.id }) { (workout, sets) ->
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(listOfNotNull(shortDate(workout.startedAt), workout.dayName).joinToString(" · "), fontWeight = FontWeight.SemiBold)
                Text(setRows(sets, newestFirst = false).joinToString("   ") { line -> (if (line.number == null) "↳" else "") + load(line.set) }, color = muted)
            }
        }
    }
}

/** Per-side plates for the entered total load, using the bar and plate sizes from Settings. */
@Composable
fun PlatesDialog(weight: String, settings: Settings, onDismiss: () -> Unit) {
    val unit = settings.unit.label
    val target = weight.trim().replace(',', '.').toBigDecimalOrNull()
    val bar = settings.bar.trim().replace(',', '.').toBigDecimalOrNull()
    val plates = parsePlates(settings.plates)
    val result = if (target != null && bar != null && plates != null) plateLoad(target, bar, plates) else null
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Plates") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                target == null -> Text("Enter the total load first.")
                bar == null || plates == null -> Text("Set the bar and plates in Settings.")
                result == null -> Text("${target.toPlainString()} $unit is lighter than the ${bar.stripTrailingZeros().toPlainString()} $unit bar.")
                else -> {
                    Text("Per side", color = muted, style = MaterialTheme.typography.bodySmall)
                    Text(result.perSide.joinToString(" + ") { it.stripTrailingZeros().toPlainString() }.ifEmpty { "No plates" },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Bar ${bar.stripTrailingZeros().toPlainString()} $unit", color = muted)
                    if (!result.exact) Text("Exact load not possible with these plates. Closest: ${result.total.toPlainString()} $unit.",
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Text("Bar and plate sizes are in Settings.", color = muted, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } })
}

private fun shortDate(milliseconds: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(milliseconds))
