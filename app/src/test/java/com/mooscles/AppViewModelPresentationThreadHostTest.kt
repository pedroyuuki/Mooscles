package com.mooscles

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.ui.UiState
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Where and in which order the ViewModel presents saved data; real Room and DataStore, no timing claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelPresentationThreadHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("mooscles.db")
    }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    @Test fun presentRunsOffTheMainThread() {
        val workoutId = runBlocking(Dispatchers.IO) {
            app.preferences.autoRest(false)
            app.repository.initialize()
            app.repository.startEmpty()
        }
        val model = newModel()
        awaitUi(model) { !it.loading && it.active?.id == workoutId }
        model.renameWorkout(workoutId, "Presented in the background")
        awaitUi(model) { it.active?.name == "Presented in the background" }
        assertTrue("Opening and the rename must each be presented", model.presentedCount.get() >= 2)
        assertEquals("present() must never run on the main thread", 0, model.presentedOnMainThread.get())
    }

    @Test fun latestDataWinsAfterQuickSuccessiveChanges() {
        val exerciseId = runBlocking(Dispatchers.IO) {
            app.preferences.autoRest(false)
            app.repository.initialize()
            app.repository.createCustomExercise("Quick press", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        }
        val model = newModel()
        awaitUi(model) { !it.loading && it.exercises.any { e -> e.id == exerciseId } }
        val seen = CopyOnWriteArrayList<UiState>()
        model.viewModelScope.launch(Dispatchers.Unconfined) { model.contentUi.collect { seen += it } }

        // Back-to-back writes, without waiting for any of them to be presented.
        val finished = runBlocking(Dispatchers.IO) {
            val repo = app.repository
            (1..3).map { round ->
                val id = repo.startEmpty(name = "Quick $round", now = round * 10_000L)
                val entry = repo.addWorkoutExercise(id, exerciseId)
                val set = repo.snapshot().exerciseSets(entry).first()
                repo.updateSet(set.copy(reps = round, weightKg = 20.0 + round, completed = true))
                repo.finishWorkout(id, now = round * 10_000L + 5_000L)
                id
            }
        }
        val latest: (UiState) -> Boolean = { state ->
            state.active == null && state.history.map { it.id }.toSet() == finished.toSet()
        }
        awaitUi(model, latest)
        // Give any stale presentation the chance to arrive after the latest one.
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }

        assertTrue(latest(model.contentUi.value))
        val first = seen.indexOfFirst(latest)
        assertTrue("The latest data was never published", first >= 0)
        assertTrue("No earlier state may be published after the latest one", seen.drop(first).all(latest))
        val sizes = seen.map { it.history.size }
        assertEquals("History must only grow while the writes are presented in order", sizes.sorted(), sizes)
    }

    @Test fun firstStateStillLoadingUntilPresented() {
        val routineId = runBlocking(Dispatchers.IO) {
            app.repository.initialize()
            app.repository.createRoutine("Shown after presentation")
        }
        val model = newModel()
        // Every state with the number of presentations finished when it reached the collector.
        val seen = CopyOnWriteArrayList<Pair<UiState, Int>>()
        model.viewModelScope.launch(Dispatchers.Unconfined) {
            model.contentUi.collect { seen += it to model.presentedCount.get() }
        }
        assertTrue("A new model starts loading", model.contentUi.value.loading)
        awaitUi(model) { !it.loading && it.routines.any { routine -> routine.id == routineId } }

        assertTrue("The first published state is the loading one", seen.first().first.loading)
        val early = seen.filter { (state, presented) -> !state.loading && presented == 0 }
        assertTrue("No ready state may be published before the first presentation: $early", early.isEmpty())
    }

    private fun newModel() = AppViewModel(app).also { models += it }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!predicate(model.contentUi.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected state was not published: ${model.contentUi.value.error}", predicate(model.contentUi.value))
    }
}
