package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mooscles.data.LoadConvention
import com.mooscles.designsystem.components.NumericField
import com.mooscles.designsystem.components.NumericInput
import com.mooscles.designsystem.components.NumericKind
import com.mooscles.designsystem.components.SetRow
import com.mooscles.designsystem.components.SetRowState
import com.mooscles.designsystem.components.SetStatus
import com.mooscles.designsystem.components.SetType
import com.mooscles.designsystem.components.setRowSwipe
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_enter_reps
import com.mooscles.screens.resources.workout_load_of_set
import com.mooscles.screens.resources.workout_reps_of_set
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PreviousSetUi
import com.mooscles.ui.SetField
import com.mooscles.ui.SetSuggestion
import com.mooscles.ui.SetSuggestions
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import org.jetbrains.compose.resources.stringResource

/** The test tag of the [WorkoutSetRow] of [setId]. */
internal fun workoutSetRowTag(setId: String): String = "workout-set-$setId"

/** The bounds the database accepts for a set (`EditRules.workoutSet`); a completed set needs at least 1 rep. */
private const val MAX_REPS = 9999
private const val MAX_LOAD_KG = 100_000.0

/**
 * One set of the active workout, drawn with the design system's [SetRow].
 *
 * [suggestion] is what [SetSuggestions.forExercise] gives this set and [previous] its pair in the
 * previous session ([SetSuggestions.pairPrevious]); [number] is its 1-based position among the
 * [total] sets of [entry]. Every write goes through [actions]:
 *
 * - the check, with valid reps (saved or suggested), draws the disc at once ([ActiveWorkoutScreenState.optimistic])
 *   and sends `updateSet` with the effective values, the load in the source's exact kg; without
 *   valid reps it vibrates `Rejected` and opens the reps in edit with the "Enter the reps" error;
 * - unchecking sends the same set with `completed = false`;
 * - tapping the previous result of a pending set copies it (load and reps, not done) with the
 *   `StepperStep` haptic; on a done set it is not a target;
 * - swiping right is the check (only for a pending set); swiping left asks [onRemove] with the
 *   set's position in [entry];
 * - each valid value typed in a field is written as it is typed; emptying a field marks it in
 *   [ActiveWorkoutScreenState.cleared], so it gets no suggestion again.
 *
 * The record haptic stays off: the screen plays it from its own record event.
 */
