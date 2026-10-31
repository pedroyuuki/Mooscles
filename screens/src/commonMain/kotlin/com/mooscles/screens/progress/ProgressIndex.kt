package com.mooscles.screens.progress

import androidx.compose.runtime.Immutable
import com.mooscles.data.ExerciseSearchIndex
import com.mooscles.data.LoadConvention
import com.mooscles.data.SearchEntry
import com.mooscles.domain.EstimatedOneRepMax
import com.mooscles.screens.format.BestSets
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutUi

/** One chart series: an exercise in one convention (its label, as `ExerciseUi.convention` carries it), the records' key. */
data class SeriesKey(val exerciseId: String, val convention: String)

/** A metric's value in a session, in kg (or reps), and the set it comes from. */
@Immutable
data class SetValue(val value: Double, val weightKg: Double, val reps: Int)

/**
 * One finished workout of one [SeriesKey] (two entries of the exercise in the workout are one session).
 *
 * [load] is the convention's load measure (spec D6, plan decision 6): the heaviest working set (ties by the most
 * reps, then the first in workout order) for load and added weight, and for Assistance the lowest assistance above 0
 * among the assisted repetition sets (ties the same way); null without such a set. [estimate] is the best Epley
 * estimate of those working sets with up to 10 reps (D5), null on Added weight and Assistance. [reps] is the most
 * reps in one repetition set of the convention's group (unassisted, or assisted on Assistance), the first on a tie.
 * [volumeKg] is the domain's volume, warm-ups included. [weightRecord] and [repsRecord] say whether a beaten record
 * (never a first record) of this session is in the record lists. [sets] are the completed sets of the session's
 * entries, in workout order.
 */
@Immutable
class SessionPoint(
    val workoutId: String,
    val workoutName: String,
    val startedAt: Long,
    val load: SetValue?,
    val estimate: SetValue?,
    val reps: SetValue?,
    val volumeKg: Double,
    val weightRecord: Boolean,
    val repsRecord: Boolean,
    val sets: List<SetUi>,
)

/**
 * One record of the timeline (spec D11). [order] is the position of the exercise's first entry in its workout (in
 * that convention); [current] is plan decision 18: `UiState.records` holds the same record.
 */
@Immutable
class RecordEvent(val record: RecordUi, val workoutName: String, val order: Int, val current: Boolean)

/**
 * What the 5A screens read about progress, built once per history (plan decision 3), all in kg: the sessions of each
 * exercise and convention, the record events and the search over the exercises that have records.
 */
