package com.mooscles

import com.mooscles.data.*
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.UiState
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/** Plain JVM tests: presentation caches must invalidate by saved values, never just row counts. */
class WorkoutPresentationTest {
    private val bench = Exercise(id = "bench", name = "Bench", muscle = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL, convention = LoadConvention.TOTAL_LOAD)
    private val pull = Exercise(id = "pull", name = "Pull-up", muscle = MuscleGroup.BACK,
        equipment = Equipment.BODYWEIGHT, convention = LoadConvention.BODYWEIGHT)

    private fun entry(id: String, workout: String, exercise: Exercise, position: Int) =
        WorkoutExercise(id = id, workoutId = workout, exerciseId = exercise.id, name = exercise.name,
            muscle = exercise.muscle, equipment = exercise.equipment, convention = exercise.convention,
            position = position, restSeconds = 120 + position)

    private fun fixture(): AppData = AppData(
        exercises = listOf(bench, pull),
        routines = listOf(Routine("routine-a", "First", 0), Routine("routine-b", "Second", 1)),
        routineExercises = listOf(
            RoutineExercise("plan-a", "routine-a", bench.id, position = 0),
            RoutineExercise("plan-pull", "routine-a", pull.id, position = 1),
            RoutineExercise("plan-b", "routine-a", bench.id, position = 2)
        ),
        routineSets = listOf(
            RoutineSet("target-a", "plan-a", targetReps = 6, targetWeightKg = 22.0),
            RoutineSet("target-pull", "plan-pull", targetReps = 12),
            RoutineSet("target-b", "plan-b", targetReps = 10, targetWeightKg = 17.5, inheritPr = false)
        ),
        workouts = listOf(
            Workout("active", "Active", startedAt = 3000, sourceRoutineId = "routine-a"),
            Workout("latest", "Latest", startedAt = 2000, endedAt = 2500, status = WorkoutStatus.FINISHED),
            Workout("older", "Older", startedAt = 1000, endedAt = 1500, status = WorkoutStatus.FINISHED)
        ),
        workoutExercises = listOf(
            entry("active-a", "active", bench, 0), entry("active-pull", "active", pull, 1),
            entry("active-b", "active", bench, 2), entry("latest-bench", "latest", bench, 0),
            entry("latest-pull", "latest", pull, 1), entry("older-bench", "older", bench, 0)
        ),
        sets = listOf(
            WorkoutSet("a1", "active-a", position = 0, reps = 6, weightKg = 22.0),
            WorkoutSet("a2", "active-a", position = 1),
            WorkoutSet("pull1", "active-pull", reps = 12),
            WorkoutSet("b1", "active-b", reps = 10, weightKg = 17.5),
            WorkoutSet("latest-record", "latest-bench", reps = 6, weightKg = 22.0, completed = true),
            WorkoutSet("latest-bodyweight", "latest-pull", reps = 12, completed = true),
            WorkoutSet("older-record", "older-bench", reps = 7, weightKg = 20.0, completed = true)
        )
    )

    @Test fun singleActiveEditReusesHistoryRoutinesUnaffectedCardsAndUnaffectedSetRows() {
        val presenter = WorkoutPresentation()
        val data = fixture()
        val first = presenter.present(data, PreferencesUi())
        val changed = data.copy(sets = data.sets.map { if (it.id == "a1") it.copy(reps = 8, weightKg = 22.5) else it })
        val next = presenter.present(changed, PreferencesUi())
        assertSame(first.history, next.history)
        assertSame(first.records, next.records)
        assertSame(first.routines, next.routines)
        assertSame(first.exercises, next.exercises)
        assertNotSame(first.active, next.active)
        val before = first.active!!.exercises
        val after = next.active!!.exercises
        assertNotSame(before[0], after[0])
        assertNotSame(before[0].sets[0], after[0].sets[0])
        assertSame(before[0].sets[1], after[0].sets[1])
        assertSame(before[1], after[1])
        assertSame(before[2], after[2])
        assertEquals(listOf("active-a", "active-pull", "active-b"), after.map { it.id })
        assertEquals(listOf("bench", "pull", "bench"), after.map { it.exercise.id })
        assertEquals(8, after[0].sets[0].reps)
        assertEquals(22.5, after[0].sets[0].weightKg, 0.0)
        assertEquals(10, after[2].sets[0].reps)
        assertEquals(17.5, after[2].sets[0].weightKg, 0.0)
        assertEquals(0.0, after[1].sets[0].weightKg, 0.0)
        val unchanged = presenter.present(changed.copy(), PreferencesUi())
        assertSame(next.active, unchanged.active)
    }

