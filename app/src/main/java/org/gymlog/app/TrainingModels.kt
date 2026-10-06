package org.gymlog.app

import java.math.BigDecimal
import java.math.RoundingMode

const val MAX_NAME_LENGTH = 80
const val MAX_NOTE_LENGTH = 500
const val MAX_REPS = 999
const val MAX_GRAMS = 2_000_000
const val MAX_IMPLEMENTS = 2

val muscleGroups = listOf("Chest", "Back", "Shoulders", "Legs", "Arms", "Core")

/** Exactly the feeling values a completed workout can record; null means none. */
val feelings = listOf("Good", "Okay", "Difficult")

/** How the entered load of a set is meant to be read. Snapshotted from equipment onto each logged set. */
enum class LoadKind(val label: String) {
    TOTAL("Total load"),
    PER_IMPLEMENT("Per implement"),
    ADDED_LOAD("Added load"),
    ASSISTANCE("Assistance"),
    UNSPECIFIED("Unspecified");

    fun description(implements: Int): String = when (this) {
        TOTAL -> "total load incl. bar"
        PER_IMPLEMENT -> if (implements == 2) "load each, 2 together" else "load for 1 implement"
        ADDED_LOAD -> "added load, 0 = bodyweight"
        ASSISTANCE -> "assistance, not in volume"
        UNSPECIFIED -> "load type not set, not in volume"
    }
}

/** A rejected user request whose message is safe and useful to show as-is. */
class InputConflict(message: String) : IllegalArgumentException(message)

/** Display/entry unit. Records are always stored as integer grams, so switching units never rewrites history. */
enum class WeightUnit(val label: String, private val gramsPerUnit: BigDecimal) {
    KG("kg", BigDecimal(1000)),
    LB("lb", BigDecimal("453.59237"));

    fun format(grams: Int): String = when (this) {
        KG -> BigDecimal.valueOf(grams.toLong(), 3)
        LB -> BigDecimal(grams).divide(gramsPerUnit, 2, RoundingMode.HALF_UP)
    }.stripTrailingZeros().toPlainString()

    /** kg input must be exact to the gram; lb input is rounded to the nearest gram. */
    fun parse(text: String): Int? = try {
        val value = text.trim().replace(',', '.').toBigDecimal()
        val grams = when (this) {
            KG -> value.movePointRight(3).intValueExact()
            LB -> value.multiply(gramsPerUnit).setScale(0, RoundingMode.HALF_UP).intValueExact()
        }
        grams.takeIf { value.signum() >= 0 && it in 0..MAX_GRAMS }
    } catch (_: IllegalArgumentException) { null } catch (_: ArithmeticException) { null }

    fun volume(gramReps: Long): String = when (this) {
        KG -> BigDecimal.valueOf(gramReps, 3)
        LB -> BigDecimal(gramReps).divide(gramsPerUnit, 0, RoundingMode.HALF_UP)
    }.let { if (it.signum() == 0) "0" else it.stripTrailingZeros().toPlainString() }
}

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("System") }

/** Personal preferences. They change presentation and entry only, never recorded facts. */
data class Settings(
    val unit: WeightUnit = WeightUnit.KG,
    val loadStepKg: String = "2.5",
    val loadStepLb: String = "5",
    val theme: ThemeMode = ThemeMode.DARK,
    val restEnabled: Boolean = true,
    val restSeconds: Int = 90,
    val restAutoStart: Boolean = true,
    val restSound: Boolean = true,
    val restVibrate: Boolean = true,
    val barKg: String = "20",
    val barLb: String = "45",
    val platesKg: String = "25, 20, 15, 10, 5, 2.5, 1.25",
    val platesLb: String = "45, 35, 25, 10, 5, 2.5",
) {
    val loadStep: String get() = if (unit == WeightUnit.KG) loadStepKg else loadStepLb
    val bar: String get() = if (unit == WeightUnit.KG) barKg else barLb
    val plates: String get() = if (unit == WeightUnit.KG) platesKg else platesLb
    fun withLoadStep(step: String): Settings = if (unit == WeightUnit.KG) copy(loadStepKg = step) else copy(loadStepLb = step)
    fun withBar(bar: String): Settings = if (unit == WeightUnit.KG) copy(barKg = bar) else copy(barLb = bar)
    fun withPlates(plates: String): Settings = if (unit == WeightUnit.KG) copy(platesKg = plates) else copy(platesLb = plates)
}

