package org.gymlog.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

/** Result of saving a set: its id, and whether it completed a superset round (A then B). */
data class SavedSet(val id: Long, val completedRound: Boolean)

/** Called serially off the UI thread. A successful mutation has committed before returning. */
class WorkoutStore(context: Context) : SQLiteOpenHelper(context, "training.db", null, SCHEMA_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE exercise (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, muscle TEXT NOT NULL, $favoriteColumn, $hiddenColumn, UNIQUE(name, muscle))")
        db.execSQL("CREATE TABLE equipment (id INTEGER PRIMARY KEY, exercise_id INTEGER NOT NULL REFERENCES exercise(id), name TEXT NOT NULL COLLATE NOCASE, $loadColumns, $hiddenColumn, UNIQUE(exercise_id, name))")
        db.execSQL(splitDayTable)
        db.execSQL(splitDayExerciseTable)
        db.execSQL("CREATE TABLE workout (id INTEGER PRIMARY KEY, started_at INTEGER NOT NULL, ended_at INTEGER, selected_equipment INTEGER REFERENCES equipment(id), feeling TEXT CHECK(feeling IN ('Good','Okay','Difficult')), note TEXT NOT NULL DEFAULT '' CHECK(length(note) <= 500), $dayNameColumn, $splitDayColumn, $partnerColumn)")
        db.execSQL("CREATE UNIQUE INDEX one_active_workout ON workout((1)) WHERE ended_at IS NULL")
        db.execSQL("CREATE TABLE logged_set (id INTEGER PRIMARY KEY, workout_id INTEGER NOT NULL REFERENCES workout(id), equipment_id INTEGER NOT NULL REFERENCES equipment(id), grams INTEGER NOT NULL CHECK(grams BETWEEN 0 AND 2000000), reps INTEGER NOT NULL CHECK(reps BETWEEN 1 AND 999), logged_at INTEGER NOT NULL, $loadColumns, $parentColumn, $supersetColumn)")
        db.execSQL("CREATE INDEX sets_by_workout ON logged_set(workout_id, id)")
        db.execSQL("CREATE INDEX sets_by_equipment ON logged_set(equipment_id, id)")
        db.execSQL("CREATE INDEX completed_workouts ON workout(id) WHERE ended_at IS NOT NULL")
        createV5Indexes(db)
        val seeds = mapOf(
            "Chest" to listOf("Flat bench press", "Incline press", "Decline press", "Pec deck", "Standing chest fly", "Cable crossover", "Dips"),
            "Back" to listOf("Lat pulldown", "Seated row", "Pull-up", "Barbell row"),
            "Shoulders" to listOf("Shoulder press", "Lateral raise", "Reverse fly"),
            "Legs" to listOf("Squat", "Leg press", "Leg extension", "Leg curl", "Romanian deadlift", "Calf raise"),
            "Arms" to listOf("Biceps curl", "Hammer curl", "Triceps pushdown", "Triceps extension"),
            "Core" to listOf("Crunch", "Hanging leg raise"),
        )
        seeds.forEach { (muscle, names) -> names.forEach { name ->
            val id = db.insertOrThrow("exercise", null, values("name" to name, "muscle" to muscle))
            equipment(db, id, "Unspecified equipment", LoadKind.UNSPECIFIED, 1)
        } }
        seedFreeWeights(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion in 1 until SCHEMA_VERSION && newVersion == SCHEMA_VERSION) { "Unsupported database version" }
        if (oldVersion < 2) {
            for (table in listOf("equipment", "logged_set")) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $kindColumn")
                db.execSQL("ALTER TABLE $table ADD COLUMN $implementsColumn")
            }
            db.execSQL("ALTER TABLE workout ADD COLUMN feeling TEXT CHECK(feeling IN ('Good','Okay','Difficult'))")
            db.execSQL("ALTER TABLE workout ADD COLUMN note TEXT NOT NULL DEFAULT '' CHECK(length(note) <= 500)")
            db.execSQL("CREATE INDEX completed_workouts ON workout(id) WHERE ended_at IS NOT NULL")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE exercise ADD COLUMN $favoriteColumn")
            db.execSQL("ALTER TABLE exercise ADD COLUMN $hiddenColumn")
            db.execSQL("ALTER TABLE equipment ADD COLUMN $hiddenColumn")
        }
        if (oldVersion < 4) {
            db.execSQL(splitDayTable)
            db.execSQL("ALTER TABLE workout ADD COLUMN $dayNameColumn")
            db.execSQL("ALTER TABLE workout ADD COLUMN $splitDayColumn")
        }
        db.execSQL(splitDayExerciseTable)
        db.execSQL("ALTER TABLE workout ADD COLUMN $partnerColumn")
        db.execSQL("ALTER TABLE logged_set ADD COLUMN $parentColumn")
        db.execSQL("ALTER TABLE logged_set ADD COLUMN $supersetColumn")
        createV5Indexes(db)
        if (oldVersion < 2) seedFreeWeights(db)
    }

    private fun createV5Indexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX sets_by_time ON logged_set(logged_at)")
        db.execSQL("CREATE INDEX sets_by_parent ON logged_set(parent_set_id)")
    }

    private fun seedFreeWeights(db: SQLiteDatabase) {
        val ids = query(db, "SELECT id FROM exercise WHERE muscle = 'Chest' AND name IN ('Flat bench press','Incline press','Decline press')") { it.getLong(0) }
        for (id in ids) {
            for ((name, kind, count) in listOf(Triple("Barbell", LoadKind.TOTAL, 1), Triple("Dumbbells", LoadKind.PER_IMPLEMENT, 2))) {
                var candidate = name
                var suffix = 1
                while (true) {
                    val existing = findEquipment(db, id, candidate)
                    if (existing == null) { equipment(db, id, candidate, kind, count); break }
                    if (existing.loadKind == kind && existing.implements == count) break
                    candidate = "$name (${kind.label} ${suffix++})"
                }
            }
        }
    }

    fun load(historyBefore: Long? = null, detailId: Long? = null, now: Long = System.currentTimeMillis()): TrainingData {
        val db = readableDatabase
        val exercises = query(db, "SELECT id, name, muscle, favorite, hidden FROM exercise ORDER BY id") {
            Exercise(it.getLong(0), it.getString(1), it.getString(2), it.getInt(3) == 1, it.getInt(4) == 1)
        }
        val equipment = query(db, "SELECT $equipmentFields FROM equipment ORDER BY id", map = ::readEquipment)
        val active = query(db, "SELECT $workoutFields FROM workout WHERE ended_at IS NULL", map = ::readWorkout).firstOrNull()
        val page = if (active == null) query(db,
            "SELECT $workoutFields FROM workout WHERE ended_at IS NOT NULL${if (historyBefore != null) " AND id < ?" else ""} ORDER BY id DESC LIMIT 31",
            historyBefore?.let { arrayOf(it.toString()) }, ::readWorkout) else emptyList()
        val detail = detailId?.let { query(db, "SELECT $workoutFields FROM workout WHERE id = ?", arrayOf(it.toString()), ::readWorkout).firstOrNull() }
        val workouts = (listOfNotNull(active) + page.take(30) + listOfNotNull(detail)).distinctBy { it.id }
        val setWorkouts = listOfNotNull(active?.id, detail?.id).distinct()
        val sets = if (setWorkouts.isEmpty()) emptyList() else query(db,
            "SELECT $setFields FROM logged_set WHERE workout_id IN (${setWorkouts.joinToString { "?" }}) ORDER BY id",
            setWorkouts.map { it.toString() }.toTypedArray(), ::readSet)
        // One indexed last-record lookup per equipment, not a scan over the full set archive. Drop segments never prefill the next set.
        val latest = query(db, "SELECT ${setFields.split(", ").joinToString { "s.$it" }} FROM equipment e JOIN logged_set s ON s.id = (SELECT id FROM logged_set WHERE equipment_id = e.id AND parent_set_id IS NULL ORDER BY id DESC LIMIT 1)", map = ::readSet)
        val totals = workouts.associate { workout ->
            workout.id to if (workout.id in setWorkouts) workoutTotals(sets.filter { it.workoutId == workout.id }) else
                query(db, "SELECT COALESCE(SUM(parent_set_id IS NULL),0), COALESCE(SUM(reps),0), COALESCE(SUM(CASE WHEN load_kind IN ('ASSISTANCE','UNSPECIFIED') THEN 0 ELSE CAST(grams AS INTEGER) * reps * CASE WHEN load_kind = 'PER_IMPLEMENT' THEN implements ELSE 1 END END),0), COALESCE(SUM(load_kind IN ('ASSISTANCE','UNSPECIFIED') AND parent_set_id IS NULL),0) FROM logged_set WHERE workout_id = ?", arrayOf(workout.id.toString())) {
                    WorkoutTotals(it.getInt(0), it.getLong(1), it.getLong(2), it.getInt(3))
                }.single()
        }
        val exerciseSets = if (workouts.isEmpty()) emptyMap() else query(db,
            "SELECT s.workout_id, e.exercise_id, COUNT(*) FROM logged_set s JOIN equipment e ON e.id = s.equipment_id " +
                "WHERE s.parent_set_id IS NULL AND s.workout_id IN (${workouts.joinToString { "?" }}) GROUP BY s.workout_id, e.exercise_id ORDER BY s.workout_id, MIN(s.id)",
            workouts.map { it.id.toString() }.toTypedArray()) { it.getLong(0) to ExerciseSets(it.getLong(1), it.getInt(2)) }
            .groupBy({ it.first }, { it.second })
        val dayExercises = query(db, "SELECT split_day_id, exercise_id FROM split_day_exercise ORDER BY split_day_id, position") { it.getLong(0) to it.getLong(1) }
            .groupBy({ it.first }, { it.second })
        val splitDays = query(db, "SELECT id, name, muscles FROM split_day ORDER BY position, id") {
            SplitDay(it.getLong(0), it.getString(1), it.getString(2).split(',').filter(String::isNotEmpty), dayExercises[it.getLong(0)].orEmpty())
        }
        val lastSplitDayId = query(db, "SELECT split_day_id FROM workout WHERE ended_at IS NOT NULL AND split_day_id IS NOT NULL ORDER BY id DESC LIMIT 1") { it.getLong(0) }.firstOrNull()
        val weekStart = weekStart(now)
        val lastWeekStart = Calendar.getInstance().apply { timeInMillis = weekStart; add(Calendar.WEEK_OF_YEAR, -1) }.timeInMillis
        return TrainingData(exercises, equipment, workouts, sets, latest, totals, exerciseSets, page.size > 30, splitDays, lastSplitDayId,
            muscleSets(db, weekStart, Long.MAX_VALUE), muscleSets(db, lastWeekStart, weekStart))
    }

    /** Sets (not drop segments) per muscle group logged in [from, until); uses the logged_at index. */
    private fun muscleSets(db: SQLiteDatabase, from: Long, until: Long): Map<String, Int> = query(db,
        "SELECT x.muscle, COUNT(*) FROM logged_set s JOIN equipment q ON q.id = s.equipment_id JOIN exercise x ON x.id = q.exercise_id " +
            "WHERE s.logged_at >= ? AND s.logged_at < ? AND s.parent_set_id IS NULL GROUP BY x.muscle",
        arrayOf(from.toString(), until.toString())) { it.getString(0) to it.getInt(1) }.toMap()

    /** Recent sessions with this equipment, newest first, plus its heaviest set (most reps breaks ties). */
    fun progress(equipmentId: Long, sessions: Int = 12): Progress {
        val db = readableDatabase
        val id = equipmentId.toString()
        val workoutIds = query(db, "SELECT DISTINCT workout_id FROM logged_set WHERE equipment_id = ? ORDER BY workout_id DESC LIMIT $sessions", arrayOf(id)) { it.getLong(0) }
        val sets = if (workoutIds.isEmpty()) emptyList() else query(db,
            "SELECT $setFields FROM logged_set WHERE equipment_id = ? AND workout_id IN (${workoutIds.joinToString { "?" }}) ORDER BY id",
            arrayOf(id) + workoutIds.map { it.toString() }, ::readSet)
        val workouts = if (workoutIds.isEmpty()) emptyMap() else query(db,
            "SELECT $workoutFields FROM workout WHERE id IN (${workoutIds.joinToString { "?" }})", workoutIds.map { it.toString() }.toTypedArray(), ::readWorkout)
            .associateBy { it.id }
        val best = query(db, "SELECT $setFields FROM logged_set WHERE equipment_id = ? AND parent_set_id IS NULL ORDER BY grams DESC, reps DESC, id LIMIT 1", arrayOf(id), ::readSet).firstOrNull()
        val bySession = sets.groupBy { it.workoutId }
        return Progress(equipmentId, workoutIds.mapNotNull { workoutId -> workouts[workoutId]?.let { it to bySession[workoutId].orEmpty() } }, best)
    }

    /** Starts a workout, optionally for one split day. The day's name is copied so later plan edits never rewrite history. */
    fun start(splitDayId: Long? = null) = transaction { db ->
        if (query(db, "SELECT id FROM workout WHERE ended_at IS NULL") { it.getLong(0) }.isEmpty()) {
            val dayName = splitDayId?.let { id -> query(db, "SELECT name FROM split_day WHERE id = ?", arrayOf(id.toString())) { it.getString(0) }.single() }
            db.insertOrThrow("workout", null, values("started_at" to System.currentTimeMillis(), "day_name" to dayName, "split_day_id" to splitDayId))
        }
    }

    fun addSplitDays(days: List<Pair<String, List<String>>>) = transaction { db ->
        for ((name, muscles) in days) insertSplitDay(db, name, muscles, emptyList())
    }

    fun addSplitDay(name: String, muscles: List<String>, exerciseIds: List<Long>) = transaction { db -> insertSplitDay(db, name, muscles, exerciseIds) }

    private fun insertSplitDay(db: SQLiteDatabase, name: String, muscles: List<String>, exerciseIds: List<Long>) {
        val position = query(db, "SELECT COALESCE(MAX(position), -1) + 1 FROM split_day") { it.getInt(0) }.single()
        val id = db.insertOrThrow("split_day", null, values("position" to position, "name" to validDayName(name), "muscles" to validMuscles(muscles)))
        setDayExercises(db, id, exerciseIds)
    }

    fun updateSplitDay(id: Long, name: String, muscles: List<String>, exerciseIds: List<Long>) = transaction { db ->
        check(db.update("split_day", values("name" to validDayName(name), "muscles" to validMuscles(muscles)), "id = ?", arrayOf(id.toString())) == 1)
        setDayExercises(db, id, exerciseIds)
    }

    private fun setDayExercises(db: SQLiteDatabase, dayId: Long, exerciseIds: List<Long>) {
        require(exerciseIds.distinct().size == exerciseIds.size)
        db.delete("split_day_exercise", "split_day_id = ?", arrayOf(dayId.toString()))
        exerciseIds.forEachIndexed { position, exerciseId ->
            db.insertOrThrow("split_day_exercise", null, values("split_day_id" to dayId, "position" to position, "exercise_id" to exerciseId))
        }
    }

    /** Workouts keep their copied day name; only the rotation link is cleared. */
    fun deleteSplitDay(id: Long) { check(writableDatabase.delete("split_day", "id = ?", arrayOf(id.toString())) == 1) }

    fun clearSplit() { writableDatabase.delete("split_day", null, null) }

    /** Moves a day one place earlier (-1) or later (+1) in the rotation. */
    fun moveSplitDay(id: Long, offset: Int) = transaction { db ->
        val ids = query(db, "SELECT id FROM split_day ORDER BY position, id") { it.getLong(0) }.toMutableList()
        val from = ids.indexOf(id)
        check(from >= 0)
        val to = (from + offset).coerceIn(0, ids.lastIndex)
        ids.add(to, ids.removeAt(from))
        ids.forEachIndexed { position, dayId -> db.update("split_day", values("position" to position), "id = ?", arrayOf(dayId.toString())) }
    }

    private fun validDayName(name: String): String = name.trim().also { require(it.isNotEmpty() && it.length <= MAX_DAY_NAME_LENGTH) }
    private fun validMuscles(muscles: List<String>): String {
        require(muscles.isNotEmpty() && muscles.all { it in muscleGroups })
        return muscleGroups.filter { it in muscles }.joinToString(",")
    }

    fun finish(id: Long) {
        writableDatabase.execSQL("UPDATE workout SET ended_at = MAX(started_at, ?), partner_equipment = NULL WHERE id = ? AND ended_at IS NULL", arrayOf(System.currentTimeMillis(), id))
        check(query(readableDatabase, "SELECT id FROM workout WHERE id = ? AND ended_at IS NOT NULL", arrayOf(id.toString())) { it.getLong(0) }.isNotEmpty())
    }

    /** Removes a completed workout and every set recorded in it. The active workout cannot be deleted here. */
    fun deleteWorkout(id: Long) = transaction { db ->
        check(query(db, "SELECT id FROM workout WHERE id = ? AND ended_at IS NOT NULL", arrayOf(id.toString())) { it.getLong(0) }.isNotEmpty())
        db.delete("logged_set", "workout_id = ?", arrayOf(id.toString()))
        check(db.delete("workout", "id = ?", arrayOf(id.toString())) == 1)
    }

    fun updateExercise(id: Long, name: String, favorite: Boolean, hidden: Boolean) = transaction { db ->
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_NAME_LENGTH)
        val muscle = query(db, "SELECT muscle FROM exercise WHERE id = ?", arrayOf(id.toString())) { it.getString(0) }.single()
        if (query(db, "SELECT id FROM exercise WHERE name = ? AND muscle = ? AND id != ?", arrayOf(trimmed, muscle, id.toString())) { it.getLong(0) }.isNotEmpty()) {
            throw InputConflict("Another $muscle exercise already has that name.")
        }
        check(db.update("exercise", values("name" to trimmed, "favorite" to favorite.flag, "hidden" to hidden.flag), "id = ?", arrayOf(id.toString())) == 1)
    }

    fun updateEquipment(id: Long, name: String, hidden: Boolean) = transaction { db ->
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_NAME_LENGTH)
        val exerciseId = query(db, "SELECT exercise_id FROM equipment WHERE id = ?", arrayOf(id.toString())) { it.getLong(0) }.single()
        if (query(db, "SELECT id FROM equipment WHERE exercise_id = ? AND name = ? AND id != ?", arrayOf(exerciseId.toString(), trimmed, id.toString())) { it.getLong(0) }.isNotEmpty()) {
            throw InputConflict("This exercise already has equipment with that name.")
        }
        check(db.update("equipment", values("name" to trimmed, "hidden" to hidden.flag), "id = ?", arrayOf(id.toString())) == 1)
    }

    /** Deletion is only for entries without recorded sets; recorded history is hidden instead of rewritten. */
    fun deleteExercise(id: Long) = transaction { db ->
        if (query(db, "SELECT 1 FROM logged_set s JOIN equipment e ON e.id = s.equipment_id WHERE e.exercise_id = ? LIMIT 1", arrayOf(id.toString())) { 1 }.isNotEmpty()) {
            throw InputConflict("This exercise has recorded sets. Hide it instead.")
        }
        db.execSQL("UPDATE workout SET selected_equipment = NULL WHERE selected_equipment IN (SELECT id FROM equipment WHERE exercise_id = ?)", arrayOf(id))
        db.execSQL("UPDATE workout SET partner_equipment = NULL WHERE partner_equipment IN (SELECT id FROM equipment WHERE exercise_id = ?)", arrayOf(id))
        db.delete("equipment", "exercise_id = ?", arrayOf(id.toString()))
        check(db.delete("exercise", "id = ?", arrayOf(id.toString())) == 1)
    }

    fun deleteEquipment(id: Long) = transaction { db ->
        if (query(db, "SELECT 1 FROM logged_set WHERE equipment_id = ? LIMIT 1", arrayOf(id.toString())) { 1 }.isNotEmpty()) {
            throw InputConflict("This equipment has recorded sets. Hide it instead.")
        }
        if (query(db, "SELECT COUNT(*) FROM equipment WHERE exercise_id = (SELECT exercise_id FROM equipment WHERE id = ?)", arrayOf(id.toString())) { it.getInt(0) }.single() <= 1) {
            throw InputConflict("An exercise needs at least one equipment option.")
        }
        db.execSQL("UPDATE workout SET selected_equipment = NULL WHERE selected_equipment = ?", arrayOf(id))
        db.execSQL("UPDATE workout SET partner_equipment = NULL WHERE partner_equipment = ?", arrayOf(id))
        check(db.delete("equipment", "id = ?", arrayOf(id.toString())) == 1)
    }

    fun select(workoutId: Long, equipmentId: Long) = transaction { db ->
        val workout = activeWorkout(db, workoutId)
        // Choosing the partner (or another machine for the partner's exercise) swaps the two halves instead of ending the superset.
        val exerciseOf = { id: Long -> query(db, "SELECT exercise_id FROM equipment WHERE id = ?", arrayOf(id.toString())) { it.getLong(0) }.single() }
        val partner = workout.partnerEquipment?.let { current ->
            if (current == equipmentId || exerciseOf(current) == exerciseOf(equipmentId)) workout.selectedEquipment else current
        }
        check(db.update("workout", values("selected_equipment" to equipmentId, "partner_equipment" to partner?.takeIf { it != equipmentId }),
            "id = ? AND ended_at IS NULL", arrayOf(workoutId.toString())) == 1)
    }

    /** Pairs the selected equipment with [partnerId] as a superset, or ends the superset when null. */
    fun pair(workoutId: Long, partnerId: Long?) = transaction { db ->
        val workout = activeWorkout(db, workoutId)
        if (partnerId != null) {
            val selected = workout.selectedEquipment ?: throw InputConflict("Choose the first exercise before adding a superset.")
            val exercises = query(db, "SELECT exercise_id FROM equipment WHERE id IN (?, ?)", arrayOf(selected.toString(), partnerId.toString())) { it.getLong(0) }
            if (exercises.size != 2 || exercises[0] == exercises[1]) throw InputConflict("A superset needs two different exercises.")
        }
        check(db.update("workout", values("partner_equipment" to partnerId), "id = ? AND ended_at IS NULL", arrayOf(workoutId.toString())) == 1)
    }

    private fun activeWorkout(db: SQLiteDatabase, workoutId: Long): Workout =
        query(db, "SELECT $workoutFields FROM workout WHERE id = ? AND ended_at IS NULL", arrayOf(workoutId.toString()), ::readWorkout).single()

    fun addExercise(name: String, muscle: String): Long = transaction { db ->
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH && muscle in muscleGroups)
        db.insertWithOnConflict("exercise", null, values("name" to name.trim(), "muscle" to muscle), SQLiteDatabase.CONFLICT_IGNORE)
        val id = query(db, "SELECT id FROM exercise WHERE name = ? AND muscle = ?", arrayOf(name.trim(), muscle)) { it.getLong(0) }.single()
        db.update("exercise", values("hidden" to 0), "id = ?", arrayOf(id.toString()))
        equipment(db, id, "Unspecified equipment", LoadKind.UNSPECIFIED, 1)
    }

    fun addEquipment(exerciseId: Long, name: String, loadKind: LoadKind, implements: Int): Long = transaction { db ->
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH)
        require(implements in 1..MAX_IMPLEMENTS && (loadKind == LoadKind.PER_IMPLEMENT || implements == 1))
        equipment(db, exerciseId, name.trim(), loadKind, implements)
    }

    private fun findEquipment(db: SQLiteDatabase, exerciseId: Long, name: String): Equipment? =
        query(db, "SELECT $equipmentFields FROM equipment WHERE exercise_id = ? AND name = ?", arrayOf(exerciseId.toString(), name), ::readEquipment).firstOrNull()

    private fun equipment(db: SQLiteDatabase, exerciseId: Long, name: String, kind: LoadKind, implements: Int): Long {
        val existing = findEquipment(db, exerciseId, name)
        if (existing != null) {
            if (existing.loadKind != kind || existing.implements != implements) {
                throw InputConflict("That equipment name already uses a different load type. Select the existing equipment or use a different name.")
            }
            if (existing.hidden) db.update("equipment", values("hidden" to 0), "id = ?", arrayOf(existing.id.toString()))
            return existing.id
        }
        return db.insertOrThrow("equipment", null, values("exercise_id" to exerciseId, "name" to name, "load_kind" to kind.name, "implements" to implements))
    }

    /**
     * Saves a set on the selected equipment. With [drop], it becomes a drop segment of this workout's latest
     * set of the same exercise. In a superset, the set joins or opens a round and selection switches to the partner.
     */
    fun saveSet(workoutId: Long, equipmentId: Long, grams: Int, reps: Int, drop: Boolean = false): SavedSet = transaction { db ->
        require(grams in 0..MAX_GRAMS && reps in 1..MAX_REPS)
        val equipment = query(db, "SELECT $equipmentFields FROM equipment WHERE id = ?", arrayOf(equipmentId.toString()), ::readEquipment).single()
        val workout = activeWorkout(db, workoutId)
        val values = values("workout_id" to workoutId, "equipment_id" to equipmentId, "grams" to grams, "reps" to reps,
            "logged_at" to System.currentTimeMillis(), "load_kind" to equipment.loadKind.name, "implements" to equipment.implements)
        if (drop) {
            val parent = query(db, "SELECT s.id FROM logged_set s JOIN equipment e ON e.id = s.equipment_id " +
                "WHERE s.workout_id = ? AND s.parent_set_id IS NULL AND e.exercise_id = ? ORDER BY s.id DESC LIMIT 1",
                arrayOf(workoutId.toString(), equipment.exerciseId.toString())) { it.getLong(0) }.firstOrNull()
                ?: throw InputConflict("Save the set first, then its drop.")
            values.put("parent_set_id", parent)
            if (workout.selectedEquipment != equipmentId) select(workoutId, equipmentId)
            return@transaction SavedSet(db.insertOrThrow("logged_set", null, values), completedRound = false)
        }
        val partner = workout.partnerEquipment
        if (partner == null || equipmentId !in setOfNotNull(workout.selectedEquipment, partner)) {
            if (workout.selectedEquipment != equipmentId) select(workoutId, equipmentId)
            return@transaction SavedSet(db.insertOrThrow("logged_set", null, values), completedRound = false)
        }
        // A round is one set of each half. Join the open round started by the other half, or open a new one.
        val open = query(db, "SELECT supersetGroup, equipment_id FROM (SELECT superset_group AS supersetGroup, equipment_id FROM logged_set " +
            "WHERE workout_id = ? AND parent_set_id IS NULL ORDER BY id DESC LIMIT 1)", arrayOf(workoutId.toString())) {
            if (it.isNull(0)) null else it.getLong(0) to it.getLong(1)
        }.firstOrNull()?.takeIf { (group, otherEquipment) ->
            otherEquipment != equipmentId &&
                query(db, "SELECT COUNT(*) FROM logged_set WHERE superset_group = ?", arrayOf(group.toString())) { it.getInt(0) }.single() == 1
        }?.first
        val id = db.insertOrThrow("logged_set", null, values.apply { if (open != null) put("superset_group", open) })
        if (open == null) db.update("logged_set", values("superset_group" to id), "id = ?", arrayOf(id.toString()))
        val other = if (equipmentId == partner) workout.selectedEquipment!! else partner
        check(db.update("workout", values("selected_equipment" to other, "partner_equipment" to equipmentId), "id = ?", arrayOf(workoutId.toString())) == 1)
        SavedSet(id, completedRound = open != null)
    }

    fun editSet(id: Long, grams: Int, reps: Int) {
        require(grams in 0..MAX_GRAMS && reps in 1..MAX_REPS)
        check(writableDatabase.update("logged_set", values("grams" to grams, "reps" to reps), "id = ?", arrayOf(id.toString())) == 1)
    }

    /** Deletes a set and its drop segments. With [undo], a superset selection that advanced past it returns to its equipment. */
    fun deleteSet(id: Long, undo: Boolean = false) = transaction { db ->
        val set = query(db, "SELECT $setFields FROM logged_set WHERE id = ?", arrayOf(id.toString()), ::readSet).single()
        if (undo && set.parentSetId == null && set.supersetGroup != null) {
            val workout = query(db, "SELECT $workoutFields FROM workout WHERE id = ? AND ended_at IS NULL", arrayOf(set.workoutId.toString()), ::readWorkout).firstOrNull()
            if (workout?.partnerEquipment == set.equipmentId) {
                db.update("workout", values("selected_equipment" to set.equipmentId, "partner_equipment" to workout.selectedEquipment), "id = ?", arrayOf(workout.id.toString()))
            }
        }
        check(db.delete("logged_set", "id = ?", arrayOf(id.toString())) == 1)
    }

    fun feedback(workoutId: Long, feeling: String?, note: String) {
        require((feeling == null || feeling in feelings) && note.length <= MAX_NOTE_LENGTH)
        check(writableDatabase.update("workout", values("feeling" to feeling, "note" to note.trim()), "id = ? AND ended_at IS NOT NULL", arrayOf(workoutId.toString())) == 1)
    }

    internal fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try { val result = block(db); db.setTransactionSuccessful(); return result }
        finally { db.endTransaction() }
    }

    private fun <T> query(db: SQLiteDatabase, sql: String, args: Array<String>? = null, map: (Cursor) -> T): List<T> =
        db.rawQuery(sql, args).use { cursor -> buildList { while (cursor.moveToNext()) add(map(cursor)) } }

    private fun readEquipment(c: Cursor) = Equipment(c.getLong(0), c.getLong(1), c.getString(2), LoadKind.valueOf(c.getString(3)), c.getInt(4), c.getInt(5) == 1)
    private val Boolean.flag: Int get() = if (this) 1 else 0
    private fun readWorkout(c: Cursor) = Workout(c.getLong(0), c.getLong(1), c.nullableLong(2), c.nullableLong(3), if (c.isNull(4)) null else c.getString(4), c.getString(5),
        if (c.isNull(6)) null else c.getString(6), c.nullableLong(7), c.nullableLong(8))
    private fun readSet(c: Cursor) = LoggedSet(c.getLong(0), c.getLong(1), c.getLong(2), c.getInt(3), c.getInt(4), c.getLong(5), LoadKind.valueOf(c.getString(6)), c.getInt(7),
        c.nullableLong(8), c.nullableLong(9))
    private fun Cursor.nullableLong(column: Int): Long? = if (isNull(column)) null else getLong(column)
    private fun values(vararg entries: Pair<String, Any?>): ContentValues = ContentValues(entries.size).apply {
        entries.forEach { (key, value) -> when (value) {
            null -> putNull(key)
            is String -> put(key, value)
            is Long -> put(key, value)
            is Int -> put(key, value)
            else -> error("Unsupported value")
        } }
    }

    companion object {
        const val SCHEMA_VERSION = 5
        /** Tables in foreign-key order: every table only references tables listed before it (or itself, by smaller id). */
        val tables = listOf("exercise", "equipment", "split_day", "split_day_exercise", "workout", "logged_set")

        /** Monday 00:00 local time of the week containing [now]. */
        fun weekStart(now: Long): Long = Calendar.getInstance().apply {
            timeInMillis = now
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis

        private const val kindColumn = "load_kind TEXT NOT NULL DEFAULT 'UNSPECIFIED' CHECK(load_kind IN ('TOTAL','PER_IMPLEMENT','ADDED_LOAD','ASSISTANCE','UNSPECIFIED'))"
        private const val implementsColumn = "implements INTEGER NOT NULL DEFAULT 1 CHECK(implements BETWEEN 1 AND 2 AND (load_kind = 'PER_IMPLEMENT' OR implements = 1))"
        private const val favoriteColumn = "favorite INTEGER NOT NULL DEFAULT 0 CHECK(favorite IN (0, 1))"
        private const val hiddenColumn = "hidden INTEGER NOT NULL DEFAULT 0 CHECK(hidden IN (0, 1))"
        private const val loadColumns = "$kindColumn, $implementsColumn"
        private const val equipmentFields = "id, exercise_id, name, load_kind, implements, hidden"
        private const val workoutFields = "id, started_at, ended_at, selected_equipment, feeling, note, day_name, split_day_id, partner_equipment"
        private const val splitDayTable = "CREATE TABLE split_day (id INTEGER PRIMARY KEY, position INTEGER NOT NULL, name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 40), muscles TEXT NOT NULL CHECK(length(muscles) > 0))"
        private const val splitDayExerciseTable = "CREATE TABLE split_day_exercise (split_day_id INTEGER NOT NULL REFERENCES split_day(id) ON DELETE CASCADE, position INTEGER NOT NULL, exercise_id INTEGER NOT NULL REFERENCES exercise(id) ON DELETE CASCADE, PRIMARY KEY(split_day_id, exercise_id))"
        private const val dayNameColumn = "day_name TEXT CHECK(day_name IS NULL OR length(day_name) <= 40)"
        private const val splitDayColumn = "split_day_id INTEGER REFERENCES split_day(id) ON DELETE SET NULL"
        private const val partnerColumn = "partner_equipment INTEGER REFERENCES equipment(id)"
        private const val parentColumn = "parent_set_id INTEGER REFERENCES logged_set(id) ON DELETE CASCADE"
        private const val supersetColumn = "superset_group INTEGER"
        private const val setFields = "id, workout_id, equipment_id, grams, reps, logged_at, load_kind, implements, parent_set_id, superset_group"
    }
}
