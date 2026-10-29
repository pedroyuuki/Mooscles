package com.mooscles.screens.workout

import com.mooscles.data.ExerciseSearch
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals

class ExercisePickerTest {
    private fun exercise(id: String, name: String = id, muscle: String = "Chest", equipment: String = "Barbell") =
        ExerciseUi(id, name, muscle, equipment)

    private fun workout(id: String, startedAt: Long, vararg exercises: ExerciseUi) =
        WorkoutUi(id, "Workout $id", startedAt, endedAt = startedAt + 1, exercises = exercises.mapIndexed { i, e -> WorkoutExerciseUi("$id-$i", e) })

    @Test
    fun recentsAreTheLastWorkoutsExercisesUpToEightWithoutRepeats() {
        val (a, b, c, d, e) = listOf("a", "b", "c", "d", "e").map { exercise(it) }
        val (f, g, h, i, j) = listOf("f", "g", "h", "i", "j").map { exercise(it) }
        // Newest first, whatever order the list comes in; in a workout, in the workout's order.
        val history = listOf(
            workout("w1", startedAt = 100, a, b),
            workout("w3", startedAt = 300, c, a, d),
            workout("w2", startedAt = 200, e, c, f, g, h, i, j),
        )
        assertEquals(listOf("c", "a", "d", "e", "f", "g", "h", "i"), ExercisePicker.recents(history).map { it.id })
        assertEquals(listOf("c", "a"), ExercisePicker.recents(history, limit = 2).map { it.id })
        // The same exercise twice in one workout counts once.
        assertEquals(listOf("b"), ExercisePicker.recents(listOf(workout("w", 1, b, b))).map { it.id })
        assertEquals(emptyList(), ExercisePicker.recents(emptyList()))
    }

    @Test
    fun searchFoldsAccentsLikeToday() {
        val raise = exercise("custom-raise", "Elevação lateral", muscle = "Shoulders", equipment = "Dumbbell")
        val bench = exercise("catalog_barbell_bench_press", "Barbell bench press")
        val fly = exercise("custom-fly", "Crucifixo máquina", muscle = "Chest", equipment = "Selectorized machine")
        val all = listOf(raise, bench, fly)
        for (query in listOf("elevacao", "ELEVAÇÃO", "  Elevacao   LATERAL ", "lateral elevação", "shoulders")) {
            assertEquals(listOf(raise), ExercisePicker.filter(all, query), query)
        }
        assertEquals(listOf(fly), ExercisePicker.filter(all, "crucifixo maquina"))
        // Muscle and equipment are searchable, as today.
        assertEquals(listOf(bench, fly), ExercisePicker.filter(all, "chest"))
        assertEquals(emptyList(), ExercisePicker.filter(all, "squat"))
        // The same search as the library: whatever ExerciseSearch keeps, in alphabetical order.
        for (query in listOf("", "press", "dumbbell", "elev lat", "barbell chest", "zzz")) {
            val expected = all.filter { ExerciseSearch.matches(it.id, it.name, it.muscle, it.equipment, query) }.sortedBy { it.name }
            assertEquals(expected, ExercisePicker.filter(all, query), query)
        }
    }

    @Test
    fun allIsAlphabetical() {
        val names = listOf("Supino reto", "agachamento livre", "Élevation", "Barbell row", "Remada curvada", "Abdominal", "barbell curl")
        val all = names.mapIndexed { i, name -> exercise("x$i", name, muscle = "Core", equipment = "Other") }
        // Case and accents do not move a name: "agachamento" sits with "Abdominal", "Élevation" with the E's.
        assertEquals(
            listOf("Abdominal", "agachamento livre", "barbell curl", "Barbell row", "Élevation", "Remada curvada", "Supino reto"),
            ExercisePicker.filter(all, "").map { it.name },
        )
        assertEquals(listOf("barbell curl", "Barbell row"), ExercisePicker.filter(all, "barbell").map { it.name })
    }
}
