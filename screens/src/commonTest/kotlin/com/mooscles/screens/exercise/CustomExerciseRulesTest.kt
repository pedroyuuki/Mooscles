package com.mooscles.screens.exercise

import com.mooscles.data.Equipment
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.LoadConvention
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The custom exercise form's rules (spec "Formulário de exercício personalizado"). */
class CustomExerciseRulesTest {
    private fun catalog(stableId: String): ExerciseUi {
        val entry = ExerciseCatalog.exercises.single { it.id == "catalog_$stableId" }
        return ExerciseUi(entry.id, entry.name, entry.muscle.label, entry.equipment.label, entry.convention.label)
    }

    private val bench = catalog("barbell_bench_flat")
    private val mine = ExerciseUi("custom-ze", "Supino articulado do Zé", "Chest", "Plate-loaded machine", custom = true)
    private val exercises = listOf(bench, mine)

    @Test
    fun defaultConventionByEquipment() {
        val expected = mapOf(
            Equipment.DUMBBELL to LoadConvention.PER_HAND,
            Equipment.BODYWEIGHT to LoadConvention.BODYWEIGHT,
        )
        for (equipment in Equipment.entries) {
            assertEquals(expected[equipment] ?: LoadConvention.TOTAL_LOAD, CustomExerciseRules.defaultConvention(equipment), "$equipment")
        }
    }

    @Test
    fun duplicateMatchesStoredEnglishAndPortugueseNames() {
        // The catalog's English name, its pt-BR name, folded (case, accents, spacing and punctuation).
        assertEquals(bench, CustomExerciseRules.duplicateOf("barbell bench press", exercises, exceptId = null))
        assertEquals(bench, CustomExerciseRules.duplicateOf("  SUPINO reto com barra ", exercises, exceptId = null))
        // A stored name the catalog no longer gives (an older build's) counts too.
        val renamed = bench.copy(name = "Flat bench (old)")
        assertEquals(renamed, CustomExerciseRules.duplicateOf("flat bench old", listOf(renamed), exceptId = null))
        // A custom one by its typed name, accents folded.
        assertEquals(mine, CustomExerciseRules.duplicateOf("supino articulado do ze", exercises, exceptId = null))
        assertNull(CustomExerciseRules.duplicateOf("supino articulado", exercises, exceptId = null))
        assertNull(CustomExerciseRules.duplicateOf("   ", exercises, exceptId = null))
    }

    @Test
    fun duplicateIgnoresTheExerciseBeingEdited() {
        assertNull(CustomExerciseRules.duplicateOf("Supino articulado do Zé", exercises, exceptId = mine.id))
        assertEquals(bench, CustomExerciseRules.duplicateOf("Supino reto com barra", exercises, exceptId = mine.id))
    }

    @Test
    fun loggedSetsCountHistoryAndTheActiveWorkout() {
        fun workout(id: String, exercise: ExerciseUi, completed: Boolean, ended: Boolean = true) = WorkoutUi(
            id, "Treino", startedAt = 1, endedAt = if (ended) 2 else null,
            exercises = listOf(WorkoutExerciseUi("$id-0", exercise, sets = listOf(SetUi("$id-s", reps = 8, weightKg = 40.0, completed = completed)))),
        )
        val done = workout("w1", mine, completed = true)
        val planned = workout("w2", mine, completed = false)
        val other = workout("w3", bench, completed = true)
        assertTrue(CustomExerciseRules.hasLoggedSets(mine.id, listOf(other, done), active = null))
        assertTrue(CustomExerciseRules.hasLoggedSets(mine.id, emptyList(), active = workout("a", mine, completed = true, ended = false)))
        // A set that was never completed was not logged; another exercise's sets do not count.
        assertFalse(CustomExerciseRules.hasLoggedSets(mine.id, listOf(planned, other), active = workout("a", mine, completed = false, ended = false)))
        assertFalse(CustomExerciseRules.hasLoggedSets(mine.id, emptyList(), active = null))
    }
}
