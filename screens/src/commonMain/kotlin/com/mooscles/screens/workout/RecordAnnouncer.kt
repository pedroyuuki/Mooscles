package com.mooscles.screens.workout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.PrToast
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_record_reps_detail
import com.mooscles.screens.resources.workout_record_reps_title
import com.mooscles.screens.resources.workout_record_weight_detail
import com.mooscles.screens.resources.workout_record_weight_title
import com.mooscles.ui.RecordKind
import com.mooscles.ui.SetRecordUi
import com.mooscles.ui.WorkoutUi
import org.jetbrains.compose.resources.stringResource

/**
 * A record to announce: the set [setId] of [exerciseName], done at [weightKg] × [reps] (exact kg),
 * beat [record]. [key] is `"<setId>:<weightKg>:<reps>:<kind>"`: the same set at the same value is
 * announced once; at a new value (edited, or beating the record again), once more.
 */
data class RecordEvent(
    val key: String,
    val setId: String,
    val exerciseName: String,
    val record: SetRecordUi,
    val weightKg: Double,
    val reps: Int,
)

/**
 * Which record the active workout announces (spec D17, D18). The record is the presentation's
 * (`SetUi.record`, chosen by the domain), never the screen's guess; the screen only decides
 * whether to celebrate it.
 */
object RecordAnnouncer {
    /** The key of [record] on the set [setId] done at [weightKg] × [reps]. */
    fun key(setId: String, weightKg: Double, reps: Int, record: SetRecordUi): String = "$setId:$weightKg:$reps:${record.kind}"

    /**
     * The first record of [workout] to announce, or null: on a set done with a record, completed
     * on this screen ([completedHere]) and whose [key] is not in [announced]. A record already in
     * the state when the screen opened (a resumed workout) is on a set not completed here, so it
     * only shows its badge. A first record has no `SetUi.record` (D18), and a record that stops
     * holding just leaves the set: neither says anything.
     */
    fun next(workout: WorkoutUi, completedHere: Set<String>, announced: Set<String>): RecordEvent? {
        if (completedHere.isEmpty()) return null
        for (entry in workout.exercises) {
            for (set in entry.sets) {
                val record = set.record ?: continue
                if (!set.completed || set.id !in completedHere) continue
                val key = key(set.id, set.weightKg, set.reps, record)
                if (key !in announced) return RecordEvent(key, set.id, entry.exercise.name, record, set.weightKg, set.reps)
            }
        }
        return null
    }
}

/**
 * The record reaction of the active workout (spec D17, decision 15), put once over the top of the
 * list, under the header. When [RecordAnnouncer] finds a record to announce, it is marked
 * announced ([ActiveWorkoutScreenState.announced], saved, so a scroll, a recreation or a resumed
 * workout never plays it again), the `PersonalRecord` haptic plays (a frame later, so after the
 * tap's `SetCompleted`, which the row plays: the workout rows pass `recordHaptic = false`) and the
 * [PrToast] shows "New weight record" or "New reps record" with the set and the record it beat,
 * for `timing.prToast`, read politely. While a row is in edit the toast waits, so it never covers
 * it, and shows when the edit ends. The badge is the row's ([WorkoutSetRow]).
 *
 * A record arriving while the person has just unchecked its set (the optimistic state says
 * pending) is not announced: the next echo takes the record away.
 */
@Composable
internal fun RecordReaction(workout: WorkoutUi, pounds: Boolean, screen: ActiveWorkoutScreenState, modifier: Modifier = Modifier) {
    val haptics = MTheme.haptics
    var shown by remember(workout.id) { mutableStateOf<RecordEvent?>(null) }
    val event = RecordAnnouncer.next(workout, screen.completedHere, screen.announced)
        ?.takeUnless { screen.optimistic[it.setId] == false }
    LaunchedEffect(event) {
        val found = event ?: return@LaunchedEffect
        // The row plays the check's haptic from its own effect; this one comes after it.
        withFrameNanos { }
        screen.announce(found.key)
        haptics.perform(HapticEvent.PersonalRecord)
        shown = found
    }
    val toast = shown ?: return
    PrToast(
        title = recordTitle(toast.record.kind),
        detail = recordDetail(toast, pounds),
        visible = screen.editing == null,
        onDismiss = { if (shown == toast) shown = null },
        modifier = modifier.widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth(),
    )
}

/** "New weight record" or "New reps record". */
@Composable
private fun recordTitle(kind: RecordKind): String = stringResource(
    if (kind == RecordKind.WEIGHT) Res.string.workout_record_weight_title else Res.string.workout_record_reps_title,
)

/**
 * The set and the record it beat: "87,5 kg × 6 · before 85 kg" (the history keeps only the best
 * load) or "80 kg × 8 · before 80 kg × 6" (the most reps at that load), in the chosen unit.
 */
@Composable
private fun recordDetail(event: RecordEvent, pounds: Boolean): String {
    val locale = currentNumberLocale()
    val unit = unitSymbol(pounds)
    val load: (Double) -> String = { kg -> MoosclesNumbers.weight(MoosclesNumbers.plain(WeightUnits.fromKg(kg, pounds)), unit, locale) }
    val record = event.record
    val previousKg = record.previousKg
    val previousReps = record.previousReps
    return when {
        record.kind == RecordKind.WEIGHT && previousKg != null ->
            stringResource(Res.string.workout_record_weight_detail, load(event.weightKg), event.reps, load(previousKg))
        record.kind == RecordKind.REPS && previousReps != null ->
            stringResource(Res.string.workout_record_reps_detail, load(event.weightKg), event.reps, previousReps)
        // The domain never sends a record without what it beat; should one come, the set alone.
        else -> MoosclesNumbers.setText(MoosclesNumbers.plain(WeightUnits.fromKg(event.weightKg, pounds)), unit, event.reps, locale)
    }
}
