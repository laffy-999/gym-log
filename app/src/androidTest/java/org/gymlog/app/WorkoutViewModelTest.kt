package org.gymlog.app

import android.app.Application
import android.content.Context
import java.io.File
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.TestCase
import androidx.lifecycle.ViewModelStore

class WorkoutViewModelTest : TestCase() {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var isolated: IsolatedDatabaseContext
    private lateinit var model: WorkoutViewModel
    private lateinit var models: ViewModelStore

    private class TestApplication(base: Context) : Application() {
        init { attachBaseContext(base) }
    }

    override fun setUp() {
        super.setUp()
        isolated = IsolatedDatabaseContext(instrumentation.targetContext)
        models = ViewModelStore()
    }
    override fun tearDown() {
        instrumentation.runOnMainSync { models.clear() }
        RestTimer.cancel(isolated)
        isolated.deleteDatabase("training.db")
        isolated.deleteSharedPreferences("settings")
        isolated.deleteSharedPreferences("rest_timer")
        super.tearDown()
    }
    private fun createModel() {
        instrumentation.runOnMainSync {
            model = WorkoutViewModel(TestApplication(isolated))
            models.put("logger", model)
        }
        settled()
    }
    private fun settled() {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (model.state.value.busy && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(10)
        instrumentation.waitForIdleSync()
        assertFalse("Operation did not settle", model.state.value.busy)
    }
    private fun act(block: WorkoutViewModel.() -> Unit) {
        instrumentation.runOnMainSync { model.block() }
        settled()
        assertNull(model.state.value.error)
    }
    private fun selectBench() {
        val bench = model.state.value.data.exercises.single { it.name == "Flat bench press" }
        act { chooseExercise(bench) }
    }
    private fun enterAndSave(weight: String) { act { weight(weight); reps("10"); save() } }

    fun testNewWorkoutUsesCommittedLoadInsteadOfOldSessionDraft() {
        createModel()
        act { start() }
        selectBench()
        act { addEquipment("Machine A", LoadKind.TOTAL, 1) }
        val a = model.state.value.selectedEquipment!!.id
        enterAndSave("50")
        act { addEquipment("Machine B", LoadKind.TOTAL, 1) }
        act { select(a) }
        assertEquals("50", model.state.value.weight)
        enterAndSave("60")
        act { finish() }
        assertEquals(2, model.state.value.data.totals[model.state.value.detailId]!!.setCount)
        act { start() }
        selectBench()
        assertEquals(a, model.state.value.selectedEquipment!!.id)
        assertEquals("60", model.state.value.weight)
    }

    fun testAddingEquipmentAndExercisePreservesOutgoingDraft() {
        createModel()
        act { start() }
        selectBench()
        val a = model.state.value.selectedEquipment!!.id
        enterAndSave("50")
        act { weight("65"); addEquipment("New machine", LoadKind.TOTAL, 1) }
        assertEquals("", model.state.value.weight)
        act { select(a) }
        assertEquals("65", model.state.value.weight)
        act { weight("70"); addExercise("My press", "Chest") }
        act { select(a) }
        assertEquals("70", model.state.value.weight)
    }

    fun testInitialLoadFailureCannotStartAndRetryRecoversExistingSession() {
        WorkoutStore(isolated).use { store -> store.start() }
        val database = isolated.getDatabasePath("training.db")
        val held = File(database.path + ".held")
        assertTrue(database.renameTo(held))
        assertTrue(database.mkdir())
        try {
            createModel()
            assertFalse(model.state.value.ready)
            assertNotNull(model.state.value.error)
            instrumentation.runOnMainSync { model.start() }
            assertNull(model.state.value.data.active)
        } finally {
            assertTrue(database.delete())
            assertTrue(held.renameTo(database))
        }
        act { reload() }
        assertTrue(model.state.value.ready)
        assertNotNull(model.state.value.data.active)
        act { start() }
        assertEquals(1, model.state.value.data.workouts.count { it.endedAt == null })
    }

    fun testRepeatedTriggerDuringCommitCreatesOneSet() {
        createModel()
        act { start() }
        selectBench()
        act { weight("50"); reps("10"); save(); weight("55"); save() }
        assertEquals(1, model.state.value.data.sets.size)
        assertEquals(10L, model.state.value.data.totals[model.state.value.data.active!!.id]!!.reps)
        assertEquals(50_000, model.state.value.data.sets.single().grams)
        assertEquals("55", model.state.value.weight)
    }

    fun testEquipmentConflictKeepsSessionUsableAndDraftIntact() {
        createModel()
        act { start() }
        selectBench()
        val selected = model.state.value.selectedEquipment!!.id
        act { weight("45"); reps("12") }
        instrumentation.runOnMainSync { model.addEquipment("Barbell", LoadKind.PER_IMPLEMENT, 1) }
        settled()
        assertTrue(model.state.value.ready)
        assertNotNull(model.state.value.error)
        assertEquals(selected, model.state.value.selectedEquipment!!.id)
        assertEquals("45", model.state.value.weight)
        act { dismissError(); save() }
        assertEquals(45_000, model.state.value.data.sets.single().grams)
    }

    fun testPoundEntryStoresExactGramsAndSettingsPersist() {
        createModel()
        act { start() }
        selectBench()
        act { weight("100"); reps("8"); updateSettings(state.value.settings.copy(unit = WeightUnit.LB, theme = ThemeMode.LIGHT)) }
        assertEquals("220.46", model.state.value.weight)
        act { weight("100"); stepLoad(1); save() }
        assertEquals(47_627, model.state.value.data.sets.single().grams)
        instrumentation.runOnMainSync { models.clear() }
        createModel()
        assertEquals(Settings(unit = WeightUnit.LB, theme = ThemeMode.LIGHT), model.state.value.settings)
        assertEquals("105", model.state.value.weight)
    }

    fun testSavedSetStartsRestOnceAndSettingsControlIt() {
        createModel()
        act { start() }
        selectBench()
        act { weight("50"); reps("10"); save() }
        val endsAt = model.restEndsAt.value!!
        assertTrue(endsAt - System.currentTimeMillis() in 80_000..90_000)
        assertEquals(endsAt, RestTimer.endsAt(isolated))
        act { extendRest(15) }
        assertEquals(endsAt + 15_000, model.restEndsAt.value)
        act { stopRest() }
        assertNull(model.restEndsAt.value)
        assertNull(RestTimer.endsAt(isolated))
        act { startRest() }
        val manual = model.restEndsAt.value!!
        assertTrue(RestTimer.expire(isolated, manual))
        assertFalse(RestTimer.expire(isolated, manual))
        act { restElapsed(manual) }
        assertNull(model.restEndsAt.value)
        act { updateSettings(state.value.settings.copy(restAutoStart = false)); save() }
        assertNull(model.restEndsAt.value)
        act { updateSettings(state.value.settings.copy(restEnabled = false)); startRest() }
        assertNull(model.restEndsAt.value)
    }

    fun testSupersetAlternatesWithRestAfterRoundAndDropsStayOneSet() {
        createModel()
        act { start() }
        selectBench()
        val bench = model.state.value.selectedEquipment!!.id
        val pulldown = model.state.value.data.exercises.single { it.name == "Lat pulldown" }
        act { pairWith(pulldown) }
        act { weight("60"); reps("8"); save() }
        assertEquals(pulldown.id, model.state.value.selectedExercise!!.id)
        assertNull("No rest between the two halves of a round", model.restEndsAt.value)
        act { weight("50"); reps("10"); save() }
        assertEquals(bench, model.state.value.selectedEquipment!!.id)
        assertEquals("60", model.state.value.weight)
        assertNotNull("Rest starts after the round", model.restEndsAt.value)
        act { endSuperset() }
        act { toggleDrop() }
        act { weight("40"); reps("6"); save() }
        val sets = model.state.value.data.sets
        assertEquals(listOf(null, null, sets.first().id), sets.map { it.parentSetId })
        assertEquals(2, model.state.value.data.totals[model.state.value.data.active!!.id]!!.setCount)
        assertTrue(model.state.value.dropMode)
    }
}