const val MIN_REST_SECONDS = 15
const val MAX_REST_SECONDS = 900
const val MAX_DAY_NAME_LENGTH = 40

/** One day of the user's rotating training split. A plan, never evidence that a workout happened. */
data class SplitDay(val id: Long, val name: String, val muscles: List<String>, val exerciseIds: List<Long> = emptyList())

data class SplitTemplate(val name: String, val days: List<Pair<String, List<String>>>)

/** Starting points only. Days from several templates can be combined, renamed, and edited freely. */
val splitTemplates = listOf(
    SplitTemplate("Full body", listOf("Full body" to muscleGroups)),
    SplitTemplate("Push / Pull / Legs", listOf(
        "Push" to listOf("Chest", "Shoulders", "Arms"), "Pull" to listOf("Back", "Arms"), "Legs" to listOf("Legs", "Core"))),
    SplitTemplate("Upper / Lower", listOf(
        "Upper" to listOf("Chest", "Back", "Shoulders", "Arms"), "Lower" to listOf("Legs", "Core"))),
    SplitTemplate("4-day split", listOf(
        "Chest & triceps" to listOf("Chest", "Arms"), "Back & biceps" to listOf("Back", "Arms"),
        "Legs" to listOf("Legs", "Core"), "Shoulders & core" to listOf("Shoulders", "Core"))),
    SplitTemplate("5-day split", listOf(
        "Chest" to listOf("Chest"), "Back" to listOf("Back"), "Shoulders" to listOf("Shoulders"),
        "Legs" to listOf("Legs"), "Arms & core" to listOf("Arms", "Core"))),
)

/** The day after the most recently trained split day, wrapping around; the first day if none was trained or it was removed. */
fun nextSplitDay(days: List<SplitDay>, lastDayId: Long?): SplitDay? {
    if (days.isEmpty()) return null
    val index = days.indexOfFirst { it.id == lastDayId }
    return if (index < 0) days.first() else days[(index + 1) % days.size]
}

private fun decimal(text: String): BigDecimal? = text.trim().replace(',', '.').toBigDecimalOrNull()

/** Adds [steps] load steps to entered text in the current unit, never going below zero. Null if either value is invalid. */
fun steppedLoad(current: String, step: String, steps: Int): String? {
    val base = decimal(current.ifBlank { "0" }) ?: return null
    val delta = decimal(step)?.takeIf { it.signum() > 0 } ?: return null
    return base.add(delta.multiply(BigDecimal(steps))).max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
}

/** Plate sizes from a comma-separated list, largest first. Null unless every entry is a positive number. */
fun parsePlates(text: String): List<BigDecimal>? {
    val plates = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { decimal(it) ?: return null }
    return plates.takeIf { it.isNotEmpty() && it.all { plate -> plate.signum() > 0 && plate <= BigDecimal(100) } }?.distinct()?.sortedDescending()
}

/** Plates for one side of a bar. [total] is what this combination actually loads; it equals the target when [exact]. */
data class PlateLoad(val perSide: List<BigDecimal>, val total: BigDecimal, val exact: Boolean)

/**
 * The fewest plates per side reaching the target exactly, or else the heaviest load below it.
 * Null when the target is lighter than the bar. Plate counts are unlimited.
 */
fun plateLoad(target: BigDecimal, bar: BigDecimal, plates: List<BigDecimal>): PlateLoad? {
    if (target < bar || plates.isEmpty()) return null
    // Exact integer arithmetic in hundredths of a unit; a side is capped well above any real load.
    val side = target.subtract(bar).divide(BigDecimal(2)).movePointRight(2).setScale(0, RoundingMode.DOWN).toInt().coerceAtMost(100_000)
    val options = plates.map { it to it.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt() }.filter { it.second > 0 }
    val count = IntArray(side + 1) { Int.MAX_VALUE }
    val last = IntArray(side + 1) { -1 }
    count[0] = 0
    for (amount in 1..side) for ((index, option) in options.withIndex()) {
        val size = option.second
        if (size <= amount && count[amount - size] != Int.MAX_VALUE && count[amount - size] + 1 < count[amount]) {
            count[amount] = count[amount - size] + 1
            last[amount] = index
        }
    }
    val reached = (side downTo 0).first { count[it] != Int.MAX_VALUE }
    val perSide = buildList {
        var amount = reached
        while (amount > 0) { add(options[last[amount]].first); amount -= options[last[amount]].second }
    }.sortedDescending()
    val total = bar.add(BigDecimal(reached).movePointLeft(2).multiply(BigDecimal(2)))
    return PlateLoad(perSide, total.stripTrailingZeros(), total.compareTo(target) == 0)
}