@Immutable
class ProgressIndex private constructor(
    private val series: Map<SeriesKey, List<SessionPoint>>,
    private val conventions: Map<String, List<String>>,
    private val snapshots: Map<String, ExerciseUi>,
    /** Every `WorkoutUi.records` of the history, first records included: newest first, workout order, weight before reps, heaviest first. */
    val events: List<RecordEvent>,
    /** The records of `UiState.records`, in the order of [events], as their history events (see [build]). */
    val currentRecords: List<RecordEvent>,
    private val search: ExerciseSearchIndex,
) {
    /** The sessions of [key], oldest first; empty for a series without one. */
    fun sessions(key: SeriesKey): List<SessionPoint> = series[key].orEmpty()

    /** The conventions [exerciseId] has sessions in, the one of its latest session first. */
    fun conventionsOf(exerciseId: String): List<String> = conventions[exerciseId].orEmpty()

    /** The exercise as its latest session saved it, in any convention: what a deleted exercise still shows. */
    fun snapshot(exerciseId: String): ExerciseUi? = snapshots[exerciseId]

    /** The ids of the exercises with a record (an event or a current record) that match [query], in both languages. */
    fun searchRecords(query: String): Set<String> = search.matching(query).toSet()

    companion object {
        /**
         * Builds the index from `UiState.history` ([history], finished workouts) and `UiState.records` ([records]).
         *
         * `UiState.records` entries carry no previous value, so their own `firstRecord` is always true. A current
         * record is therefore shown as its history event, the `WorkoutUi.records` entry of the same workout with the
         * same exercise, convention, kind, assistance and load, which has the previous value; the record itself is
         * the fallback only when its workout has no such event. A current record whose workout is not in [history]
         * (the two lists out of step) is dropped, like an event of a deleted workout, and is not searchable.
         */
        fun build(history: List<WorkoutUi>, records: List<RecordUi>): ProgressIndex {
            val byId = history.associateBy(WorkoutUi::id)
            val rank = HashMap<String, Int>(history.size).apply { history.forEachIndexed { i, w -> put(w.id, i) } }
            val eventOrder = compareByDescending<RecordEvent> { it.record.date }
                .thenBy { rank[it.record.workoutId] ?: Int.MAX_VALUE } // keeps two workouts of the same instant apart
                .thenBy { it.order }
                .thenBy { if (it.record.kind == RecordKind.WEIGHT) 0 else 1 }
                .thenByDescending { it.record.weightKg }

            val currentByWorkout = records.groupBy(RecordUi::workoutId)
            val events = history.flatMap { workout ->
                val current = currentByWorkout[workout.id].orEmpty()
                workout.records.map { record ->
                    RecordEvent(record, workout.name, orderOf(workout, record), current.any { sameRecord(it, record) })
                }
            }.sortedWith(eventOrder)
            val eventsByWorkout = events.groupBy { it.record.workoutId }
            val currentRecords = records.mapNotNull { record ->
                val workout = byId[record.workoutId] ?: return@mapNotNull null
                eventsByWorkout[workout.id]?.firstOrNull { sameRecord(record, it.record) }
                    ?: RecordEvent(record, workout.name, orderOf(workout, record), current = true)
            }.distinct().sortedWith(eventOrder)

            val beaten = (events.asSequence() + currentRecords.asSequence()).map(RecordEvent::record)
                .filterNot(RecordUi::firstRecord).map(::markOf).toHashSet()
            val series = Series(history, beaten)
            return ProgressIndex(series.points, series.conventions(), series.snapshots, events, currentRecords,
                searchIndex(events, currentRecords))
        }

        /** D18: the same exercise, convention, kind, assistance and workout, and the same load. */
        private fun sameRecord(current: RecordUi, event: RecordUi): Boolean =
            current.workoutId == event.workoutId && current.kind == event.kind &&
                current.exercise.id == event.exercise.id && current.exercise.convention == event.exercise.convention &&
                current.assistance == event.assistance && BestSets.sameLoad(current.weightKg, event.weightKg)

        private fun orderOf(workout: WorkoutUi, record: RecordUi): Int = workout.exercises
            .indexOfFirst { it.exercise.id == record.exercise.id && it.exercise.convention == record.exercise.convention }
            .let { if (it < 0) workout.exercises.size else it }

        /** A session mark: a weight record marks its series' session; a reps record only its own assistance group. */
        private data class Mark(val kind: RecordKind, val key: SeriesKey, val workoutId: String, val assistance: Boolean)

        private fun markOf(record: RecordUi) = Mark(record.kind, SeriesKey(record.exercise.id, record.exercise.convention),
            record.workoutId, record.kind == RecordKind.REPS && record.assistance)

        /**
         * The sessions of every series, oldest first, with what the latest session of each exercise saved. Workouts
         * of the same start keep the history's order, the first one counting as the newer.
         */
        private class Series(history: List<WorkoutUi>, beaten: Set<Mark>) {
            val points = LinkedHashMap<SeriesKey, MutableList<SessionPoint>>()
            val snapshots = HashMap<String, ExerciseUi>()
            private val latest = HashMap<SeriesKey, Int>()

            init {
                var tick = 0
                history.asReversed().sortedBy(WorkoutUi::startedAt).forEach { workout ->
                    workout.exercises.groupBy { SeriesKey(it.exercise.id, it.exercise.convention) }.forEach { (key, entries) ->
                        val point = session(workout, key, entries.flatMap { it.sets }, beaten) ?: return@forEach
                        points.getOrPut(key) { mutableListOf() } += point
                        latest[key] = tick++
                        snapshots[key.exerciseId] = entries.first().exercise
                    }
                }
            }

            /** Each exercise's conventions with a session, the one of its latest session first. */
            fun conventions(): Map<String, List<String>> = latest.entries.sortedByDescending { it.value }
                .groupBy({ it.key.exerciseId }, { it.key.convention })
        }

        /** Null when the session has no valid set, as the domain's performance history leaves it out. */
        private fun session(workout: WorkoutUi, key: SeriesKey, sets: List<SetUi>, beaten: Set<Mark>): SessionPoint? {
            if (sets.none(WorkingSets::valid)) return null
            val convention = WorkingSets.convention(key.convention)
            val assistanceGroup = convention == LoadConvention.ASSISTANCE
            val loadSets = sets.filter { WorkingSets.forLoad(it, convention) }
            val repSets = sets.filter { WorkingSets.forReps(it, assistanceGroup, convention) }
            val load = if (assistanceGroup) lowestAssistance(repSets) else BestSets.best(loadSets, SetUi::weightKg, SetUi::reps)
            val estimate = if (convention == LoadConvention.BODYWEIGHT) null else loadSets
                .mapNotNull { set -> EstimatedOneRepMax.estimate(set.weightKg, set.reps)?.let { SetValue(it, set.weightKg, set.reps) } }
                .maxByOrNull(SetValue::value)
            val reps = repSets.maxByOrNull(SetUi::reps)
            return SessionPoint(
                workoutId = workout.id,
                workoutName = workout.name,
                startedAt = workout.startedAt,
                load = load?.let { SetValue(it.weightKg, it.weightKg, it.reps) },
                estimate = estimate,
                reps = reps?.let { SetValue(it.reps.toDouble(), it.weightKg, it.reps) },
                volumeKg = sets.sumOf { WorkingSets.volumeKg(it, convention) },
                weightRecord = Mark(RecordKind.WEIGHT, key, workout.id, false) in beaten,
                repsRecord = Mark(RecordKind.REPS, key, workout.id, assistanceGroup) in beaten,
                sets = sets.filter(SetUi::completed),
            )
        }

        /** Decision 6: the lowest assistance above 0 (0 is no help); equal loads by the most reps, then the first. */
        private fun lowestAssistance(sets: List<SetUi>): SetUi? {
            val helped = sets.filter { it.weightKg > 0.0 }
            val lowest = helped.minOfOrNull(SetUi::weightKg) ?: return null
            return helped.filter { BestSets.sameLoad(it.weightKg, lowest) }.maxByOrNull(SetUi::reps)
        }

        /** One entry per exercise with a record, named as its latest record has it (a current one first). */
        private fun searchIndex(events: List<RecordEvent>, current: List<RecordEvent>): ExerciseSearchIndex {
            val exercises = LinkedHashMap<String, ExerciseUi>()
            (current + events).forEach { exercises.getOrPut(it.record.exercise.id) { it.record.exercise } }
            return ExerciseSearchIndex.build(exercises.values.map { SearchEntry(it.id, it.name, it.muscle, it.equipment) })
        }
    }
}
