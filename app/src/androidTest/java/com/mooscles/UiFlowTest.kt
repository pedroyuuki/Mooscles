package com.mooscles

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.components.StepperDecreaseTag
import com.mooscles.designsystem.components.StepperValueTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room + DataStore + Activity flow, through the new workout and summary (Task 24). The
 * generated routine name isolates repeated runs.
 */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun routineToWorkoutRestSummaryAndHistory() {
        val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
        val name = "Flow test ${System.currentTimeMillis()}"
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("routines-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("routines-list").performScrollToNode(hasText("New routine"))
        compose.onNodeWithText("New routine").performClick()
        compose.onNodeWithText("Routine name").performTextInput(name)
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("routines-list").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
        compose.onAllNodesWithText("Add exercise").onFirst().performClick()
        compose.onNodeWithText("Find a movement").performTextInput("Barbell bench press")
        compose.onNode(hasText("Barbell bench press", substring = false) and !hasSetTextAction()).performClick()
        var entryId: String? = null
        var firstSetId: String? = null
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Barbell bench press").fetchSemanticsNodes()
            val data = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
            val routineId = data.routines.singleOrNull { it.name == name }?.id
            val entry = routineId?.let { data.routineItems(it).singleOrNull() }
            val rows = entry?.let { data.plannedSets(it.id) }.orEmpty()
            entryId = entry?.id
            firstSetId = rows.firstOrNull()?.id
            rows.size == 2
        }
        val repsTag = "routine-set-$firstSetId-reps"
        // Room can finish before the saved routine reaches Compose. Wait for the
        // actual lazy-list item, scrolling on each attempt so this also works on small screens.
        compose.waitUntil(30_000) {
            try {
                compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag(repsTag))
                compose.onNodeWithTag(repsTag).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onNodeWithTag(repsTag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag(repsTag).performTextReplacement("8")
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-$entryId-menu"))
        compose.onNodeWithTag("routine-$entryId-menu").performClick()
        compose.onNodeWithText("Edit Rest Timer").performClick()
        compose.onNodeWithTag("routine-$entryId-rest").performTextReplacement("2:00")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Save targets").assertDoesNotExist()
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasText("Start this routine"))
        compose.onNodeWithText("Start this routine").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("workout-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-bar").assertDoesNotExist()
        val started = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        val planned = started.routineItems(started.routines.single { it.name == name }.id).single()
        val workoutExercise = started.workoutItems(started.activeWorkout!!.id).single()
        assertEquals(8, planned.targetReps)
        assertEquals(120, planned.restSeconds)
        assertEquals(120, workoutExercise.restSeconds)
        assertEquals(planned.routineId, started.activeWorkout!!.sourceRoutineId)
        assertEquals(listOf(8, 0), started.plannedSets(planned.id).map { it.targetReps })
        val sets = started.exerciseSets(workoutExercise.id)
        assertEquals(listOf(8, 0), sets.map { it.reps })
        assertTrue(sets.none { it.completed })
        // This workout's next rests, from the exercise's rest sheet: 2:00 - 0:15.
        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("exercise-rest-${workoutExercise.id}"))
        compose.onNodeWithTag("exercise-rest-${workoutExercise.id}").performClick()
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:00")
        compose.onNodeWithTag(StepperDecreaseTag).performClick()
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("1:45")
        compose.onNodeWithTag("sheet-scrim").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("sheet-scrim").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) { app.repository.snapshot() }.workoutExercises.single { it.id == workoutExercise.id }.restSeconds == 105
        }
        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("workout-set-${sets[0].id}"))
        field(sets[0].id, SetRowRepsTag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("8")))
        field(sets[0].id, SetRowLoadTag).performTextReplacement("20")
        field(sets[0].id, SetRowRepsTag).performTextReplacement("10")
        compose.onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(hasTestTag("workout-set-${sets[0].id}"))).performClick()
        compose.waitUntil(10_000) { app.timer.state.value.let { it.running && it.durationMillis == 105_000L } }
        assertEquals(105_000L, app.timer.state.value.durationMillis)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("workout-timer-layer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("workout-timer-layer").assertIsDisplayed()
        compose.onNodeWithTag("workout-header-finish").performClick()
        compose.onNodeWithTag("dialog-confirm").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Workout complete").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Workout complete").assertIsDisplayed()
        compose.onNodeWithTag("tab-history").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("history-list").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("SESSION BREAKDOWN").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SESSION BREAKDOWN").assertIsDisplayed()
        val saved = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        assertEquals(105, saved.workoutExercises.single { it.id == workoutExercise.id }.restSeconds)
        assertEquals(120, saved.routineExercises.single { it.id == planned.id }.restSeconds)

    }

    /** The editable field in the [cell] of the set row of [setId]. */
    private fun field(setId: String, cell: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(cell)) and
        hasAnyAncestor(hasTestTag("workout-set-$setId")), useUnmergedTree = true)
}
