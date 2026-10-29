package com.mooscles.screens.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.mooscles.designsystem.components.ActiveWorkoutBarState
import com.mooscles.designsystem.components.RestTimerVisual
import com.mooscles.designsystem.components.TimerPhase
import com.mooscles.designsystem.components.spokenDuration
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.resources.ds_sets_done_of
import com.mooscles.designsystem.resources.ds_timer_rest
import com.mooscles.screens.format.Durations
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.shell_elapsed_under_minute
import com.mooscles.ui.TimerUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DsRes

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60

/** Whole seconds from [startedAt] to [now]; zero when the clock is behind the start. */
internal fun elapsedSeconds(startedAt: Long, now: Long): Long = (now - startedAt).coerceAtLeast(0L) / MS_PER_SECOND

/** The phase of the rest the bar shows for [timer]: running, paused or just done; null without a rest. */
internal fun restPhase(timer: TimerUi): TimerPhase? = when {
    timer.finished -> TimerPhase.Done
    timer.paused -> TimerPhase.Paused
    timer.running -> TimerPhase.Running
    else -> null
}

/**
 * The seconds of the workout started at [startedAt]: read from [nowMillis] when first composed,
 * then once a second, on the second the clock turns. Only the composable that reads the value
 * recomposes when it changes.
 */
@Composable
internal fun rememberElapsedSeconds(startedAt: Long, nowMillis: () -> Long): State<Long> {
    val clock = rememberUpdatedState(nowMillis)
    return produceState(elapsedSeconds(startedAt, nowMillis()), startedAt) {
        while (true) {
            val millis = (clock.value() - startedAt).coerceAtLeast(0L)
            value = millis / MS_PER_SECOND
            delay(MS_PER_SECOND - millis % MS_PER_SECOND)
        }
    }
}

/**
 * What the in-progress bar shows for [workout] after [elapsedSeconds] with [timer], localized:
 * the elapsed clock ("42:18", "1:02:18") read in whole minutes ("42 minutes", "1 hour and
 * 2 minutes"; "less than a minute" before the first), the sets done of all ("9 of 15 sets") and,
 * while a rest runs, is paused or has just ended, the rest and its reading.
 */
@Composable
internal fun workoutBarState(workout: WorkoutUi, elapsedSeconds: Long, timer: TimerUi): ActiveWorkoutBarState {
    val sets = remember(workout.exercises) { workout.completedSets to workout.exercises.sumOf { it.sets.size } }
    val minutes = (elapsedSeconds / SECONDS_PER_MINUTE).toInt()
    val elapsedSpoken = if (minutes == 0) {
        stringResource(Res.string.shell_elapsed_under_minute)
    } else {
        spokenDuration(minutes * SECONDS_PER_MINUTE)
    }
    val phase = restPhase(timer)
    val restLabel = stringResource(DsRes.string.ds_timer_rest)
    val remaining = timer.remainingSeconds.coerceAtLeast(0)
    return ActiveWorkoutBarState(
        workoutName = workout.name,
        elapsedText = Durations.clock(elapsedSeconds),
        elapsedSpoken = elapsedSpoken,
        setsText = pluralCount(DsRes.plurals.ds_sets_done_of, sets.second, sets.first, sets.second),
        rest = phase?.let { RestTimerVisual(remaining, timer.durationSeconds, it, restLabel) },
        restSpoken = if (phase != null) spokenDuration(remaining) else null,
    )
}
