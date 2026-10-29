package com.mooscles.screens.workout

import com.mooscles.data.ExerciseSearch
import com.mooscles.data.SearchFolding
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutUi

/** What the exercise picker lists (spec "Seletor de exercício"): the recent exercises, and the search over all of them. */
object ExercisePicker {
    /** How many recent exercises the picker shows. */
    const val RECENT_LIMIT = 8

    /**
     * The exercises of the latest workouts in [history], newest workout first (by `startedAt`,
     * whatever order the list has) and in each workout's own order, each exercise once (by id), up
     * to [limit]. They are the history's snapshots: the picker shows the catalog's current entry
     * for each id.
     */
    fun recents(history: List<WorkoutUi>, limit: Int = RECENT_LIMIT): List<ExerciseUi> {
        val seen = HashSet<String>()
        val recent = ArrayList<ExerciseUi>()
        for (workout in history.sortedByDescending { it.startedAt }) {
            for (entry in workout.exercises) {
                if (recent.size >= limit) return recent
                if (seen.add(entry.exercise.id)) recent += entry.exercise
            }
        }
        return recent
    }

    /**
     * The [exercises] that match [query] with the library's search ([ExerciseSearch]: accents,
     * case and word order do not matter; muscle, equipment and aliases count), in alphabetical
     * order of their names with case and accents folded, so "agachamento" sits beside "Abdominal"
     * and "Élevation" among the E's. A blank query keeps them all.
     */
    fun filter(exercises: List<ExerciseUi>, query: String): List<ExerciseUi> =
        exercises
            .filter { ExerciseSearch.matches(it.id, it.name, it.muscle, it.equipment, query) }
            .sortedWith(compareBy<ExerciseUi> { SearchFolding.fold(it.name) }.thenBy { it.name }.thenBy { it.id })
}
