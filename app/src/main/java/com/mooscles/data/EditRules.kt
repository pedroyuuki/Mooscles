package com.mooscles.data

import com.mooscles.domain.RestDuration

/** Edit validation shared by the repository and the backup import. Messages are user-visible. */
internal object EditRules {
    const val MAX_ROUTINE_SETS = 50
    const val MAX_WORKOUT_SETS = 100

    fun name(value: String): String = value.trim().also {
        require(it.isNotEmpty()) { "Enter a name." }
        require(it.length <= 120) { "Names must be 120 characters or fewer." }
    }

    fun target(reps: Int, weightKg: Double?) {
        require(reps in 0..9999) { "Target reps must be blank or between 1 and 9999." }
        require(weightKg == null || (weightKg.isFinite() && weightKg in 0.0..100000.0)) { "Enter a valid non-negative target weight." }
    }

    fun plannedSets(count: Int) {
        require(count in 1..MAX_ROUTINE_SETS) { "Planned sets must be between 1 and 50." }
    }

    fun rest(seconds: Int?) {
        require(seconds == null || seconds in 0..RestDuration.MAX_SECONDS) { "Rest must be between 0:00 and 5:00." }
    }

    fun workoutSet(item: WorkoutSet) {
        require(item.reps in 0..9999) { "Reps must be between 0 and 9999." }
        require(item.weightKg.isFinite() && item.weightKg >= 0 && item.weightKg <= 100000) { "Enter a valid non-negative weight." }
        require(!item.completed || item.reps > 0) { "Enter at least 1 rep before completing a set." }
    }
}
