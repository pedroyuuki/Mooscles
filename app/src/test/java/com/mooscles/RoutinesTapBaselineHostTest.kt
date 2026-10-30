package com.mooscles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.lang.reflect.Proxy

/**
 * TEMPORARY (phase 4B, Task 15 step 1): counts the taps (performClick, never typed text) the OLD routine
 * screens need. Deleted with those screens; the numbers live in tap-baseline-4b.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class RoutinesTapBaselineHostTest {
    @get:Rule val compose = createComposeRule()
    private var taps = 0
    private val exercises = (1..6).map { ExerciseUi("ex$it", "Movement $it", "CHEST", "BARBELL") }
    private val ui = mutableStateOf(UiState(loading = false, exercises = exercises))
    private val editing = mutableStateOf<String?>(null)
    private var nextId = 0

    private fun SemanticsNodeInteraction.tap() { taps++; performClick() }
    private fun update(f: (UiState) -> UiState) { ui.value = f(ui.value) }
    private fun mapRoutines(id: String, f: (RoutineUi) -> RoutineUi) = update { s -> s.copy(routines = s.routines.map { if (it.id == id) f(it) else it }) }

    private val actions: MoosclesActions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { proxy, m, a ->
        when (m.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === a?.firstOrNull()
            "toString" -> "baseline actions"
            "createRoutine" -> update { it.copy(routines = it.routines + RoutineUi("r${nextId++}", a[0] as String)) }
            "addRoutineExercise" -> mapRoutines(a[0] as String) { r ->
                r.copy(exercises = r.exercises + PlannedExerciseUi("p${nextId++}", exercises.first { it.id == a[1] }, setTargets = listOf(RoutineSetUi("s${nextId++}", 8), RoutineSetUi("s${nextId++}", 8))))
            }
            "moveRoutineExercise" -> update { s -> s.copy(routines = s.routines.map { r ->
                val i = r.exercises.indexOfFirst { it.id == a[0] }; val j = i + (a[1] as Int)
                if (i < 0 || j !in r.exercises.indices) r else r.copy(exercises = r.exercises.toMutableList().also { l -> l.add(j, l.removeAt(i)) })
            }) }
            "addRoutineSet" -> update { s -> s.copy(routines = s.routines.map { r -> r.copy(exercises = r.exercises.map { p ->
                if (p.id == a[0]) p.copy(setTargets = p.setTargets + p.setTargets.last().copy(id = "s${nextId++}")) else p }) }) }
            "startWorkout" -> update { it.copy(active = WorkoutUi("w", "Workout", 0L, null, emptyList(), sourceRoutineId = a[0] as String?)) }
            else -> null
        }
    } as MoosclesActions

    private fun show() = compose.setContent {
        MaterialTheme {
            val s = ui.value
            val id = editing.value
            val r = s.routines.find { it.id == id }
            if (r != null) RoutineEditor(r, s, actions) {} else RoutinesScreen(s, actions, onEdit = { editing.value = it }) {}
        }
    }

    @Test fun measure() {
        show()
        // 1. New routine with 5 exercises, from the Train tab to the editor holding all 5.
        compose.onNodeWithTag("routines-list").performScrollToNode(hasText("New routine"))
        compose.onNodeWithText("New routine").tap()
        compose.onNodeWithText("Routine name").performTextInput("Push")
        compose.onNodeWithText("Save").tap()
        compose.onNodeWithTag("routines-list").performScrollToNode(hasText("Push"))
        compose.onNodeWithText("Push").tap() // opens the editor
        for (i in 1..5) {
            compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasText("Add exercise"))
            compose.onAllNodesWithText("Add exercise").onFirst().tap()
            compose.onNodeWithText("Find a movement").performTextInput("Movement $i") // typed text is not counted
            compose.onNode(hasText("Movement $i") and !hasSetTextAction()).tap()
        }
        val create = taps
        assertEquals(5, ui.value.routines.single().exercises.size)
        // 2. Reorder: the 5th exercise to the 1st place (the old screen only has move up/down).
        val last = ui.value.routines.single().exercises.last().id
        repeat(4) {
            compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-$last-add-set"))
            val addTop = compose.onNodeWithTag("routine-$last-add-set").fetchSemanticsNode().boundsInRoot.top
            val ups = compose.onAllNodesWithContentDescription("Move up").fetchSemanticsNodes()
            val k = ups.indexOfFirst { it.boundsInRoot.top > addTop }
            compose.onAllNodesWithContentDescription("Move up")[k].tap()
        }
        val reorder = taps - create
        assertEquals(last, ui.value.routines.single().exercises.first().id)
        // 3. Add a planned set equal to the one above.
        val first = ui.value.routines.single().exercises.first().id
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-$first-add-set"))
        val before = taps
        compose.onNodeWithTag("routine-$first-add-set").tap()
        val addSet = taps - before
        assertEquals(3, ui.value.routines.single().exercises.first().setTargets.size)
        // 4. Start the workout of a routine, from the Train tab.
        editing.value = null
        compose.waitForIdle()
        val rid = ui.value.routines.single().id
        val b2 = taps
        compose.onNodeWithTag("routines-list").performScrollToNode(hasTestTag("routine-$rid-start"))
        compose.onNodeWithTag("routine-$rid-start").tap()
        val start = taps - b2
        println("TAP-BASELINE create5=$create reorder5to1=$reorder addSetEqual=$addSet startWorkout=$start")
    }
}
