package org.gymlog.app

import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.TestCase

class WorkoutStoreTest : TestCase() {
    private lateinit var isolated: IsolatedDatabaseContext
    private lateinit var store: WorkoutStore

    override fun setUp() {
        super.setUp()
        isolated = IsolatedDatabaseContext(InstrumentationRegistry.getInstrumentation().targetContext)
        store = WorkoutStore(isolated)
    }
    override fun tearDown() {
        store.close()
        isolated.deleteDatabase("training.db")
        super.tearDown()
    }

    fun testLoadSemanticsCorrectionsAndPersistence() {
        val catalog = store.load()
        val bench = catalog.exercises.single { it.name == "Flat bench press" }
        val bar = catalog.equipment.single { it.exerciseId == bench.id && it.name == "Barbell" }
        val dumbbells = catalog.equipment.single { it.exerciseId == bench.id && it.name == "Dumbbells" }
        val assist = store.addEquipment(bench.id, "Assisted machine", LoadKind.ASSISTANCE, 1)
        val added = store.addEquipment(bench.id, "Bodyweight", LoadKind.ADDED_LOAD, 1)
        store.start()
        val id = store.load().active!!.id
        store.start()
        assertEquals(id, store.load().active!!.id)
        val first = store.saveSet(id, bar.id, 50_000, 10).id
        store.saveSet(id, dumbbells.id, 20_000, 10)
        store.saveSet(id, assist, 30_000, 8)
        store.saveSet(id, added, 0, 12)
        assertEquals(WorkoutTotals(4, 40, 900_000, 1), store.load().totals[id])
        store.editSet(first, 50_000, 8)
        assertEquals(WorkoutTotals(4, 38, 800_000, 1), store.load().totals[id])
        val accidental = store.saveSet(id, bar.id, 60_000, 1).id
        store.deleteSet(accidental)
        assertEquals(50_000, store.load().latestSets.single { it.equipmentId == bar.id }.grams)
        store.finish(id)
        val ended = store.load(detailId = id).workouts.single { it.id == id }.endedAt
        store.finish(id)
        store.feedback(id, "Okay", "Knee discomfort")
        store.close()
        store = WorkoutStore(isolated)
        val reopened = store.load(detailId = id)
        assertNull(reopened.active)
        assertEquals(WorkoutTotals(4, 38, 800_000, 1), reopened.totals[id])
        val workout = reopened.workouts.single { it.id == id }
        assertEquals(ended, workout.endedAt)
        assertEquals("Okay", workout.feeling)
        assertEquals("Knee discomfort", workout.note)
        assertEquals(LoadKind.PER_IMPLEMENT, reopened.sets.single { it.equipmentId == dumbbells.id }.loadKind)
        assertEquals(2, reopened.sets.single { it.equipmentId == dumbbells.id }.implements)
    }

    fun testHistoryGroupsSetsByExerciseInTrainingOrder() {
        val data = store.load()
        val bench = data.exercises.single { it.name == "Flat bench press" }
        val pulldown = data.exercises.single { it.name == "Lat pulldown" }
        val bar = data.equipment.single { it.exerciseId == bench.id && it.name == "Barbell" }.id
        val dumbbells = data.equipment.single { it.exerciseId == bench.id && it.name == "Dumbbells" }.id
        val cable = data.equipment.single { it.exerciseId == pulldown.id }.id
        store.start()
        val id = store.load().active!!.id
        store.saveSet(id, cable, 40_000, 10)
        store.saveSet(id, bar, 60_000, 8)
        store.saveSet(id, cable, 40_000, 9)
        store.saveSet(id, dumbbells, 20_000, 10)
        store.finish(id)
        assertEquals(listOf(ExerciseSets(pulldown.id, 2), ExerciseSets(bench.id, 2)), store.load().exerciseSets[id])
    }

