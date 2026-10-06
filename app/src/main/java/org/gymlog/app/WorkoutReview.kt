package org.gymlog.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val maxNoteLength = MAX_NOTE_LENGTH

@Composable
fun WorkoutReview(
    workout: Workout,
    totals: WorkoutTotals,
    unit: WeightUnit,
    busy: Boolean,
    onSaveFeedback: (String?, String) -> Unit,
) {
    // Reset drafts only when the persisted feedback changes.
    var draftFeeling by rememberSaveable(workout.id, workout.feeling, workout.note) { mutableStateOf(workout.feeling) }
    var draftNote by rememberSaveable(workout.id, workout.feeling, workout.note) { mutableStateOf(workout.note) }
    val savedFeeling = draftFeeling == workout.feeling
    val savedNote = draftNote.trim() == workout.note
    val dirty = !savedFeeling || !savedNote

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(listOfNotNull(workout.dayName, reviewDateText(workout.startedAt)).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(
            "${setCountText(totals.setCount)} · ${repCountText(totals.reps)} · ${reviewDurationText((workout.endedAt ?: workout.startedAt) - workout.startedAt)}",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        )
        Text("Volume: ${unit.volume(totals.volumeGramReps)} ${unit.label}-reps", style = MaterialTheme.typography.titleMedium)
        Text(
            "${unit.label} × reps; per-implement loads × implements; bodyweight not counted." +
                if (totals.excludedSets > 0) " ${setCountText(totals.excludedSets)} with assisted or unset load excluded." else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            feelings.forEach { feeling ->
                val selected = draftFeeling == feeling
                FilterChip(selected = selected, enabled = !busy,
                    onClick = { draftFeeling = if (selected) null else feeling }, label = { Text(feeling) })
            }
        }
        OutlinedTextField(
            value = draftNote,
            onValueChange = { draftNote = it.take(maxNoteLength) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            enabled = !busy,
        )
        if (dirty) OutlinedButton(onClick = { onSaveFeedback(draftFeeling, draftNote.trim()) }, enabled = !busy) { Text("Save") }
    }
}

private fun reviewDurationText(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

private fun reviewDateText(milliseconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(milliseconds))
