package com.mooscles.data

import androidx.room.useReaderConnection
import com.mooscles.TemporaryDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iOS entry point on the simulator: BundledSQLiteDriver, the migrations and the shared transactions. */
class IosDatabaseTest {
    private val directory = TemporaryDirectory()
    private val opened = mutableListOf<WorkoutDatabase>()

    private fun open(): WorkoutDatabase = openWorkoutDatabase(directory.path).also { opened += it }

    @AfterTest fun tearDown() {
        opened.forEach { it.close() }
        directory.delete()
    }

    @Test fun freshDatabaseHasTheDefaultFolderAndVersionFive() = runBlocking<Unit> {
        val database = open()
        val repository = WorkoutRepository(database)
        repository.initialize()

        val data = repository.data.first()
        val folder = data.routineFolders.single { it.id == RoutineFolder.DEFAULT_ID }
        assertEquals("My routines", folder.name)
        assertEquals(ExerciseCatalog.exercises.size, data.exercises.size)
        assertEquals(5L, database.useReaderConnection { connection ->
            connection.usePrepared("PRAGMA user_version") { check(it.step()); it.getLong(0) }
        })
        assertTrue(directory.exists(WorkoutDatabase.FILE_NAME))
    }

    @Test fun workoutRoundTripsAfterReopening() = runBlocking<Unit> {
        val database = open()
        val repository = WorkoutRepository(database)
        repository.initialize()
        val routine = repository.createRoutine("Push")
        repository.addRoutineExercise(routine, ExerciseCatalog.exercises.first().id)
        val workout = repository.startRoutine(routine, now = 1_000_000L)
        val first = repository.workoutSets(workout).minBy { it.position }
        repository.updateSet(first.copy(reps = 8, weightKg = 60.0, completed = true))
        assertTrue(repository.finishWorkout(workout, now = 1_600_000L))
        val before = repository.data.first()
        database.close()

        val after = WorkoutRepository(open()).data.first()
        assertEquals(before.workouts, after.workouts)
        assertEquals(before.workoutExercises, after.workoutExercises)
        assertEquals(before.sets, after.sets)
        assertEquals(before.routines, after.routines)
        assertEquals(before.routineExercises, after.routineExercises)
        assertEquals(before.routineSets, after.routineSets)
        val finished = after.workouts.single()
        assertEquals(WorkoutStatus.FINISHED, finished.status)
        assertEquals(1_600_000L, finished.endedAt)
        assertEquals(routine, finished.sourceRoutineId)
        val completed = after.sets.single { it.completed }
        assertEquals(8, completed.reps)
        assertEquals(60.0, completed.weightKg)
    }

    @Test fun failedNestedTransactionRollsBackTheOuterOne() = runBlocking<Unit> {
        val database = open()
        val repository = WorkoutRepository(database)
        repository.initialize()
        val before = repository.snapshot()

        val error = runCatching {
            database.writeTransaction {
                repository.createRoutine("A")
                val nested = runCatching {
                    database.writeTransaction {
                        repository.createRoutine("B")
                        error("Nested failure")
                    }
                }.exceptionOrNull()
                assertEquals("Nested failure", nested?.message)
                // The caller swallows the nested failure and returns normally.
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException, "$error")
        val causes = generateSequence<Throwable>(error) { it.cause }.map { it.message }.toList()
        assertTrue("A nested write transaction failed, so the enclosing one is rolled back" in causes, "$causes")
        assertTrue("Nested failure" in causes, "$causes")
        assertEquals(before, repository.snapshot())
        assertTrue(repository.snapshot().routines.none { it.name == "A" || it.name == "B" })
    }

    @Test fun nestedRepositoryCommandsJoinTheOuterTransactionAndFailAllOrNothing() = runBlocking<Unit> {
        val database = open()
        val repository = WorkoutRepository(database)
        repository.initialize()
        val before = repository.snapshot()

        val error = runCatching {
            database.writeTransaction {
                val routine = repository.createRoutine("Inside the outer transaction")
                // A nested command reads the outer, uncommitted write on the same connection.
                assertTrue(repository.snapshot().routines.any { it.id == routine })
                repository.createRoutine("Rejected", folderId = "missing-folder")
            }
        }.exceptionOrNull()

        assertEquals("Folder not found.", error?.message)
        assertEquals(before, repository.snapshot())
    }
}
