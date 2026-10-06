package org.gymlog.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Editing target for [SplitDayDialog] that creates a new day instead of changing one. */
const val NEW_DAY = -1L

fun LazyListScope.settingsItems(
    state: LoggerState,
    muscle: String,
    onMuscle: (String) -> Unit,
    onSettings: (Settings) -> Unit,
    onToggleFavorite: (Exercise) -> Unit,
    onOpenExercise: (Long) -> Unit,
    onAddTemplate: (SplitTemplate) -> Unit,
    onEditDay: (Long) -> Unit,
    onMoveDay: (Long, Int) -> Unit,
    onClearSplit: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
) {
    val settings = state.settings
    val days = state.data.splitDays
    item { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
    item { BackupSettings(state.busy, onExport, onImport) }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Training split")
            Text(if (days.isEmpty()) "No split: every workout is free. Add templates or single days and combine them as you like."
                else "Days rotate in this order. Start suggests the next day; you can still pick any day or train freely.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    items(days.size, key = { "day-${days[it].id}" }) { index ->
        val day = days[index]
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !state.busy) { onEditDay(day.id) },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("${index + 1}. ${day.name}", fontWeight = FontWeight.Medium)
                    Text(dayExerciseNames(day, state.data).ifEmpty { day.muscles }.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onMoveDay(day.id, -1) }, enabled = !state.busy && index > 0) { Text("↑") }
                TextButton(onClick = { onMoveDay(day.id, 1) }, enabled = !state.busy && index < days.lastIndex) { Text("↓") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    item {
        var templates by remember { mutableStateOf(false) }
        var confirmClear by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onEditDay(NEW_DAY) }, enabled = !state.busy) { Text("+ Day") }
            Box {
                TextButton(onClick = { templates = true }, enabled = !state.busy) { Text("+ Template ▾") }
                DropdownMenu(expanded = templates, onDismissRequest = { templates = false }) {
                    splitTemplates.forEach { template ->
                        DropdownMenuItem(text = { Text(template.name) }, onClick = { templates = false; onAddTemplate(template) })
                    }
                }
            }
            if (days.isNotEmpty()) TextButton(onClick = { if (confirmClear) { confirmClear = false; onClearSplit() } else confirmClear = true }, enabled = !state.busy) {
                Text(if (confirmClear) "Tap again to clear" else "Clear", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    item { RestTimerSettings(settings, onSettings) }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Weight unit")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightUnit.entries.forEach { unit ->
                    FilterChip(selected = settings.unit == unit, enabled = !state.busy, onClick = { onSettings(settings.copy(unit = unit)) }, label = { Text(unit.label) })
                }
            }
            Text("Changes display and entry only. Saved sets are not modified.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    item {
        var step by rememberSaveable(settings.unit) { mutableStateOf(settings.loadStep) }
        val valid = SettingsStore.validStep(step)
        OutlinedTextField(step, { value ->
            step = value.take(6)
            if (SettingsStore.validStep(step)) onSettings(settings.withLoadStep(step.trim().replace(',', '.')))
        }, label = { Text("Load step (${settings.unit.label})") }, isError = !valid, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = { if (!valid) Text("Enter a step above 0 and up to 100") })
    }
    item { PlateSettings(settings, onSettings) }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(selected = settings.theme == mode, onClick = { onSettings(settings.copy(theme = mode)) }, label = { Text(mode.label) })
                }
            }
        }
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Exercises & equipment")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(muscleGroups) { group -> FilterChip(selected = muscle == group, onClick = { onMuscle(group) }, label = { Text(group) }) }
            }
        }
    }
    val exercises = state.data.exercises.filter { it.muscle == muscle }.sortedWith(compareByDescending<Exercise> { it.favorite }.thenBy { it.name })
    items(exercises, key = { it.id }) { exercise ->
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !state.busy) { onOpenExercise(exercise.id) },
                verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.name, Modifier.weight(1f).padding(start = 4.dp))
                if (exercise.hidden) Text("hidden", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                FavoriteButton(exercise, enabled = !state.busy) { onToggleFavorite(exercise) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun RestTimerSettings(settings: Settings, onSettings: (Settings) -> Unit) {
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(RestTimer.canNotify(context)) }
    var exact by remember { mutableStateOf(RestTimer.canAlertExactly(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = RestTimer.canNotify(context)
        exact = RestTimer.canAlertExactly(context)
        onPauseOrDispose { }
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = it }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("Rest timer")
        CheckRow("Rest timer", settings.restEnabled) { onSettings(settings.copy(restEnabled = it)) }
        if (!settings.restEnabled) return@Column
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60, 90, 120, 180).forEach { seconds ->
                FilterChip(selected = settings.restSeconds == seconds, onClick = { onSettings(settings.copy(restSeconds = seconds)) },
                    label = { Text("${seconds / 60}:${"%02d".format(seconds % 60)}") })
            }
        }
        var custom by rememberSaveable(settings.restSeconds) { mutableStateOf(settings.restSeconds.toString()) }
        val valid = custom.toIntOrNull() in MIN_REST_SECONDS..MAX_REST_SECONDS
        OutlinedTextField(custom, { value ->
            custom = value.filter(Char::isDigit).take(3)
            custom.toIntOrNull()?.takeIf { it in MIN_REST_SECONDS..MAX_REST_SECONDS }?.let { onSettings(settings.copy(restSeconds = it)) }
        }, label = { Text("Seconds") }, isError = !valid, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { if (!valid) Text("$MIN_REST_SECONDS to $MAX_REST_SECONDS seconds") })
        CheckRow("Start after each saved set", settings.restAutoStart) { onSettings(settings.copy(restAutoStart = it)) }
        CheckRow("Sound", settings.restSound) { onSettings(settings.copy(restSound = it)) }
        CheckRow("Vibrate", settings.restVibrate) { onSettings(settings.copy(restVibrate = it)) }
        if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TextButton(onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow alerts while the app is in the background") }
        }
        if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TextButton(onClick = {
                context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
            }) { Text("Allow on-time alerts when the screen is off") }
            Text("Without this, Android may deliver the alert late while the app is closed.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BackupSettings(busy: Boolean, onExport: (Uri) -> Unit, onImport: (Uri) -> Unit) {
    var confirmImport by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(onExport) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImport) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("Backup")
        Text("Saves every workout, exercise, split day, and setting to one file. Keep it somewhere other than this phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { export.launch("gym-log-backup-${java.time.LocalDate.now()}.json") }, enabled = !busy) { Text("Export backup") }
            TextButton(onClick = { confirmImport = true }, enabled = !busy) { Text("Import backup") }
        }
    }
    if (confirmImport) AlertDialog(onDismissRequest = { confirmImport = false }, title = { Text("Replace all data?") },
        text = { Text("Importing replaces every workout, exercise, split day, and setting on this phone with the backup. If the file can't be read, nothing changes.") },
        confirmButton = { TextButton(onClick = { confirmImport = false; import.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) { Text("Choose file") } },
        dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } })
}