    @Test fun correctingAndDeletingFinishedPerformanceInvalidatesRecordsPreviousAndActivePrs() {
        val presenter = WorkoutPresentation()
        val base = fixture()
        val data = base.copy(sets = base.sets.map { if (it.id == "a1") it.copy(weightKg = 23.0, reps = 4, completed = true) else it })
        val first = presenter.present(data, PreferencesUi())
        assertEquals("22 kg × 6", first.active!!.exercises[0].previous)
        assertEquals(23.0, first.active!!.exercises[0].provisionalRecordKg!!, 0.0)
        val corrected = data.copy(sets = data.sets.map { if (it.id == "latest-record") it.copy(weightKg = 25.0, reps = 3) else it })
        val next = presenter.present(corrected, PreferencesUi())
        assertNotSame(first.history, next.history)
        assertNotSame(first.records, next.records)
        assertSame(first.routines, next.routines)
        assertEquals(25.0, next.records.single { it.kind == RecordKind.WEIGHT && it.exercise.id == "bench" }.weightKg, 0.0)
        assertEquals("25 kg × 3", next.active!!.exercises[0].previous)
        assertEquals("25 kg × 3", next.active!!.exercises[2].previous)
        assertNull(next.active!!.exercises[0].provisionalRecordKg)
        val deletedIds = corrected.workoutExercises.filter { it.workoutId == "latest" }.map { it.id }.toSet()
        val deleted = corrected.copy(workouts = corrected.workouts.filterNot { it.id == "latest" },
            workoutExercises = corrected.workoutExercises.filterNot { it.id in deletedIds },
            sets = corrected.sets.filterNot { it.workoutExerciseId in deletedIds })
        val finalUi = presenter.present(deleted, PreferencesUi())
        assertEquals(listOf("older"), finalUi.history.map { it.id })
        assertEquals(20.0, finalUi.records.single { it.kind == RecordKind.WEIGHT }.weightKg, 0.0)
        assertEquals("20 kg × 7", finalUi.active!!.exercises[0].previous)
        assertEquals(23.0, finalUi.active!!.exercises[0].provisionalRecordKg!!, 0.0)
        assertNull(finalUi.active!!.exercises[1].previous)
        assertTrue(finalUi.records.none { it.exercise.id == "pull" })
    }

    @Test fun routineReorderingAndIndependentTargetEditsInvalidateImmediately() {
        val presenter = WorkoutPresentation()
        val data = fixture()
        val first = presenter.present(data, PreferencesUi())
        val changed = data.copy(
            routines = data.routines.map { it.copy(position = 1 - it.position) },
            routineExercises = data.routineExercises.map {
                it.copy(position = when (it.id) { "plan-b" -> 0; "plan-pull" -> 1; else -> 2 })
            },
            routineSets = data.routineSets.map {
                if (it.id == "target-b") it.copy(targetWeightKg = null, targetReps = 15) else it
            }
        )
        val next = presenter.present(changed, PreferencesUi())
        assertNotSame(first.routines, next.routines)
        assertSame(first.history, next.history)
        assertSame(first.records, next.records)
        assertSame(first.active, next.active)
        assertEquals(listOf("routine-b", "routine-a"), next.routines.map { it.id })
        val entries = next.routines.last().exercises
        assertEquals(listOf("plan-b", "plan-pull", "plan-a"), entries.map { it.id })
        assertNull(entries[0].setTargets.single().weightKg)
        assertEquals(15, entries[0].setTargets.single().reps)
        assertFalse(entries[0].setTargets.single().inherited)
        assertEquals(22.0, entries[2].setTargets.single().weightKg!!, 0.0)
        assertEquals(6, entries[2].setTargets.single().reps)
    }

