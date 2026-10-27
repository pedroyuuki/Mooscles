package com.mooscles.backup

/** User-facing backup copy. Singular only for exactly one; zero stays plural. */
object BackupTexts {
    fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

    fun counts(workouts: Int, routines: Int, customExercises: Int) =
        "${count(workouts, "workout")}, ${count(routines, "routine")} and ${count(customExercises, "custom exercise")}"

    fun exported(workouts: Int, routines: Int, customExercises: Int) = "Exported ${counts(workouts, routines, customExercises)}"

    fun imported(summary: ImportSummary) =
        "Imported ${counts(summary.workoutsAdded, summary.routinesAdded, summary.customExercisesAdded)}"
}