    fun testHistoryPagesDoNotMaterializeArchiveSets() {
        val equipment = store.load().equipment.first().id
        repeat(35) {
            store.start()
            val id = store.load().active!!.id
            store.saveSet(id, equipment, 1000, 1)
            store.finish(id)
        }
        val first = store.load()
        assertEquals(30, first.workouts.size)
        assertTrue(first.hasOlder)
        assertTrue(first.sets.isEmpty())
        val second = store.load(historyBefore = first.workouts.minOf { it.id })
        assertEquals(5, second.workouts.size)
        assertFalse(second.hasOlder)
        assertTrue(first.workouts.map { it.id }.intersect(second.workouts.map { it.id }.toSet()).isEmpty())
        val detail = store.load(detailId = second.workouts.last().id)
        assertEquals(listOf(second.workouts.last().id), detail.sets.map { it.workoutId })
        assertEquals(35L, detail.latestSets.single().workoutId)
    }

    fun testMigrationPreservesLegacyHistoryWithoutGuessingLoad() {
        store.close()
        isolated.openOrCreateDatabase("training.db", 0, null).use { db ->
            db.execSQL("CREATE TABLE exercise(id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, muscle TEXT NOT NULL, UNIQUE(name,muscle))")
            db.execSQL("CREATE TABLE equipment(id INTEGER PRIMARY KEY, exercise_id INTEGER NOT NULL REFERENCES exercise(id), name TEXT NOT NULL COLLATE NOCASE, UNIQUE(exercise_id,name))")
            db.execSQL("CREATE TABLE workout(id INTEGER PRIMARY KEY, started_at INTEGER NOT NULL, ended_at INTEGER, selected_equipment INTEGER REFERENCES equipment(id))")
            db.execSQL("CREATE UNIQUE INDEX one_active_workout ON workout((1)) WHERE ended_at IS NULL")
            db.execSQL("CREATE TABLE logged_set(id INTEGER PRIMARY KEY, workout_id INTEGER NOT NULL REFERENCES workout(id), equipment_id INTEGER NOT NULL REFERENCES equipment(id), grams INTEGER NOT NULL, reps INTEGER NOT NULL, logged_at INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX sets_by_workout ON logged_set(workout_id,id)")
            db.execSQL("CREATE INDEX sets_by_equipment ON logged_set(equipment_id,id)")
            db.execSQL("INSERT INTO exercise VALUES(7,'Flat bench press','Chest')")
            db.execSQL("INSERT INTO equipment VALUES(9,7,'Barbell')")
            db.execSQL("INSERT INTO workout VALUES(11,1000,NULL,9)")
            db.execSQL("INSERT INTO logged_set VALUES(13,11,9,50000,10,2000)")
            db.version = 1
        }
        store = WorkoutStore(isolated)
        val data = store.load()
        assertEquals(11L, data.active!!.id)
        assertEquals(LoggedSet(13, 11, 9, 50_000, 10, 2000), data.sets.single())
        assertEquals(WorkoutTotals(1, 10, 0, 1), data.totals[11])
        assertEquals(LoadKind.UNSPECIFIED, data.equipment.single { it.id == 9L }.loadKind)
        assertTrue(data.equipment.any { it.exerciseId == 7L && it.loadKind == LoadKind.TOTAL && it.id != 9L })
        assertTrue(data.equipment.any { it.exerciseId == 7L && it.loadKind == LoadKind.PER_IMPLEMENT && it.implements == 2 })
    }