@Composable
private fun PlateSettings(settings: Settings, onSettings: (Settings) -> Unit) {
    var bar by rememberSaveable(settings.unit) { mutableStateOf(settings.bar) }
    var plates by rememberSaveable(settings.unit) { mutableStateOf(settings.plates) }
    val barValid = SettingsStore.validBar(bar)
    val platesValid = parsePlates(plates) != null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("Plate calculator")
        OutlinedTextField(bar, { value ->
            bar = value.take(6)
            if (SettingsStore.validBar(bar)) onSettings(settings.withBar(bar.trim().replace(',', '.')))
        }, label = { Text("Bar (${settings.unit.label})") }, isError = !barValid, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(plates, { value ->
            plates = value.take(80)
            if (parsePlates(plates) != null) onSettings(settings.withPlates(plates))
        }, label = { Text("Plates you have (${settings.unit.label})") }, isError = !platesValid, singleLine = true,
            supportingText = { Text(if (platesValid) "Comma-separated, e.g. 25, 20, 10, 5" else "Enter sizes above 0, separated by commas") })
    }
}

@Composable
fun SplitDayDialog(
    day: SplitDay?,
    exercises: List<Exercise>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, muscles: List<String>, exerciseIds: List<Long>) -> Unit,
    onDelete: () -> Unit,
) {
    var name by rememberSaveable(day?.id) { mutableStateOf(day?.name ?: "") }
    var muscles by rememberSaveable(day?.id) { mutableStateOf(day?.muscles.orEmpty().toList()) }
    var planned by rememberSaveable(day?.id) { mutableStateOf(day?.exerciseIds.orEmpty().toList()) }
    var confirmDelete by rememberSaveable(day?.id) { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (day == null) "New day" else "Edit day") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(name, { name = it.take(MAX_DAY_NAME_LENGTH) }, label = { Text("Name") }, singleLine = true)
            SectionTitle("Muscle groups")
            muscleGroups.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { muscle ->
                        FilterChip(selected = muscle in muscles, onClick = { muscles = if (muscle in muscles) muscles - muscle else muscles + muscle },
                            label = { Text(muscle) })
                    }
                }
            }
            SectionTitle("Exercises (optional)")
            planned.forEachIndexed { index, id ->
                val exercise = exercises.find { it.id == id } ?: return@forEachIndexed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(exercise.name, Modifier.weight(1f))
                    TextButton(onClick = { planned = planned.toMutableList().apply { add(index - 1, removeAt(index)) } }, enabled = index > 0) { Text("↑") }
                    TextButton(onClick = { planned = planned - id }) { Text("✕") }
                }
            }
            Box {
                TextButton(onClick = { adding = true }) { Text("+ Exercise ▾") }
                DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                    exercises.filter { !it.hidden && it.id !in planned && (muscles.isEmpty() || it.muscle in muscles) }
                        .sortedWith(compareBy<Exercise> { muscleGroups.indexOf(it.muscle) }.thenByDescending { it.favorite }.thenBy { it.name })
                        .forEach { exercise ->
                            DropdownMenuItem(text = { Text("${exercise.name} · ${exercise.muscle}") }, onClick = { planned = planned + exercise.id; adding = false })
                        }
                }
            }
            if (day != null) TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }, enabled = !busy) {
                Text(if (confirmDelete) "Tap again to delete" else "Delete day", color = MaterialTheme.colorScheme.error)
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onSave(name, muscles, planned) }, enabled = !busy && name.isNotBlank() && muscles.isNotEmpty()) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun ManageExerciseDialog(
    exercise: Exercise,
    equipment: List<Equipment>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, favorite: Boolean, hidden: Boolean) -> Unit,
    onDelete: () -> Unit,
    onEquipment: (Long) -> Unit,
) {
    var name by rememberSaveable(exercise.id) { mutableStateOf(exercise.name) }
    var favorite by rememberSaveable(exercise.id) { mutableStateOf(exercise.favorite) }
    var hidden by rememberSaveable(exercise.id) { mutableStateOf(exercise.hidden) }
    var confirmDelete by rememberSaveable(exercise.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(exercise.muscle) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(name, { name = it.take(MAX_NAME_LENGTH) }, label = { Text("Name") }, singleLine = true)
            CheckRow("Favorite", favorite) { favorite = it }
            CheckRow("Hidden from picker", hidden) { hidden = it }
            SectionTitle("Equipment")
            equipment.forEach { item ->
                TextButton(onClick = { onEquipment(item.id) }, enabled = !busy) {
                    Text(item.name + if (item.hidden) " (hidden)" else "", Modifier.fillMaxWidth())
                }
            }
            TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }, enabled = !busy) {
                Text(if (confirmDelete) "Tap again to delete" else "Delete exercise", color = MaterialTheme.colorScheme.error)
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onSave(name, favorite, hidden) }, enabled = !busy && name.isNotBlank()) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun ManageEquipmentDialog(
    equipment: Equipment,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, hidden: Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var name by rememberSaveable(equipment.id) { mutableStateOf(equipment.name) }
    var hidden by rememberSaveable(equipment.id) { mutableStateOf(equipment.hidden) }
    var confirmDelete by rememberSaveable(equipment.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Equipment") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(name, { name = it.take(MAX_NAME_LENGTH) }, label = { Text("Name") }, singleLine = true)
            Text("${equipment.loadKind.label}: ${equipment.loadKind.description(equipment.implements)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            CheckRow("Hidden from picker", hidden) { hidden = it }
            TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }, enabled = !busy) {
                Text(if (confirmDelete) "Tap again to delete" else "Delete equipment", color = MaterialTheme.colorScheme.error)
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onSave(name, hidden) }, enabled = !busy && name.isNotBlank()) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
