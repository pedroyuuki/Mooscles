package com.mooscles.screens.workout

import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi

/** A set still to do, as the expanded timer names it: its number in its exercise, its load (exact kg) and reps. */
internal data class UpNext(val setNumber: Int, val weightKg: Double, val reps: Int)

/**
 * What comes after a rest (spec "Expandido", "A seguir: série 5 · 85 × 6"): the first pending set
 * of the timer's exercise ([of]'s `timerEntryId`, `TimerUi.workoutExerciseId`); when that exercise
 * has none, left the workout or the rest has no exercise (started from the rest sheet), the first
 * pending set of the workout, in its order; with nothing pending, nothing.
 */
internal object RestUpNext {
    fun of(workout: WorkoutUi, timerEntryId: String?): UpNext? {
        val own = timerEntryId?.let { id -> workout.exercises.firstOrNull { it.id == id } }?.let(::firstPending)
        return own ?: workout.exercises.firstNotNullOfOrNull(::firstPending)
    }

    private fun firstPending(entry: WorkoutExerciseUi): UpNext? {
        val index = entry.sets.indexOfFirst { !it.completed }
        if (index < 0) return null
        val set = entry.sets[index]
        return UpNext(index + 1, set.weightKg, set.reps)
    }
}