    @Test fun poundsChangesRefreshPreviousDisplayWithoutChangingStoredKgOrRoutineTargets() {
        val presenter = WorkoutPresentation()
        val data = fixture()
        val kg = presenter.present(data, PreferencesUi())
        val pounds = presenter.present(data, PreferencesUi(pounds = true))
        assertEquals("22 kg × 6", kg.active!!.exercises[0].previous)
        assertEquals("48.5 lb × 6", pounds.active!!.exercises[0].previous)
        assertEquals("Bodyweight × 12", pounds.active!!.exercises[1].previous)
        assertEquals(22.0, pounds.active!!.exercises[0].sets[0].weightKg, 0.0)
        assertSame(kg.routines, pounds.routines)
        val back = presenter.present(data, PreferencesUi())
        assertEquals("22 kg × 6", back.active!!.exercises[0].previous)
        assertEquals(22.0, data.sets.first { it.id == "a1" }.weightKg, 0.0)
    }

    /**
     * Best of three cold presents (each on a fresh presenter, so a loaded machine does not fail it), then a cached
     * one with equal data. The 1000 ms ceiling still fails the per-workout passes, which never went below 2114 ms.
     */
    private fun assertPresentsQuickly(data: AppData, label: String): UiState {
        lateinit var presenter: WorkoutPresentation
        lateinit var first: UiState
        val coldMs = List(3) {
            presenter = WorkoutPresentation()
            measureTimeMillis { first = presenter.present(data, PreferencesUi()) }
        }
        val firstMs = coldMs.min()
        lateinit var second: UiState
        val secondMs = measureTimeMillis { second = presenter.present(data.copy(), PreferencesUi()) }
        println("$label present=${coldMs}ms best=${firstMs}ms cached=${secondMs}ms")
        assertEquals(LargeHistoryFixture.WORKOUTS, first.history.size)
        assertTrue(first.history.any { it.records.isNotEmpty() })
        assertSame(first.history, second.history)
        assertTrue("$label best cold present took ${firstMs}ms ($coldMs)", firstMs < 1000)
        assertTrue("$label cached present took ${secondMs}ms", secondMs < 200)
        return first
    }

    @Test fun largeHistoryPresentsQuickly() {
        val data = LargeHistoryFixture.appData()
        assertEquals(LargeHistoryFixture.SETS, data.sets.size)
        assertPresentsQuickly(data, "six exercises")
    }

    @Test fun largeHistoryWithManyExercisesPresentsQuickly() {
        val data = LargeHistoryFixture.appData(LargeHistoryFixture.variedExercises(48))
        assertEquals(48, data.workoutExercises.map { it.exerciseId to it.convention }.distinct().size)
        val ui = assertPresentsQuickly(data, "48 exercises")
        assertTrue(ui.history.first().exercises.any { it.previous != null })
    }

    private val assisted = Exercise(id = "assisted-dip", name = "Assisted dip", muscle = MuscleGroup.TRICEPS,
        equipment = Equipment.SELECTORIZED_MACHINE, convention = LoadConvention.ASSISTANCE)

    /** 30 workouts (one active) with repeated loads, assistance and warm-ups, so every record path appears. */
    private fun generatedHistory(random: Random): AppData {
        val movements = listOf(bench, pull, assisted)
        val workouts = List(30) { index ->
            val start = 10_000L + index * 1_000L
            if (index == 17) Workout("w-$index", "Active", startedAt = start)
            else Workout("w-$index", "Session $index", startedAt = start, endedAt = start + 500, status = WorkoutStatus.FINISHED)
        }
        val entries = mutableListOf<WorkoutExercise>()
        val sets = mutableListOf<WorkoutSet>()
        workouts.forEach { workout ->
            repeat(random.nextInt(1, 4)) { position ->
                val e = entry("${workout.id}-e$position", workout.id, movements.random(random), position)
                entries += e
                repeat(random.nextInt(1, 5)) { index ->
                    sets += WorkoutSet("${e.id}-s$index", e.id, position = index, reps = random.nextInt(1, 10),
                        weightKg = random.nextInt(0, 9) * 2.5, completed = random.nextInt(5) != 0,
                        warmup = random.nextInt(6) == 0, assistance = random.nextInt(8) == 0)
                }
            }
        }
        return AppData(exercises = movements, workouts = workouts.shuffled(random),
            workoutExercises = entries.shuffled(random), sets = sets.shuffled(random))
    }

    private data class RecordFacts(val exerciseId: String, val convention: String, val kind: RecordKind, val weightKg: Double,
        val reps: Int, val previousKg: Double?, val previousReps: Int?, val assistance: Boolean, val workoutId: String, val date: Long)

