package com.mooscles.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.currentTimeMillis
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UiState

/**
 * The `workout` route: the active workout ([ActiveWorkoutScreen]) when there is one; while it
 * opens ([UiState.startingWorkout], or the state still loading), [skeletonCards] placeholders
 * shaped like the cards ([WorkoutSkeleton]); otherwise "No workout in progress" ([NoWorkout]),
 * with "Choose routine" ([onChooseRoutine]) and "Empty workout" (`startWorkout(null)`).
 *
 * The screen's own state ([rememberActiveWorkoutScreenState]) and its toast host live here, so
 * they outlast the switch from the placeholders to the workout. Around everything, a tap outside
 * the row in edit ends the edit ([endsSetEditingOnOutsideTap]). The undo toast of a removal
 * ([UndoRemovalToast]) and the error toast ([WorkoutErrorToast]: the localized [UiState.errorCode],
 * never [UiState.error]) share that host.
 *
 * Confirming "Finish" keeps the workout's id ([rememberSaveable], so it survives recreation) and
 * calls `finishWorkout`; once the workout is in [UiState.history], [onOpenSummary] gets its id.
 * Meanwhile the finish buttons show their loading state; an error gives them back. [timer] is not
 * read here yet: the rest surfaces read it (Task 20). [imeInsets] is the keyboard, which the list
 * and the toasts keep clear of; [nowMillis] is the clock the header's time reads.
 */
@Composable
fun ActiveWorkoutRoute(
    state: UiState,
    @Suppress("UNUSED_PARAMETER") timer: State<TimerUi>,
    actions: MoosclesActions,
    onMinimize: () -> Unit,
    onOpenSummary: (workoutId: String) -> Unit,
    onExerciseHistory: (exerciseId: String) -> Unit,
    onChooseRoutine: () -> Unit,
    modifier: Modifier = Modifier,
    skeletonCards: Int = 1,
    imeInsets: WindowInsets = WindowInsets.ime,
    nowMillis: () -> Long = { currentTimeMillis() },
) {
    val screen = rememberActiveWorkoutScreenState()
    val host = remember { MToastHostState() }
    var finishingId by rememberSaveable { mutableStateOf<String?>(null) }
    var summaryOpened by rememberSaveable { mutableStateOf<String?>(null) }
    val openSummary by rememberUpdatedState(onOpenSummary)
    val active = state.active

    LaunchedEffect(state.history, finishingId) {
        val id = finishingId ?: return@LaunchedEffect
        // Once: the route stays blank while the app moves to the summary.
        if (summaryOpened != id && state.history.any { it.id == id }) {
            summaryOpened = id
            openSummary(id)
        }
    }
    // Another workout in progress (started after this one finished) ends the wait.
    LaunchedEffect(active?.id) {
        if (active != null && finishingId != null && active.id != finishingId) finishingId = null
    }
    WorkoutErrorToast(state.errorCode, screen, host, actions, onError = { finishingId = null })

    Box(modifier.fillMaxSize().testTag(ActiveWorkoutTags.ROUTE).background(MTheme.colors.bgBase).endsSetEditingOnOutsideTap(screen)) {
        when {
            active != null -> ActiveWorkoutScreen(
                workout = active,
                catalog = state.exercises,
                history = state.history,
                pounds = state.preferences.pounds,
                loaded = !state.loading,
                discarding = state.discardingWorkout,
                screen = screen,
                host = host,
                actions = actions,
                finishing = finishingId != null,
                onConfirmFinish = {
                    finishingId = active.id
                    actions.finishWorkout()
                },
                onMinimize = onMinimize,
                onExerciseHistory = onExerciseHistory,
                imeInsets = imeInsets,
                nowMillis = nowMillis,
            )
            // Finished: blank once the history has the workout (the summary opens) and while the app
            // moves there, so "No workout in progress" never flashes. A workout that left without
            // reaching the history falls through to the empty state, so the screen is never a dead end.
            finishingId != null && (summaryOpened == finishingId || state.history.any { it.id == finishingId }) -> Unit
            state.loading || state.startingWorkout -> WorkoutSkeleton(skeletonCards, onMinimize)
            else -> NoWorkout(onChooseRoutine, onEmptyWorkout = { actions.startWorkout(null) })
        }
        MToastHost(host, Modifier.fillMaxSize().windowInsetsPadding(imeInsets.only(WindowInsetsSides.Bottom)))
    }
    UndoRemovalToast(screen, host, actions)
}
