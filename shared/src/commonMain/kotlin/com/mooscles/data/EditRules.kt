package com.mooscles.data

import com.mooscles.domain.RestDuration

/** Edit validation shared by the repository and the backup import. Messages are user-visible. */
internal object EditRules {
    const val MAX_ROUTINE_SETS = 50
    const val MAX_WORKOUT_SETS = 100

    fun name(value: String): String = value.trim().also {
        dataRequire(it.isNotEmpty(), DataError.INVALID_VALUE) { "Enter a name." }
        dataRequire(it.length <= 120, DataError.INVALID_VALUE) { "Names must be 120 characters or fewer." }
    }

    fun target(reps: Int, weightKg: Double?) {
        dataRequire(reps in 0..9999, DataError.INVALID_VALUE) { "Target reps must be blank or between 1 and 9999." }
        dataRequire(weightKg == null || (weightKg.isFinite() && weightKg in 0.0..100000.0), DataError.INVALID_VALUE) { "Enter a valid non-negative target weight." }
    }

    fun plannedSets(count: Int) {
        dataRequire(count in 1..MAX_ROUTINE_SETS, DataError.INVALID_VALUE) { "Planned sets must be between 1 and 50." }
    }

    /** [count] is the number of planned sets an exercise has once the change is applied. */
    fun routineSetCount(count: Int) {
        dataRequire(count <= MAX_ROUTINE_SETS, DataError.SET_LIMIT) { "A maximum of 50 planned sets is supported per exercise." }
    }

    /** [count] is the number of sets a workout exercise has once the change is applied. */
    fun workoutSetCount(count: Int) {
        dataRequire(count <= MAX_WORKOUT_SETS, DataError.SET_LIMIT) { "A maximum of 100 sets is supported per exercise." }
    }

    fun rest(seconds: Int?) {
        dataRequire(seconds == null || seconds in 0..RestDuration.MAX_SECONDS, DataError.INVALID_VALUE) { "Rest must be between 0:00 and 5:00." }
    }

    fun workoutSet(item: WorkoutSet) {
        dataRequire(item.reps in 0..9999, DataError.INVALID_VALUE) { "Reps must be between 0 and 9999." }
        dataRequire(item.weightKg.isFinite() && item.weightKg >= 0 && item.weightKg <= 100000, DataError.INVALID_VALUE) { "Enter a valid non-negative weight." }
        dataRequire(!item.completed || item.reps > 0, DataError.INVALID_VALUE) { "Enter at least 1 rep before completing a set." }
    }
}