    private fun RecordUi.facts() = RecordFacts(exercise.id, exercise.convention, kind, weightKg, reps, previousKg, previousReps,
        assistance, workoutId, date)

    @Test fun historyRecordsUnchangedByBatch() {
        val random = Random(2026)
        var beaten = 0
        var first = 0
        var reps = 0
        repeat(20) { round ->
            val data = generatedHistory(random)
            val ui = WorkoutPresentation().present(data, PreferencesUi())
            val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }
            assertEquals(finished.sortedByDescending { it.startedAt }.map { it.id }, ui.history.map { it.id })
            ui.history.forEach { workout ->
                val w = finished.single { it.id == workout.id }
                val date = w.endedAt ?: w.startedAt
                val weightPrs = WorkoutCalculations.prsForWorkout(data, w.id)
                val repsPrs = WorkoutCalculations.repsPrsForWorkout(data, w.id)
                val expected = weightPrs.map {
                    RecordFacts(it.exerciseId, it.convention.label, RecordKind.WEIGHT, it.newWeightKg, it.reps,
                        it.previousWeightKg, null, false, w.id, date)
                } + repsPrs.map {
                    RecordFacts(it.exerciseId, it.convention.label, RecordKind.REPS, it.weightKg, it.newReps,
                        null, it.previousReps, it.assistance, w.id, date)
                }
                assertEquals("round $round workout ${w.id}", expected, workout.records.map { it.facts() })
                workout.exercises.forEach { entry ->
                    val pr = weightPrs.firstOrNull { it.exerciseId == entry.exercise.id && it.convention.label == entry.exercise.convention }
                    assertEquals("round $round entry ${entry.id}", pr?.newWeightKg, entry.provisionalRecordKg)
                    assertEquals("round $round entry ${entry.id}", pr?.firstRecord ?: false, entry.firstRecord)
                    val own = workout.records.filter { it.exercise.id == entry.exercise.id && it.exercise.convention == entry.exercise.convention }
                    assertTrue("round $round entry ${entry.id}", own.containsAll(entry.records))
                }
                beaten += workout.records.count { !it.firstRecord }
                first += workout.records.count { it.firstRecord }
                reps += workout.records.count { it.kind == RecordKind.REPS }
            }
        }
        assertTrue("beaten records: $beaten", beaten > 20)
        assertTrue("first records: $first", first > 20)
        assertTrue("reps records: $reps", reps > 20)
    }

    @Test fun activeWorkoutRecordsStillComputedAgainstHistory() {
        val data = AppData(
            exercises = listOf(bench),
            workouts = listOf(
                Workout("older", "Older", startedAt = 1000, endedAt = 1500, status = WorkoutStatus.FINISHED),
                Workout("latest", "Latest", startedAt = 2000, endedAt = 2500, status = WorkoutStatus.FINISHED),
                Workout("active", "Active", startedAt = 3000)
            ),
            workoutExercises = listOf(entry("older-bench", "older", bench, 0), entry("latest-bench", "latest", bench, 0),
                entry("active-bench", "active", bench, 0)),
            sets = listOf(
                WorkoutSet("o1", "older-bench", reps = 5, weightKg = 100.0, completed = true),
                WorkoutSet("l1", "latest-bench", reps = 5, weightKg = 110.0, completed = true),
                WorkoutSet("a1", "active-bench", reps = 3, weightKg = 120.0, completed = true)
            )
        )
        val ui = WorkoutPresentation().present(data, PreferencesUi())
        val active = ui.active!!
        val record = active.records.single { it.kind == RecordKind.WEIGHT }
        assertEquals(120.0, record.weightKg, 0.0)
        assertEquals(3, record.reps)
        assertEquals(110.0, record.previousKg!!, 0.0)
        assertFalse(record.firstRecord)
        assertEquals("active", record.workoutId)
        assertEquals(120.0, active.exercises.single().provisionalRecordKg!!, 0.0)
        assertFalse(active.exercises.single().firstRecord)
        assertEquals(listOf(record), active.exercises.single().records.filter { it.kind == RecordKind.WEIGHT })
        // The active session must not leak into the history's baseline.
        val latest = ui.history.single { it.id == "latest" }.records.single { it.kind == RecordKind.WEIGHT }
        assertEquals(100.0, latest.previousKg!!, 0.0)
        assertEquals(110.0, ui.records.single { it.kind == RecordKind.WEIGHT }.weightKg, 0.0)
    }
}
