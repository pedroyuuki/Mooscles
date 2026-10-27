package com.mooscles.domain

import com.mooscles.LargeHistoryFixture
import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WorkoutCalculationsBatchTest {
    private val conventions = listOf(LoadConvention.TOTAL_LOAD, LoadConvention.BODYWEIGHT, LoadConvention.ASSISTANCE)

    /** Nominal loads are 0.25 kg multiples; noise stays within ±1 ulp so equal loads remain one cluster. */
    private fun noisyLoad(random: Random): Double {
        val nominal = random.nextInt(0, 13) * 0.25 + if (random.nextInt(4) == 0) 100.0 else 0.0
        return when (random.nextInt(4)) {
            0 -> Math.nextUp(nominal)
            1 -> Math.nextDown(nominal)
            else -> nominal
        }
    }

    private fun randomHistory(random: Random, exerciseCount: Int? = null): AppData {
        val exerciseIds = List(exerciseCount ?: random.nextInt(1, 5)) { "movement-$it" }
        val workoutCount = random.nextInt(1, 41)
        // Ids are shuffled so the id tie-breaker disagrees with creation order.
        val idNumbers = (0 until workoutCount).shuffled(random)
        val activeIndex = if (random.nextInt(3) == 0) random.nextInt(workoutCount) else -1
        val workouts = List(workoutCount) { index ->
            // Few distinct start times, so startedAt repeats often.
            val startedAt = random.nextInt(0, maxOf(2, workoutCount / 3)) * 1000L
            if (index == activeIndex) {
                Workout("w-${idNumbers[index]}", "Active", startedAt, null, WorkoutStatus.ACTIVE)
            } else {
                val endedAt = when (random.nextInt(3)) {
                    0 -> null
                    1 -> startedAt + 500
                    else -> startedAt + random.nextLong(1, 3) * 500
                }
                Workout("w-${idNumbers[index]}", "Workout $index", startedAt, endedAt, WorkoutStatus.FINISHED)
            }
        }
        val entries = mutableListOf<WorkoutExercise>()
        val sets = mutableListOf<WorkoutSet>()
        workouts.forEach { workout ->
            repeat(random.nextInt(0, 5)) { position ->
                val exerciseId = exerciseIds.random(random)
                val entry = WorkoutExercise("${workout.id}-e$position", workout.id, exerciseId,
                    "$exerciseId as named in ${workout.id}", MuscleGroup.CHEST, Equipment.BARBELL,
                    conventions.random(random), position = random.nextInt(0, 4))
                entries += entry
                repeat(random.nextInt(0, 6)) { setIndex ->
                    val roll = random.nextInt(10)
                    sets += WorkoutSet("${entry.id}-s$setIndex", entry.id, position = random.nextInt(0, 4),
                        reps = if (roll == 0) 0 else random.nextInt(1, 9),
                        weightKg = when {
                            roll == 1 -> 0.0
                            roll == 2 && random.nextInt(10) == 0 -> Double.POSITIVE_INFINITY
                            else -> noisyLoad(random)
                        },
                        completed = random.nextInt(6) != 0,
                        warmup = random.nextInt(6) == 0,
                        assistance = random.nextInt(6) == 0)
                }
            }
        }
        // Storage order is arbitrary, like Room's position-ordered rows across workouts.
        return AppData(workouts = workouts.shuffled(random), workoutExercises = entries.shuffled(random),
            sets = sets.shuffled(random))
    }

    @Test fun batchMatchesPerWorkoutOnRandomHistories() {
        val random = Random(42)
        var beatenWeight = 0
        var firstWeight = 0
        var beatenReps = 0
        var firstReps = 0
        var repeatedStarts = 0
        var activeHistories = 0
        repeat(200) { history ->
            val data = randomHistory(random)
            val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }
            val weight = WorkoutCalculations.prsByWorkout(data)
            val reps = WorkoutCalculations.repsPrsByWorkout(data)
            assertEquals("history $history shared pass", HistoryRecords(weight, reps), WorkoutCalculations.historyRecords(data))
            assertEquals("history $history weight keys", finished.map { it.id }.toSet(), weight.keys)
            assertEquals("history $history reps keys", finished.map { it.id }.toSet(), reps.keys)
            finished.forEach { workout ->
                val expectedWeight = WorkoutCalculations.prsForWorkout(data, workout.id)
                val expectedReps = WorkoutCalculations.repsPrsForWorkout(data, workout.id)
                assertEquals("history $history workout ${workout.id} weight", expectedWeight, weight[workout.id])
                assertEquals("history $history workout ${workout.id} reps", expectedReps, reps[workout.id])
                beatenWeight += expectedWeight.count { !it.firstRecord }
                firstWeight += expectedWeight.count { it.firstRecord }
                beatenReps += expectedReps.count { !it.firstRecord }
                firstReps += expectedReps.count { it.firstRecord }
            }
            repeatedStarts += finished.size - finished.map { it.startedAt }.distinct().size
            if (data.workouts.any { it.status == WorkoutStatus.ACTIVE }) activeHistories++
        }
        // The generator must exercise the interesting paths, or the equality above proves nothing.
        assertTrue("beaten weight records: $beatenWeight", beatenWeight > 50)
        assertTrue("first weight records: $firstWeight", firstWeight > 50)
        assertTrue("beaten reps records: $beatenReps", beatenReps > 50)
        assertTrue("first reps records: $firstReps", firstReps > 50)
        assertTrue("repeated startedAt: $repeatedStarts", repeatedStarts > 50)
        assertTrue("histories with an active workout: $activeHistories", activeHistories > 10)
    }

    @Test fun performanceHistoriesMatchPerExerciseHistories() {
        val random = Random(7)
        var compared = 0
        var varied = 0
        repeat(60) { history ->
            val data = randomHistory(random, exerciseCount = 40)
            val all = WorkoutCalculations.performanceHistories(data)
            val keys = data.workoutExercises.map { it.exerciseId to it.convention }.distinct()
            assertTrue("history $history keys", keys.containsAll(all.keys))
            keys.forEach { (exerciseId, convention) ->
                val expected = WorkoutCalculations.performanceHistory(data, exerciseId, convention)
                assertEquals("history $history $exerciseId $convention", expected, all[exerciseId to convention].orEmpty())
                if (expected.size > 1) compared++
            }
            if (all.keys.map { it.first }.distinct().size >= 30) varied++
        }
        assertTrue("keys with several performances: $compared", compared > 100)
        assertTrue("histories with at least 30 distinct exercises: $varied", varied >= 5)
    }

    /** The linear scan [WorkoutCalculations.repsRecords] used before its per-group slots, kept as an oracle. */
    private fun linearRepsRecords(data: AppData): List<ExerciseRepsRecord> {
        fun sameLoad(a: Double, b: Double) = kotlin.math.abs(a - b) <= maxOf(Math.ulp(a), Math.ulp(b)) * 4
        val chronology = compareBy<Workout> { it.startedAt }.thenBy { it.endedAt ?: Long.MAX_VALUE }.thenBy { it.id }
        val setsByEntry = data.sets.groupBy { it.workoutExerciseId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
        val result = mutableListOf<ExerciseRepsRecord>()
        data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology).forEach { workout ->
            val best = mutableListOf<Pair<WorkoutExercise, WorkoutSet>>()
            fun assisted(c: Pair<WorkoutExercise, WorkoutSet>) = c.second.assistance || c.first.convention == LoadConvention.ASSISTANCE
            data.workoutItems(workout.id).forEach { exercise ->
                setsByEntry[exercise.id].orEmpty().filter { WorkoutCalculations.validCompleted(it) && !it.warmup }.forEach { set ->
                    val candidate = exercise to set
                    val index = best.indexOfFirst { it.first.exerciseId == exercise.exerciseId && it.first.convention == exercise.convention &&
                        assisted(it) == assisted(candidate) && sameLoad(it.second.weightKg, set.weightKg) }
                    if (index < 0) best += candidate else if (set.reps > best[index].second.reps) best[index] = candidate
                }
            }
            best.forEach { candidate ->
                val (exercise, set) = candidate
                val index = result.indexOfFirst { it.exerciseId == exercise.exerciseId && it.convention == exercise.convention &&
                    it.assistance == assisted(candidate) && sameLoad(it.weightKg, set.weightKg) }
                if (index < 0 || set.reps > result[index].reps) {
                    val record = ExerciseRepsRecord(exercise.exerciseId, exercise.name, exercise.convention, set.weightKg,
                        assisted(candidate), set.reps, workout.id, workout.endedAt ?: workout.startedAt)
                    if (index < 0) result += record else result[index] = record
                }
            }
        }
        return result.sortedWith(compareBy<ExerciseRepsRecord> { it.exerciseName }.thenBy { it.convention.name }
            .thenBy { it.assistance }.thenBy { it.weightKg })
    }

    @Test fun repsRecordsMatchLinearScan() {
        val random = Random(99)
        var records = 0
        repeat(200) { history ->
            val data = randomHistory(random, exerciseCount = if (history % 2 == 0) null else 12)
            val expected = linearRepsRecords(data)
            assertEquals("history $history", expected, WorkoutCalculations.repsRecords(data))
            records += expected.size
        }
        assertTrue("reps records: $records", records > 500)
    }

    /**
     * The flat scan [WorkoutCalculations.repsPrsForWorkout] used before its per-group baseline, kept as an oracle:
     * one slot list for every exercise, convention, assistance mode and load, searched from the front.
     */
    private fun linearRepsPrsForWorkout(data: AppData, workoutId: String): List<RepsPr> {
        fun sameLoad(a: Double, b: Double) = kotlin.math.abs(a - b) <= maxOf(Math.ulp(a), Math.ulp(b)) * 4
        fun assisted(c: Pair<WorkoutExercise, WorkoutSet>) = c.second.assistance || c.first.convention == LoadConvention.ASSISTANCE
        fun comparable(a: Pair<WorkoutExercise, WorkoutSet>, b: Pair<WorkoutExercise, WorkoutSet>) =
            a.first.exerciseId == b.first.exerciseId && a.first.convention == b.first.convention &&
                assisted(a) == assisted(b) && sameLoad(a.second.weightKg, b.second.weightKg)
        val chronology = compareBy<Workout> { it.startedAt }.thenBy { it.endedAt ?: Long.MAX_VALUE }.thenBy { it.id }
        val setsByEntry = data.sets.groupBy { it.workoutExerciseId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
        fun best(entries: List<WorkoutExercise>): List<Pair<WorkoutExercise, WorkoutSet>> {
            val best = mutableListOf<Pair<WorkoutExercise, WorkoutSet>>()
            entries.forEach { exercise ->
                setsByEntry[exercise.id].orEmpty().filter { WorkoutCalculations.validCompleted(it) && !it.warmup }.forEach { set ->
                    val candidate = exercise to set
                    val index = best.indexOfFirst { comparable(it, candidate) }
                    if (index < 0) best += candidate else if (set.reps > best[index].second.reps) best[index] = candidate
                }
            }
            return best
        }
        val target = data.workouts.firstOrNull { it.id == workoutId } ?: return emptyList()
        val earlier = data.workouts.filter {
            it.status == WorkoutStatus.FINISHED && it.id != target.id && chronology.compare(it, target) < 0
        }.map { it.id }.toSet()
        val baseline = best(data.workoutExercises.filter { it.workoutId in earlier })
        return best(data.workoutItems(workoutId)).mapNotNull { candidate ->
            val (exercise, set) = candidate
            val previous = baseline.firstOrNull { comparable(it, candidate) }?.second?.reps
            if (previous != null && set.reps <= previous) return@mapNotNull null
            RepsPr(exercise.exerciseId, exercise.name, exercise.convention, set.weightKg, assisted(candidate),
                previous, set.reps, previous == null)
        }
    }

    @Test fun repsPrsForWorkoutMatchesFlatScan() {
        val random = Random(2027)
        var compared = 0
        var activeTargets = 0
        var beaten = 0
        var first = 0
        repeat(200) { history ->
            val data = randomHistory(random, exerciseCount = if (history % 2 == 0) null else 12)
            data.workouts.forEach { workout ->
                val expected = linearRepsPrsForWorkout(data, workout.id)
                val label = "history $history workout ${workout.id}"
                assertEquals("$label reps", expected, WorkoutCalculations.repsPrsForWorkout(data, workout.id))
                val summary = WorkoutCalculations.summary(data, workout.id, now = 0L)!!
                assertEquals("$label summary beaten", expected.filterNot { it.firstRecord }, summary.repsPrs)
                assertEquals("$label summary first", expected.filter { it.firstRecord }, summary.firstRepsRecords)
                summary.exercises.forEach { exercise ->
                    assertEquals("$label summary ${exercise.exercise.id}", expected.filter {
                        it.exerciseId == exercise.exercise.exerciseId && it.convention == exercise.exercise.convention
                    }, exercise.repsPrs)
                }
                compared++
                if (workout.status == WorkoutStatus.ACTIVE) activeTargets++
                beaten += expected.count { !it.firstRecord }
                first += expected.count { it.firstRecord }
            }
        }
        // The generator must exercise the interesting paths, or the equality above proves nothing.
        assertTrue("compared workouts: $compared", compared > 1000)
        assertTrue("active targets: $activeTargets", activeTargets > 30)
        assertTrue("beaten reps records: $beaten", beaten > 200)
        assertTrue("first reps records: $first", first > 200)
    }

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
        val expected = linearRepsPrsForWorkout(data, active.id)
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, WorkoutCalculations.repsPrsForWorkout(data, active.id))
        val timesMs = List(3) {
            val started = System.nanoTime()
            WorkoutCalculations.repsPrsForWorkout(data, active.id)
            (System.nanoTime() - started) / 1_000_000
        }
        println("active reps=${timesMs}ms")
        assertTrue("active reps took ${timesMs}ms", timesMs.min() < 30)
    }

    @Test fun batchIgnoresActiveWorkoutsAsBaseline() {
        val active = Workout("active", "Active", 100, null, WorkoutStatus.ACTIVE)
        val done = Workout("done", "Done", 200, 300, WorkoutStatus.FINISHED)
        val activeEntry = WorkoutExercise("active-bench", active.id, "bench", "Bench",
            MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        val doneEntry = activeEntry.copy(id = "done-bench", workoutId = done.id)
        val data = AppData(workouts = listOf(active, done), workoutExercises = listOf(activeEntry, doneEntry),
            sets = listOf(
                // Same load with more reps: a leak into either baseline would hide the finished first records.
                WorkoutSet("a", activeEntry.id, reps = 12, weightKg = 60.0, completed = true),
                WorkoutSet("d", doneEntry.id, reps = 5, weightKg = 60.0, completed = true)))

        val weight = WorkoutCalculations.prsByWorkout(data)
        val reps = WorkoutCalculations.repsPrsByWorkout(data)

        assertEquals(setOf("done"), weight.keys)
        assertEquals(setOf("done"), reps.keys)
        assertEquals(listOf(WeightPr("bench", "Bench", LoadConvention.TOTAL_LOAD, null, 60.0, 5, true)), weight["done"])
        assertEquals(listOf(RepsPr("bench", "Bench", LoadConvention.TOTAL_LOAD, 60.0, false, null, 5, true)), reps["done"])
        assertEquals(WorkoutCalculations.prsForWorkout(data, "done"), weight["done"])
        assertEquals(WorkoutCalculations.repsPrsForWorkout(data, "done"), reps["done"])
    }

    @Test fun batchOnLargeHistoryIsFast() {
        val workouts = List(208) { index ->
            Workout("workout-$index", "Session", index * 86_400_000L, index * 86_400_000L + 3_600_000L,
                WorkoutStatus.FINISHED)
        }
        val entries = workouts.flatMap { workout ->
            List(8) { position ->
                WorkoutExercise("${workout.id}-e$position", workout.id, "movement-$position", "Movement $position",
                    MuscleGroup.CHEST, Equipment.BARBELL, conventions[position % conventions.size], position = position)
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
        assertTrue("batch took ${elapsedMs}ms", elapsedMs < 1500)
    }
}
