package com.mooscles.domain

import com.mooscles.LargeHistoryFixture
import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two timing limits of [WorkoutCalculationsBatchTest], on the JVM only: they were set for the
 * JIT, which a debug Kotlin/Native build on the iOS simulator does not match.
 */
class WorkoutCalculationsBatchTimingTest {
    /** The common test class holds the flat-scan oracle and the conventions the histories use. */
    private val oracle = WorkoutCalculationsBatchTest()

    @Test fun activeWorkoutRepsPrsOnLargeHistoryAreFast() {
        val exercises = LargeHistoryFixture.variedExercises(48)
        val history = LargeHistoryFixture.appData(exercises)
        val start = history.workouts.maxOf { it.endedAt!! } + 86_400_000L
        val active = Workout("active", "Active", startedAt = start)
        val entries = exercises.mapIndexed { position, exercise ->
            WorkoutExercise("active-e$position", active.id, exercise.id, exercise.name, exercise.muscle,
                exercise.equipment, exercise.convention, position = position)
        }
        val sets = entries.flatMapIndexed { index, entry ->
            List(3) { position ->
                WorkoutSet("${entry.id}-s$position", entry.id, position = position, reps = 6 + position + index % 5,
                    weightKg = 20.0 + ((index + position) % 40) * 2.5, completed = true)
            }
        }
        val data = history.copy(workouts = history.workouts + active, workoutExercises = history.workoutExercises + entries,
            sets = history.sets + sets)

        // Checking the result first also warms the JIT, so the timings below measure the algorithm, not class loading.
        val expected = oracle.linearRepsPrsForWorkout(data, active.id)
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, WorkoutCalculations.repsPrsForWorkout(data, active.id))
        val timesMs = List(3) {
            val started = System.nanoTime()
            WorkoutCalculations.repsPrsForWorkout(data, active.id)
            (System.nanoTime() - started) / 1_000_000
        }
        println("active reps=${timesMs}ms")
        assertTrue(timesMs.min() < 30, "active reps took ${timesMs}ms")
    }

    @Test fun batchOnLargeHistoryIsFast() {
        val workouts = List(208) { index ->
            Workout("workout-$index", "Session", index * 86_400_000L, index * 86_400_000L + 3_600_000L,
                WorkoutStatus.FINISHED)
        }
        val entries = workouts.flatMap { workout ->
            List(8) { position ->
                WorkoutExercise("${workout.id}-e$position", workout.id, "movement-$position", "Movement $position",
                    MuscleGroup.CHEST, Equipment.BARBELL, oracle.conventions[position % oracle.conventions.size], position = position)
            }
        }
        val sets = workouts.flatMapIndexed { workoutIndex, workout ->
            List(61) { setIndex ->
                WorkoutSet("${workout.id}-s$setIndex", "${workout.id}-e${setIndex % 8}", position = setIndex / 8,
                    reps = 5 + (workoutIndex + setIndex) % 8, weightKg = 20.0 + ((workoutIndex * 3 + setIndex) % 40) * 2.5,
                    completed = true, warmup = setIndex % 8 == 0 && setIndex < 8)
            }
        }
        val data = AppData(workouts = workouts.reversed(), workoutExercises = entries.sortedBy { it.position },
            sets = sets.sortedBy { it.position })

        val started = System.nanoTime()
        val weight = WorkoutCalculations.prsByWorkout(data)
        val reps = WorkoutCalculations.repsPrsByWorkout(data)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        println("batch=${elapsedMs}ms")

        assertEquals(208, weight.size)
        assertEquals(208, reps.size)
        assertTrue(elapsedMs < 1500, "batch took ${elapsedMs}ms")
    }
}