    fun testUpgradeFromV2KeepsCustomCatalogVisibleAndUnstarred() {
        store.close()
        isolated.openOrCreateDatabase("training.db", 0, null).use { db ->
            db.execSQL("CREATE TABLE exercise (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, muscle TEXT NOT NULL, UNIQUE(name, muscle))")
            db.execSQL("CREATE TABLE equipment (id INTEGER PRIMARY KEY, exercise_id INTEGER NOT NULL REFERENCES exercise(id), name TEXT NOT NULL COLLATE NOCASE, load_kind TEXT NOT NULL DEFAULT 'UNSPECIFIED', implements INTEGER NOT NULL DEFAULT 1, UNIQUE(exercise_id, name))")
            db.execSQL("CREATE TABLE workout (id INTEGER PRIMARY KEY, started_at INTEGER NOT NULL, ended_at INTEGER, selected_equipment INTEGER REFERENCES equipment(id), feeling TEXT, note TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE UNIQUE INDEX one_active_workout ON workout((1)) WHERE ended_at IS NULL")
            db.execSQL("CREATE TABLE logged_set (id INTEGER PRIMARY KEY, workout_id INTEGER NOT NULL REFERENCES workout(id), equipment_id INTEGER NOT NULL REFERENCES equipment(id), grams INTEGER NOT NULL, reps INTEGER NOT NULL, logged_at INTEGER NOT NULL, load_kind TEXT NOT NULL DEFAULT 'UNSPECIFIED', implements INTEGER NOT NULL DEFAULT 1)")
            db.execSQL("CREATE INDEX sets_by_workout ON logged_set(workout_id, id)")
            db.execSQL("CREATE INDEX sets_by_equipment ON logged_set(equipment_id, id)")
            db.execSQL("CREATE INDEX completed_workouts ON workout(id) WHERE ended_at IS NOT NULL")
            db.execSQL("INSERT INTO exercise VALUES(3,'My press','Chest')")
            db.execSQL("INSERT INTO equipment VALUES(5,3,'Panatta','TOTAL',1)")
            db.execSQL("INSERT INTO workout VALUES(8,1000,2000,5,'Good','Knee')")
            db.execSQL("INSERT INTO logged_set VALUES(13,8,5,40000,12,1500,'TOTAL',1)")
            db.version = 2
        }
        store = WorkoutStore(isolated)
        val data = store.load(detailId = 8)
        assertEquals(Exercise(3, "My press", "Chest"), data.exercises.single())
        assertEquals(Equipment(5, 3, "Panatta", LoadKind.TOTAL, 1), data.equipment.single())
        assertEquals(LoggedSet(13, 8, 5, 40_000, 12, 1500, LoadKind.TOTAL, 1), data.sets.single())
        assertEquals("Knee", data.workouts.single().note)
    }

    fun testDeletingWorkoutRemovesOnlyItsSetsAndNeverTheActiveWorkout() {
        val equipment = store.load().equipment.first().id
        store.start()
        val kept = store.load().active!!.id
        store.saveSet(kept, equipment, 10_000, 5)
        store.finish(kept)
        store.start()
        val removed = store.load().active!!.id
        store.saveSet(removed, equipment, 20_000, 6)
        try { store.deleteWorkout(removed); fail("Active workout deleted") } catch (_: IllegalStateException) { }
        store.finish(removed)
        store.deleteWorkout(removed)
        val data = store.load(detailId = kept)
        assertEquals(listOf(kept), data.workouts.map { it.id })
        assertEquals(WorkoutTotals(1, 5, 0, 1), data.totals[kept])
        assertEquals(kept, data.latestSets.single().workoutId)
    }

    fun testCatalogChangesNeverDeleteRecordedHistory() {
        val data = store.load()
        val bench = data.exercises.single { it.name == "Flat bench press" }
        val bar = data.equipment.single { it.exerciseId == bench.id && it.name == "Barbell" }
        store.start()
        val id = store.load().active!!.id
        store.saveSet(id, bar.id, 60_000, 5)
        try { store.deleteExercise(bench.id); fail("Exercise with sets deleted") } catch (_: InputConflict) { }
        try { store.deleteEquipment(bar.id); fail("Equipment with sets deleted") } catch (_: InputConflict) { }
        try { store.updateExercise(bench.id, "dips", favorite = false, hidden = false); fail("Duplicate name accepted") } catch (_: InputConflict) { }
        store.updateExercise(bench.id, "Bench", favorite = true, hidden = true)
        store.updateEquipment(bar.id, "Olympic bar", hidden = true)
        val updated = store.load()
        assertEquals(Exercise(bench.id, "Bench", "Chest", favorite = true, hidden = true), updated.exercises.single { it.id == bench.id })
        assertTrue(updated.equipment.single { it.id == bar.id }.let { it.name == "Olympic bar" && it.hidden })
        assertEquals(60_000, updated.sets.single().grams)
        val spare = store.addEquipment(bench.id, "Spare", LoadKind.TOTAL, 1)
        store.select(id, spare)
        store.deleteEquipment(spare)
        assertNull(store.load().active!!.selectedEquipment)
        val unusedEquipment = store.addExercise("Unused", "Chest")
        try { store.deleteEquipment(unusedEquipment); fail("Last equipment option deleted") } catch (_: InputConflict) { }
        val unusedExercise = store.load().equipment.single { it.id == unusedEquipment }.exerciseId
        store.deleteExercise(unusedExercise)
        assertTrue(store.load().exercises.none { it.id == unusedExercise })
    }

