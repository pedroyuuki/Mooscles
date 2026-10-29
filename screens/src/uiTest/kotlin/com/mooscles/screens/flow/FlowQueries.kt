package com.mooscles.screens.flow

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.mooscles.designsystem.components.ExerciseHeaderToggleTag
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.screens.workout.ActiveWorkoutTags
import com.mooscles.screens.workout.exerciseColumnsTag
import com.mooscles.screens.workout.exerciseHeaderTag
import com.mooscles.screens.workout.workoutSetRowTag
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi

// What the flows over the real stack (ActiveWorkoutFlowTest, TapBudgetTest) read and find: the view
// model's state, which comes from Room, and the nodes of the workout screen.

/** How long a flow waits, in real time, for the view model and Room. */
internal const val WAIT_MS = 20_000L

// --- The view model's state ---------------------------------------------------------------

internal fun active(vm: FlowViewModel): WorkoutUi = requireNotNull(vm.contentUi.value.active) { "No active workout" }

internal fun entry(vm: FlowViewModel, id: String): WorkoutExerciseUi = active(vm).exercises.first { it.id == id }

internal fun set(vm: FlowViewModel, id: String): SetUi? =
    vm.contentUi.value.active?.exercises?.firstNotNullOfOrNull { e -> e.sets.firstOrNull { it.id == id } }

/** Waits, in real time, for [condition]: the view model and Room work on their own threads. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.await(what: String, condition: () -> Boolean) {
    waitUntil(what, timeoutMillis = WAIT_MS, condition = condition)
}

// --- Finding and acting ---------------------------------------------------------------------

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.exists(matcher: SemanticsMatcher, unmerged: Boolean = false): Boolean =
    onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

internal fun row(id: String): SemanticsMatcher = hasTestTag(workoutSetRowTag(id))

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.check(id: String): SemanticsNodeInteraction = onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(row(id)))

/** The editable text of [cell] (the load or reps tag) in the row of [id]. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.field(id: String, cell: String): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(cell)) and hasAnyAncestor(row(id)), useUnmergedTree = true)

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.scrollToRow(id: String) {
    onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(row(id))
    waitForIdle()
}

/**
 * Opens the card of [entryId] by its header's toggle when it is collapsed (the advance closes a
 * card once its sets are all done). An open card shows its column titles right under the header.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openCard(entryId: String) {
    onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(exerciseHeaderTag(entryId)))
    waitForIdle()
    if (exists(hasTestTag(exerciseColumnsTag(entryId)), unmerged = true)) return
    onNode(hasTestTag(ExerciseHeaderToggleTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag(entryId)))).performClick()
    await("the card opens") { exists(hasTestTag(exerciseColumnsTag(entryId)), unmerged = true) }
    waitForIdle()
}
