package com.mooscles.screens.exercise

import com.mooscles.data.Equipment
import com.mooscles.data.ExerciseNames
import com.mooscles.data.LoadConvention
import com.mooscles.data.SearchFolding
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutUi

/** The rules of the custom exercise form (spec "Formulário de exercício personalizado"), pure for `commonTest`. */
internal object CustomExerciseRules {
    /** The convention an exercise of [equipment] starts with: per hand for a dumbbell, added weight for bodyweight, else the total load. */
    fun defaultConvention(equipment: Equipment): LoadConvention = when (equipment) {
        Equipment.DUMBBELL -> LoadConvention.PER_HAND
        Equipment.BODYWEIGHT -> LoadConvention.BODYWEIGHT
        else -> LoadConvention.TOTAL_LOAD
    }

    /**
     * The first of [exercises] (other than [exceptId], the one being edited) that already has the
     * name [name], compared folded ([SearchFolding]: case, accents and punctuation aside): its
     * stored name, its English catalog name or its Brazilian Portuguese one. Null when none has it,
     * and for a blank name.
     */
    fun duplicateOf(name: String, exercises: List<ExerciseUi>, exceptId: String?): ExerciseUi? {
        val folded = SearchFolding.fold(name)
        if (folded.isEmpty()) return null
        return exercises.firstOrNull { exercise ->
            exercise.id != exceptId && namesOf(exercise).any { SearchFolding.fold(it) == folded }
        }
    }

    private fun namesOf(exercise: ExerciseUi): List<String> = listOf(
        exercise.name,
        ExerciseNames.display(exercise.id, exercise.name, exercise.custom, portuguese = false),
        ExerciseNames.display(exercise.id, exercise.name, exercise.custom, portuguese = true),
    )

    /** True when a completed set of [exerciseId] is in [history] or in the [active] workout. */
    fun hasLoggedSets(exerciseId: String, history: List<WorkoutUi>, active: WorkoutUi?): Boolean =
        (history + listOfNotNull(active)).any { workout ->
            workout.exercises.any { entry -> entry.exercise.id == exerciseId && entry.sets.any { it.completed } }
        }
}