    fun testSplitTemplatesCombineRotateAndNeverRewriteHistory() {
        store.addSplitDays(splitTemplates.single { it.name == "Push / Pull / Legs" }.days)
        store.addSplitDays(splitTemplates.single { it.name == "Full body" }.days)
        var data = store.load()
        assertEquals(listOf("Push", "Pull", "Legs", "Full body"), data.splitDays.map { it.name })
        assertEquals("Push", data.nextDay!!.name)
        val legs = data.splitDays[2]
        val fullBody = data.splitDays[3]
        store.start(legs.id)
        store.finish(store.load().active!!.id)
        data = store.load()
        assertEquals("Full body", data.nextDay!!.name)
        assertEquals("Legs", data.workouts.single().dayName)
        store.moveSplitDay(fullBody.id, -1)
        data = store.load()
        assertEquals(listOf("Push", "Pull", "Full body", "Legs"), data.splitDays.map { it.name })
        assertEquals("Push", data.nextDay!!.name)
        store.updateSplitDay(legs.id, "Lower", listOf("Core", "Legs"), emptyList())
        assertEquals(SplitDay(legs.id, "Lower", listOf("Legs", "Core")), store.load().splitDays.last())
        assertEquals("Legs", store.load().workouts.single().dayName)
        store.deleteSplitDay(legs.id)
        data = store.load()
        assertEquals("Legs", data.workouts.single().dayName)
        assertNull(data.workouts.single().splitDayId)
        assertEquals("Push", data.nextDay!!.name)
        try { store.addSplitDays(listOf("Neck day" to listOf("Neck"))); fail("Unknown muscle group accepted") } catch (_: IllegalArgumentException) { }
        try { store.addSplitDays(listOf("Empty" to emptyList())); fail("Day without muscles accepted") } catch (_: IllegalArgumentException) { }
        store.clearSplit()
        assertNull(store.load().nextDay)
    }

    fun testInvalidAndIncompatibleEntriesCannotChangeRecordedFacts() {
        val data = store.load()
        val equipment = data.equipment.first()
        store.start()
        val id = store.load().active!!.id
        try { store.saveSet(id, equipment.id, -1, 10); fail("Negative load accepted") } catch (_: IllegalArgumentException) { }
        try { store.addEquipment(equipment.exerciseId, equipment.name, LoadKind.TOTAL, 1); fail("Convention silently replaced") } catch (_: IllegalArgumentException) { }
        assertEquals(WorkoutTotals(), store.load().totals[id])
        val big = store.addEquipment(equipment.exerciseId, "Paired", LoadKind.PER_IMPLEMENT, 2)
        store.saveSet(id, big, MAX_GRAMS, MAX_REPS)
        assertEquals(3_996_000_000L, store.load().totals[id]!!.volumeGramReps)
        assertNull(WeightUnit.KG.parse("0.0001"))
        assertNull(WeightUnit.KG.parse("2000.001"))
        assertEquals(1250, WeightUnit.KG.parse("1,25"))
        assertEquals(45_359, WeightUnit.LB.parse("100"))
        assertEquals("100", WeightUnit.LB.format(45_359))
        assertEquals("2866", WeightUnit.LB.volume(1_300_000L))
        assertNull(WeightUnit.LB.parse("-5"))
        assertEquals("0", steppedLoad("1", "2.5", -1))
        assertEquals("47.5", steppedLoad("45", "2,5", 1))
    }