@Composable
internal fun WorkoutSetRow(
    set: SetUi,
    number: Int,
    total: Int,
    entry: WorkoutExerciseUi,
    suggestion: SetSuggestion,
    previous: PreviousSetUi?,
    pounds: Boolean,
    screen: ActiveWorkoutScreenState,
    actions: MoosclesActions,
    onChangeType: (SetUi) -> Unit,
    onRemove: (SetUi, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    RecompositionProbe("row-${set.id}")
    val haptics = MTheme.haptics
    // Read through derived states, so a change for another set does not recompose this row.
    val optimistic by remember(screen, set.id) { derivedStateOf { screen.optimistic[set.id] } }
    val doneNow by remember(screen, set) { derivedStateOf { screen.completedOf(set) } }
    val editingThis by remember(screen, set.id) { derivedStateOf { screen.editing?.takeIf { it.setId == set.id } } }
    val editing = editingThis
    val done = doneNow
    LaunchedEffect(set.id, set.completed, optimistic) {
        if (optimistic != null) screen.settle(set.id, set.completed)
    }

    // What was typed in a field, until the state shows the same text; null shows the saved value.
    var loadDraft by remember(set.id) { mutableStateOf<String?>(null) }
    var repsDraft by remember(set.id) { mutableStateOf<String?>(null) }
    val savedLoad = if (set.weightKg == 0.0) "" else loadText(set.weightKg, pounds)
    val savedReps = if (set.reps == 0) "" else set.reps.toString()
    LaunchedEffect(set.id, savedLoad) { if (loadDraft == savedLoad) loadDraft = null }
    LaunchedEffect(set.id, savedReps) { if (repsDraft == savedReps) repsDraft = null }
    val shownLoad = loadDraft ?: savedLoad
    val shownReps = repsDraft ?: savedReps

    val maxLoad = WeightUnits.fromKg(MAX_LOAD_KG, pounds)
    // The saved text is the saved kg, exactly: "80" lb must not become 36.2873896… instead of 36.287389.
    val loadKgOf: (String) -> Double? = { text ->
        when {
            text == savedLoad -> set.weightKg
            text.isEmpty() -> 0.0
            else -> NumericInput.parseDecimal(text, max = maxLoad)?.let { WeightUnits.toKg(it, pounds) }
        }
    }
    val repsOf: (String) -> Int? = { text -> if (text.isEmpty()) 0 else NumericInput.parseInteger(text, MAX_REPS) }
    // An invalid draft is neither sent nor used: the saved value stands.
    val loadKg = loadKgOf(shownLoad) ?: set.weightKg
    val reps = repsOf(shownReps) ?: set.reps
    // A suggestion fills only a field that shows nothing.
    val loadSuggestion = suggestion.weightKg.takeIf { shownLoad.isEmpty() }
    val repsSuggestion = suggestion.reps.takeIf { shownReps.isEmpty() }
    val effective = SetSuggestions.effective(set.copy(weightKg = loadKg, reps = reps), SetSuggestion(loadSuggestion, repsSuggestion))

    // A done set with no reps would be refused by the database: that value waits as a draft.
    val write: (Double, Int) -> Unit = { kg, count ->
        if (!(done && count == 0)) actions.updateSet(set.id, count, kg, done, set.warmup, set.assistance)
    }
    val complete: () -> Unit = {
        if (effective.reps in 1..MAX_REPS) {
            screen.expect(set.id, true)
            screen.editing = null
            // An empty or invalid draft gave way to the value sent; the echo shows that value.
            if (loadDraft?.let { it.isEmpty() || loadKgOf(it) == null } == true) loadDraft = null
            if (repsDraft?.let { it.isEmpty() || repsOf(it) == null } == true) repsDraft = null
            actions.updateSet(set.id, effective.reps, effective.weightKg, true, set.warmup, set.assistance)
        } else {
            haptics.perform(HapticEvent.Rejected)
            screen.editing = SetEditTarget(set.id, SetField.REPS, error = true)
        }
    }
    val uncheck: () -> Unit = {
        screen.expect(set.id, false)
        screen.editing = null
        actions.updateSet(set.id, reps, loadKg, false, set.warmup, set.assistance)
    }
    val copyPrevious: (() -> Unit)? = if (previous != null && !done) {
        {
            haptics.perform(HapticEvent.StepperStep)
            loadDraft = null
            repsDraft = null
            actions.updateSet(set.id, previous.reps, previous.weightKg, false, set.warmup, set.assistance)
        }
    } else {
        null
    }
    val onLoadChange: (String) -> Unit = { text ->
        loadDraft = text
        if (text.isEmpty() && shownLoad.isNotEmpty()) screen.clear(set.id, SetField.LOAD)
        loadKgOf(text)?.let { write(it, reps) }
    }
    val onRepsChange: (String) -> Unit = { text ->
        repsDraft = text
        if (text.isEmpty() && shownReps.isNotEmpty()) screen.clear(set.id, SetField.REPS)
        repsOf(text)?.let { count ->
            if (count > 0 && editing?.error == true) screen.editing = editing.copy(error = false)
            write(loadKg, count)
        }
    }

    val status = when {
        !done -> SetStatus.Pending
        set.completed && set.record != null -> SetStatus.CompletedRecord
        else -> SetStatus.Completed
    }
    // In the Assistance convention every set is assisted, so none is marked (F29).
    val type = when {
        set.warmup -> SetType.Warmup
        set.assistance && entry.exercise.convention != LoadConvention.ASSISTANCE.label -> SetType.Assisted
        else -> SetType.Normal
    }
    val state = SetRowState(
        index = number,
        total = total,
        // Null without a pair: the design system then draws "—" and reads the row without it.
        previous = previous?.let { previousText(it, pounds) },
        previousSpoken = previous?.let { previousSpoken(it, pounds) },
        load = loadText(effective.weightKg, pounds),
        loadSpoken = loadSpoken(effective.weightKg, pounds),
        reps = effective.reps,
        type = type,
        status = status,
        loadSuggested = loadSuggestion != null,
        repsSuggested = repsSuggestion != null,
        editing = editing != null,
    )
    val editLoad = { screen.editing = SetEditTarget(set.id, SetField.LOAD) }
    val position = number - 1
    SetRow(
        state = state,
        onCompleteChange = { if (it) complete() else uncheck() },
        onEditLoad = editLoad,
        onEditReps = { screen.editing = SetEditTarget(set.id, SetField.REPS) },
        onChangeType = { onChangeType(set) },
        onRemove = { onRemove(set, position) },
        modifier = modifier
            .testTag(workoutSetRowTag(set.id))
            .setRowSwipe(
                completeEnabled = !done,
                removeEnabled = true,
                onComplete = complete,
                onRemove = { onRemove(set, position) },
            ),
        loadField = {
            NumericField(
                value = shownLoad,
                onValueChange = onLoadChange,
                kind = NumericKind.Load,
                accessibilityLabel = stringResource(Res.string.workout_load_of_set, number),
                suggested = loadSuggestion?.let { loadText(it, pounds) },
                selected = editing?.field == SetField.LOAD,
            )
        },
        repsField = {
            NumericField(
                value = shownReps,
                onValueChange = onRepsChange,
                kind = NumericKind.Reps,
                accessibilityLabel = stringResource(Res.string.workout_reps_of_set, number),
                suggested = repsSuggestion?.toString(),
                error = if (editing?.field == SetField.REPS && editing.error) stringResource(Res.string.workout_enter_reps) else null,
                selected = editing?.field == SetField.REPS,
            )
        },
        onEditRow = editLoad,
        onCopyPrevious = copyPrevious,
        recordHaptic = false,
    )
}