data class Exercise(val id: Long, val name: String, val muscle: String, val favorite: Boolean = false, val hidden: Boolean = false)
data class Equipment(
    val id: Long,
    val exerciseId: Long,
    val name: String,
    val loadKind: LoadKind = LoadKind.UNSPECIFIED,
    val implements: Int = 1,
    val hidden: Boolean = false,
)
data class Workout(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val selectedEquipment: Long?,
    val feeling: String? = null,
    val note: String = "",
    val dayName: String? = null,
    val splitDayId: Long? = null,
    /** The other half of an active superset; sets then alternate between it and the selected equipment. */
    val partnerEquipment: Long? = null,
)

/**
 * One recorded set or, when [parentSetId] is set, one drop segment of that set.
 * A drop segment adds its reps and volume to its set but does not count as another set.
 * Sets sharing a [supersetGroup] were performed back to back as one superset round.
 */
data class LoggedSet(
    val id: Long, val workoutId: Long, val equipmentId: Long,
    val grams: Int, val reps: Int, val loggedAt: Long,
    val loadKind: LoadKind = LoadKind.UNSPECIFIED,
    val implements: Int = 1,
    val parentSetId: Long? = null,
    val supersetGroup: Long? = null,
)

/** Exact aggregates over one workout's sets. Volume is gram-reps; only PER_IMPLEMENT multiplies by implements. */
data class WorkoutTotals(
    val setCount: Int = 0,
    val reps: Long = 0,
    val volumeGramReps: Long = 0,
    val excludedSets: Int = 0,
)

/** Sets recorded for one exercise in one workout, in the order the exercise was first trained. */
data class ExerciseSets(val exerciseId: Long, val sets: Int)

/**
 * Exact totals over [sets]. Reps count entered reps (never multiplied), including drop segments.
 * Drop segments are not counted as additional sets. ASSISTANCE and UNSPECIFIED loads count toward sets
 * and reps but are excluded from recorded volume and reported via [WorkoutTotals.excludedSets].
 */
fun workoutTotals(sets: List<LoggedSet>): WorkoutTotals {
    var reps = 0L
    var volume = 0L
    var excluded = 0
    for (set in sets) {
        reps += set.reps
        when (set.loadKind) {
            LoadKind.ASSISTANCE, LoadKind.UNSPECIFIED -> if (set.parentSetId == null) excluded += 1
            LoadKind.PER_IMPLEMENT -> volume += set.grams.toLong() * set.reps * set.implements
            LoadKind.TOTAL, LoadKind.ADDED_LOAD -> volume += set.grams.toLong() * set.reps
        }
    }
    return WorkoutTotals(sets.count { it.parentSetId == null }, reps, volume, excluded)
}

/** Recent sessions on one equipment option, newest first, and its heaviest set. */
data class Progress(val equipmentId: Long, val sessions: List<Pair<Workout, List<LoggedSet>>>, val best: LoggedSet?)

data class TrainingData(
    val exercises: List<Exercise> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val sets: List<LoggedSet> = emptyList(),
    val latestSets: List<LoggedSet> = emptyList(),
    val totals: Map<Long, WorkoutTotals> = emptyMap(),
    val exerciseSets: Map<Long, List<ExerciseSets>> = emptyMap(),
    val hasOlder: Boolean = false,
    val splitDays: List<SplitDay> = emptyList(),
    val lastSplitDayId: Long? = null,
    /** Sets (drop segments excluded) per muscle group since the start of this week and during the previous week. */
    val weekSets: Map<String, Int> = emptyMap(),
    val lastWeekSets: Map<String, Int> = emptyMap(),
) {
    val active: Workout? get() = workouts.firstOrNull { it.endedAt == null }
    val nextDay: SplitDay? get() = nextSplitDay(splitDays, lastSplitDayId)
}

fun setCountText(count: Int): String = "$count ${if (count == 1) "set" else "sets"}"
fun repCountText(count: Long): String = "$count ${if (count == 1L) "rep" else "reps"}"