    private fun benchSetup(): Triple<Long, Equipment, Equipment> {
        val data = store.load()
        val bench = data.exercises.single { it.name == "Flat bench press" }
        val bar = data.equipment.single { it.exerciseId == bench.id && it.name == "Barbell" }
        val pulldown = data.exercises.single { it.name == "Lat pulldown" }
        val cable = data.equipment.single { it.exerciseId == pulldown.id }
        store.start()
        return Triple(store.load().active!!.id, bar, cable)
    }

    fun testDropSegmentsAddRepsAndVolumeButNotSets() {
        val (id, bar, _) = benchSetup()
        try { store.saveSet(id, bar.id, 40_000, 8, drop = true); fail("Drop without a set accepted") } catch (_: InputConflict) { }
        val main = store.saveSet(id, bar.id, 60_000, 8).id
        val drop = store.saveSet(id, bar.id, 40_000, 6, drop = true).id
        store.saveSet(id, bar.id, 30_000, 5, drop = true)
        var data = store.load()
        assertEquals(listOf(null, main, main), data.sets.map { it.parentSetId })
        assertEquals(WorkoutTotals(1, 19, 870_000, 0), data.totals[id])
        assertEquals(mapOf("Chest" to 1), data.weekSets)
        assertEquals("A drop segment must not become the next set's prefill", main, data.latestSets.single { it.equipmentId == bar.id }.id)
        store.deleteSet(drop)
        assertEquals(WorkoutTotals(1, 13, 630_000, 0), store.load().totals[id])
        store.deleteSet(main)
        data = store.load()
        assertTrue("Drop segments must be deleted with their set", data.sets.isEmpty())
        store.saveSet(id, bar.id, 50_000, 5)
        store.finish(id)
        assertEquals(WorkoutTotals(1, 5, 250_000, 0), store.load().totals[id])
    }

    fun testSupersetAlternatesAndGroupsRounds() {
        val (id, bar, cable) = benchSetup()
        store.select(id, bar.id)
        try { store.pair(id, store.load().equipment.single { it.exerciseId == bar.exerciseId && it.name == "Dumbbells" }.id); fail("Same exercise paired") } catch (_: InputConflict) { }
        store.pair(id, cable.id)
        val a1 = store.saveSet(id, bar.id, 60_000, 8)
        assertFalse(a1.completedRound)
        assertEquals(cable.id, store.load().active!!.selectedEquipment)
        val b1 = store.saveSet(id, cable.id, 50_000, 10)
        assertTrue(b1.completedRound)
        assertEquals(bar.id, store.load().active!!.selectedEquipment)
        val a2 = store.saveSet(id, bar.id, 60_000, 7)
        store.deleteSet(a2.id, undo = true)
        assertEquals("Undo returns to the undone set's equipment", bar.id, store.load().active!!.selectedEquipment)
        assertEquals(cable.id, store.load().active!!.partnerEquipment)
        val data = store.load()
        assertEquals(listOf(a1.id, a1.id), data.sets.map { it.supersetGroup })
        assertEquals("Unspecified cable load is excluded from volume", WorkoutTotals(2, 18, 480_000, 1), data.totals[id])
        store.pair(id, null)
        store.saveSet(id, bar.id, 60_000, 6)
        assertNull(store.load().sets.last().supersetGroup)
        store.finish(id)
        assertNull(store.load(detailId = id).workouts.single().partnerEquipment)
    }

    fun testDayExercisesKeepOrderAndFollowCatalogDeletes() {
        val data = store.load()
        val bench = data.exercises.single { it.name == "Flat bench press" }
        val dips = data.exercises.single { it.name == "Dips" }
        store.addSplitDay("Push", listOf("Chest", "Arms"), listOf(dips.id, bench.id))
        val unusedEquipment = store.addExercise("Cable press", "Chest")
        val unused = store.load().equipment.single { it.id == unusedEquipment }.exerciseId
        val day = store.load().splitDays.single()
        store.updateSplitDay(day.id, day.name, day.muscles, listOf(bench.id, unused, dips.id))
        assertEquals(listOf(bench.id, unused, dips.id), store.load().splitDays.single().exerciseIds)
        store.deleteExercise(unused)
        assertEquals(listOf(bench.id, dips.id), store.load().splitDays.single().exerciseIds)
        try { store.updateSplitDay(day.id, day.name, day.muscles, listOf(bench.id, bench.id)); fail("Duplicate exercise accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(bench.id, dips.id), store.load().splitDays.single().exerciseIds)
    }

    fun testProgressShowsRecentSessionsAndHeaviestSet() {
        val (first, bar, cable) = benchSetup()
        store.saveSet(first, bar.id, 60_000, 5)
        store.saveSet(first, bar.id, 40_000, 3, drop = true)
        store.saveSet(first, cable.id, 90_000, 9)
        store.finish(first)
        store.start()
        val second = store.load().active!!.id
        store.saveSet(second, bar.id, 60_000, 8)
        store.saveSet(second, bar.id, 55_000, 10)
        val progress = store.progress(bar.id)
        assertEquals(listOf(second, first), progress.sessions.map { it.first.id })
        assertEquals(listOf(listOf(60_000, 55_000), listOf(60_000, 40_000)), progress.sessions.map { (_, sets) -> sets.map { it.grams } })
        assertEquals(60_000 to 8, progress.best!!.let { it.grams to it.reps })
        assertNull(store.progress(store.load().equipment.single { it.exerciseId == bar.exerciseId && it.name == "Dumbbells" }.id).best)
    }

    fun testBackupRoundTripRestoresEverythingAndBadFilesChangeNothing() {
        val (id, bar, cable) = benchSetup()
        store.select(id, bar.id)
        store.pair(id, cable.id)
        store.saveSet(id, bar.id, 60_000, 8)
        store.saveSet(id, cable.id, 50_000, 10)
        store.saveSet(id, bar.id, 40_000, 6, drop = true)
        store.finish(id)
        store.feedback(id, "Good", "Knee ok")
        store.addSplitDay("Push", listOf("Chest"), listOf(bar.exerciseId))
        store.updateExercise(bar.exerciseId, "Bench", favorite = true, hidden = false)
        store.start(store.load().splitDays.single().id)
        val settings = Settings(unit = WeightUnit.LB, restSeconds = 120, platesKg = "20, 10")
        val bytes = java.io.ByteArrayOutputStream().also { Backup.export(store, settings, it) }.toByteArray()
        val before = dump()

        val other = IsolatedDatabaseContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val target = WorkoutStore(other)
        try {
            target.addExercise("Will be replaced", "Core")
            assertEquals(settings, Backup.import(target, bytes.inputStream()))
            assertEquals(before, dump(target))
            assertEquals(store.load(detailId = id), target.load(detailId = id))
            val restored = dump(target)
            for (bad in listOf("{}", "not json", String(bytes).replace("\"grams\": 60000", "\"grams\": -5"),
                String(bytes).replace("gym-log-backup", "other-app"), String(bytes).replace("\"name\": \"Bench\"", "\"name\": [1]"))) {
                try { Backup.import(target, bad.byteInputStream()); fail("Bad backup accepted") } catch (_: InputConflict) { }
                assertEquals("A rejected backup must not change anything", restored, dump(target))
            }
        } finally {
            target.close()
            other.deleteDatabase("training.db")
        }
    }

    /** Every row of every table, for exact comparisons. */
    private fun dump(target: WorkoutStore = store): Map<String, List<List<String?>>> = WorkoutStore.tables.associateWith { table ->
        target.readableDatabase.rawQuery("SELECT * FROM $table ORDER BY rowid", null).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
        }
    }

    fun testUpgradeFromV4AddsSupersetDropAndPlanColumns() {
        store.close()
        isolated.openOrCreateDatabase("training.db", 0, null).use { db ->
            db.execSQL("CREATE TABLE exercise (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, muscle TEXT NOT NULL, favorite INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0, UNIQUE(name, muscle))")
            db.execSQL("CREATE TABLE equipment (id INTEGER PRIMARY KEY, exercise_id INTEGER NOT NULL REFERENCES exercise(id), name TEXT NOT NULL COLLATE NOCASE, load_kind TEXT NOT NULL DEFAULT 'UNSPECIFIED', implements INTEGER NOT NULL DEFAULT 1, hidden INTEGER NOT NULL DEFAULT 0, UNIQUE(exercise_id, name))")
            db.execSQL("CREATE TABLE split_day (id INTEGER PRIMARY KEY, position INTEGER NOT NULL, name TEXT NOT NULL, muscles TEXT NOT NULL)")
            db.execSQL("CREATE TABLE workout (id INTEGER PRIMARY KEY, started_at INTEGER NOT NULL, ended_at INTEGER, selected_equipment INTEGER REFERENCES equipment(id), feeling TEXT, note TEXT NOT NULL DEFAULT '', day_name TEXT, split_day_id INTEGER REFERENCES split_day(id) ON DELETE SET NULL)")
            db.execSQL("CREATE UNIQUE INDEX one_active_workout ON workout((1)) WHERE ended_at IS NULL")
            db.execSQL("CREATE TABLE logged_set (id INTEGER PRIMARY KEY, workout_id INTEGER NOT NULL REFERENCES workout(id), equipment_id INTEGER NOT NULL REFERENCES equipment(id), grams INTEGER NOT NULL, reps INTEGER NOT NULL, logged_at INTEGER NOT NULL, load_kind TEXT NOT NULL DEFAULT 'UNSPECIFIED', implements INTEGER NOT NULL DEFAULT 1)")
            db.execSQL("CREATE INDEX sets_by_workout ON logged_set(workout_id, id)")
            db.execSQL("CREATE INDEX sets_by_equipment ON logged_set(equipment_id, id)")
            db.execSQL("CREATE INDEX completed_workouts ON workout(id) WHERE ended_at IS NOT NULL")
            db.execSQL("INSERT INTO exercise VALUES(3,'My press','Chest',1,0)")
            db.execSQL("INSERT INTO equipment VALUES(5,3,'Panatta','TOTAL',1,0)")
            db.execSQL("INSERT INTO split_day VALUES(2,0,'Push','Chest')")
            db.execSQL("INSERT INTO workout VALUES(8,1000,2000,5,NULL,'','Push',2)")
            db.execSQL("INSERT INTO logged_set VALUES(13,8,5,40000,12,1500,'TOTAL',1)")
            db.version = 4
        }
        store = WorkoutStore(isolated)
        val data = store.load(detailId = 8)
        assertEquals(LoggedSet(13, 8, 5, 40_000, 12, 1500, LoadKind.TOTAL, 1), data.sets.single())
        assertEquals(SplitDay(2, "Push", listOf("Chest")), data.splitDays.single())
        assertNull(data.workouts.single().partnerEquipment)
        store.addSplitDay("Legs", listOf("Legs"), listOf(3))
        assertEquals(listOf(3L), store.load().splitDays.last().exerciseIds)
    }

    fun testPlateCalculatorFindsFewestPlatesOrClosestLighterLoad() {
        val kg = parsePlates("25, 20, 15, 10, 5, 2.5, 1.25")!!
        val exact = plateLoad("100".toBigDecimal(), "20".toBigDecimal(), kg)!!
        assertEquals(listOf("25", "15"), exact.perSide.map { it.stripTrailingZeros().toPlainString() })
        assertTrue(exact.exact)
        val close = plateLoad("101".toBigDecimal(), "20".toBigDecimal(), kg)!!
        assertFalse(close.exact)
        assertEquals("100", close.total.toPlainString())
        assertEquals(listOf("20", "10"), plateLoad("80".toBigDecimal(), "20".toBigDecimal(), parsePlates("20, 15, 10")!!)!!.perSide.map { it.toPlainString() })
        assertNull(plateLoad("15".toBigDecimal(), "20".toBigDecimal(), kg))
        assertTrue(plateLoad("20".toBigDecimal(), "20".toBigDecimal(), kg)!!.perSide.isEmpty())
        assertNull(parsePlates("20, x"))
        assertNull(parsePlates("20, -5"))
    }
}
