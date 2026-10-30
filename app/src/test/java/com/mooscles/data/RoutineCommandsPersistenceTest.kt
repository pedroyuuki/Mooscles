package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The routine and folder commands of the phase 4B screens, on the real generated Room code: ids chosen by
 * the screen, folder deletion and ordering, planned warm-ups (D14) and undoing a removed planned set.
 * Every refusal writes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineCommandsPersistenceTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var clock = 1_000L

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        repo = WorkoutRepository(database)
        repo.initialize()
    }

    @After fun close() { database.close() }

    private suspend fun exercise(name: String = "Movement ${newId()}"): String =
        repo.createCustomExercise(name, MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)

    /** A finished workout whose only completed set makes [weightKg] × [reps] the exercise's record. */
    private suspend fun record(exerciseId: String, weightKg: Double, reps: Int) {
        val workout = repo.startEmpty(now = clock.also { clock += 1_000 })
        val entry = repo.addWorkoutExercise(workout, exerciseId)
        val set = repo.snapshot().exerciseSets(entry).first()
        repo.updateSet(set.copy(weightKg = weightKg, reps = reps, completed = true))
        assertTrue(repo.finishWorkout(workout, now = clock))
    }

    private suspend fun rows(entryId: String): List<RoutineSet> = repo.snapshot().plannedSets(entryId)

    private suspend fun routines(folderId: String): List<String> =
        repo.snapshot().routines.filter { it.folderId == folderId }.sortedBy { it.position }.map { it.id }

    /** A plan whose sets are A (a 40 kg × 10 warm-up), B (100 kg × 5) and C (inherited, no record: empty). */
    private suspend fun threeSets(): Pair<String, List<RoutineSet>> {
        val routine = repo.createRoutine("Push")
        val entry = repo.addRoutineExercise(routine, exercise())
        repo.addRoutineSet(entry)
        val (a, b) = rows(entry)
        repo.updateRoutineSet(a.id, 10, 40.0)
        repo.setRoutineSetWarmup(a.id, true)
        repo.updateRoutineSet(b.id, 5, 100.0)
        return entry to rows(entry)
    }

    private suspend fun assertRefused(error: DataError, call: suspend () -> Unit) {
        val before = repo.snapshot()
        val failure = try {
            call()
            null
        } catch (e: DataRuleException) {
            e
        }
        assertNotNull("Expected the command to be refused with $error", failure)
        assertEquals(error, failure!!.error)
        assertEquals("A refused command must write nothing", before, repo.snapshot())
    }

    @Test fun createRoutineUsesTheGivenIdAndFolder() = runBlocking {
        repo.createRoutine("Existing")
        val folder = repo.createRoutineFolder("Hypertrophy")

        assertEquals("screen-chosen", repo.createRoutine("  Push day ", folder, "screen-chosen"))

        val routine = repo.snapshot().routines.single { it.id == "screen-chosen" }
        assertEquals("Push day", routine.name)
        assertEquals(folder, routine.folderId)
        assertEquals(1, routine.position)
        assertTrue(repo.snapshot().routineItems(routine.id).isEmpty())
    }

    @Test fun createRoutineRefusesAnExistingId() = runBlocking {
        val existing = repo.createRoutine("Push")
        assertRefused(DataError.INVALID_VALUE) { repo.createRoutine("Pull", id = existing) }
        assertRefused(DataError.INVALID_VALUE) { repo.createRoutine("Pull", id = "  ") }
        assertRefused(DataError.INVALID_VALUE) { repo.createRoutine("Pull", id = "") }
        assertEquals("Push", repo.snapshot().routines.single().name)
    }

    @Test fun duplicateWithANameUsesItAndTheGivenId() = runBlocking {
        val folder = repo.createRoutineFolder("Hypertrophy")
        val original = repo.createRoutine("Treino A", folder)
        repo.addRoutineExercise(original, exercise())
        repo.createRoutine("Other")

        assertEquals("copy-id", repo.duplicateRoutine(original, " Treino A (cópia) ", "copy-id"))

        val copy = repo.snapshot().routines.single { it.id == "copy-id" }
        assertEquals("Treino A (cópia)", copy.name)
        assertEquals(folder, copy.folderId)
        assertEquals(2, copy.position)
        assertEquals(1, repo.snapshot().routineItems(copy.id).size)
        // The id the screen chose must be new, and the name follows the usual rule.
        assertRefused(DataError.INVALID_VALUE) { repo.duplicateRoutine(original, "Again", "copy-id") }
        assertRefused(DataError.INVALID_VALUE) { repo.duplicateRoutine(original, "Again", " ") }
        assertRefused(DataError.INVALID_VALUE) { repo.duplicateRoutine(original, "   ", "another-id") }
        assertRefused(DataError.INVALID_VALUE) { repo.duplicateRoutine(original, "x".repeat(121), "another-id") }
        // Without a name the copy keeps today's English suffix.
        val legacy = repo.duplicateRoutine(original)
        assertEquals("Treino A (copy)", repo.snapshot().routines.single { it.id == legacy }.name)
    }

    @Test fun duplicateCopiesWarmups() = runBlocking {
        val (entry, original) = threeSets()
        val routine = repo.routineExercise(entry)!!.routineId

        val copy = repo.duplicateRoutine(routine, "Copy", "copy-id")

        val copiedEntry = repo.snapshot().routineItems(copy).single()
        val copied = rows(copiedEntry.id)
        assertEquals(original.map { it.copy(id = "", routineExerciseId = "") }, copied.map { it.copy(id = "", routineExerciseId = "") })
        assertEquals(listOf(true, false, false), copied.map { it.warmup })
        assertEquals(listOf(false, false, true), copied.map { it.inheritPr })
        assertTrue(copied.none { row -> row.id in original.map { it.id } })
    }

    @Test fun addRoutineExercisesKeepsTheOrderInOneTransaction() = runBlocking {
        val routine = repo.createRoutine("Push")
        val first = repo.addRoutineExercise(routine, exercise("First"))
        val (b, a, c) = listOf(exercise("B"), exercise("A"), exercise("C"))
        record(a, 60.0, 8)

        val added = repo.addRoutineExercises(routine, listOf(b, a, c))

        val items = repo.snapshot().routineItems(routine)
        assertEquals(listOf(first) + added, items.map { it.id })
        assertEquals(listOf(b, a, c), items.drop(1).map { it.exerciseId })
        assertEquals(listOf(0, 1, 2, 3), items.map { it.position })
        // Each one exactly as addRoutineExercise adds it: two inherited working sets and the default rest.
        items.drop(1).forEach { item ->
            assertEquals(2, item.plannedSets)
            assertEquals(com.mooscles.domain.RestDuration.DEFAULT_SECONDS, item.restSeconds)
            assertTrue(rows(item.id).all { it.inheritPr && !it.warmup })
        }
        assertEquals(listOf(8 to 60.0, 8 to 60.0), rows(added[1]).map { it.targetReps to it.targetWeightKg })
        assertEquals(60.0, items[2].targetWeightKg!!, 0.0)
        assertTrue(repo.addRoutineExercises(routine, emptyList()).isEmpty())
    }

    @Test fun aMissingExerciseAddsNone() = runBlocking {
        val routine = repo.createRoutine("Push")
        val (a, b) = listOf(exercise(), exercise())
        assertRefused(DataError.EXERCISE_NOT_FOUND) { repo.addRoutineExercises(routine, listOf(a, "missing", b)) }
        assertRefused(DataError.EXERCISE_NOT_FOUND) { repo.addRoutineExercises(routine, listOf(a, b, "missing")) }
        assertRefused(DataError.ROUTINE_NOT_FOUND) { repo.addRoutineExercises("missing", listOf(a)) }
        assertTrue(repo.snapshot().routineItems(routine).isEmpty())
    }

    @Test fun deletingAFolderMovesItsRoutinesToTheEndOfTheDefault() = runBlocking {
        val doomed = repo.createRoutineFolder("Hypertrophy")
        val kept = repo.createRoutineFolder("Strength")
        val d1 = repo.createRoutine("D1")
        val a1 = repo.createRoutine("A1", doomed)
        val d2 = repo.createRoutine("D2")
        val k1 = repo.createRoutine("K1", kept)
        val a2 = repo.createRoutine("A2", doomed)
        val entry = repo.addRoutineExercise(a1, exercise())
        val before = repo.snapshot()

        repo.deleteRoutineFolder(doomed)

        val after = repo.snapshot()
        assertNull(repo.routineFolder(doomed))
        assertEquals(listOf(d1, d2, k1, a1, a2), after.routines.sortedBy { it.position }.map { it.id })
        assertEquals(listOf(0, 1, 2, 3, 4), after.routines.sortedBy { it.position }.map { it.position })
        assertEquals(listOf(d1, d2, a1, a2), routines(RoutineFolder.DEFAULT_ID))
        assertEquals(listOf(k1), routines(kept))
        // No routine is lost or renamed, and what they plan is untouched.
        assertEquals(before.routines.map { it.id to it.name }.toSet(), after.routines.map { it.id to it.name }.toSet())
        assertEquals(before.routineExercises, after.routineExercises)
        assertEquals(before.routineSets, after.routineSets)
        assertEquals(entry, after.routineItems(a1).single().id)
        assertRefused(DataError.FOLDER_NOT_FOUND) { repo.deleteRoutineFolder(doomed) }
    }

    @Test fun deletingAFolderKeepsTheActiveWorkoutsSource() = runBlocking {
        val folder = repo.createRoutineFolder("Hypertrophy")
        val routine = repo.createRoutine("Upper", folder)
        repo.addRoutineExercise(routine, exercise())
        val workout = repo.startRoutine(routine)
        val before = repo.snapshot()

        repo.deleteRoutineFolder(folder)

        val after = repo.snapshot()
        assertEquals(routine, after.activeWorkout!!.sourceRoutineId)
        assertEquals(workout, after.activeWorkout!!.id)
        assertEquals(RoutineFolder.DEFAULT_ID, after.routines.single { it.id == routine }.folderId)
        assertEquals(before.workouts, after.workouts)
        assertEquals(before.workoutExercises, after.workoutExercises)
        assertEquals(before.sets, after.sets)
    }

    @Test fun theDefaultFolderCannotBeDeleted() = runBlocking {
        repo.createRoutine("Mine")
        repo.createRoutineFolder("Other")
        assertRefused(DataError.INVALID_VALUE) { repo.deleteRoutineFolder(RoutineFolder.DEFAULT_ID) }
        assertRefused(DataError.FOLDER_NOT_FOUND) { repo.deleteRoutineFolder("missing") }
        assertNotNull(repo.routineFolder(RoutineFolder.DEFAULT_ID))
    }

    @Test fun deletingRenumbersTheFolders() = runBlocking {
        val a = repo.createRoutineFolder("A")
        val b = repo.createRoutineFolder("B")
        val c = repo.createRoutineFolder("C")
        // The default folder reorders like any other, so it need not be first.
        repo.reorderRoutineFolders(listOf(a, RoutineFolder.DEFAULT_ID, b, c))

        repo.deleteRoutineFolder(b)

        val folders = repo.snapshot().routineFolders.sortedBy { it.position }
        assertEquals(listOf(a, RoutineFolder.DEFAULT_ID, c), folders.map { it.id })
        assertEquals(listOf(0, 1, 2), folders.map { it.position })
    }

    @Test fun reorderRoutineFoldersNeedsEveryId() = runBlocking {
        val a = repo.createRoutineFolder("A")
        val b = repo.createRoutineFolder("B")
        val default = RoutineFolder.DEFAULT_ID
        assertRefused(DataError.INVALID_VALUE) { repo.reorderRoutineFolders(listOf(b, a)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderRoutineFolders(listOf(b, a, default, a)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderRoutineFolders(listOf(b, a, a)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderRoutineFolders(listOf(b, a, "missing")) }

        repo.reorderRoutineFolders(listOf(b, default, a))

        val folders = repo.snapshot().routineFolders
        assertEquals(listOf(b, default, a), folders.map { it.id })
        assertEquals(listOf(0, 1, 2), folders.map { it.position })
        assertEquals(listOf("B", RoutineFolder.DEFAULT_NAME, "A"), folders.map { it.name })
    }

    @Test fun reorderFolderRoutinesKeepsTheOtherFoldersSlots() = runBlocking {
        val folder = repo.createRoutineFolder("Hypertrophy")
        val d1 = repo.createRoutine("D1")
        val a1 = repo.createRoutine("A1", folder)
        val d2 = repo.createRoutine("D2")
        val a2 = repo.createRoutine("A2", folder)
        val a3 = repo.createRoutine("A3", folder)

        repo.reorderFolderRoutines(folder, listOf(a3, a1, a2))

        val positions = repo.snapshot().routines.associate { it.id to it.position }
        // The folder's routines trade the global slots they held (1, 3, 4); the others keep theirs.
        assertEquals(mapOf(d1 to 0, a3 to 1, d2 to 2, a1 to 3, a2 to 4), positions)
        assertEquals(listOf(a3, a1, a2), routines(folder))
        assertEquals(listOf(d1, d2), routines(RoutineFolder.DEFAULT_ID))
    }

    @Test fun reorderFolderRoutinesRefusesForeignIds() = runBlocking {
        val folder = repo.createRoutineFolder("Hypertrophy")
        val d1 = repo.createRoutine("D1")
        val a1 = repo.createRoutine("A1", folder)
        val a2 = repo.createRoutine("A2", folder)
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a2, a1, d1)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a2, d1)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a2)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a2, a2)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a2, a1, "missing")) }
        assertRefused(DataError.FOLDER_NOT_FOUND) { repo.reorderFolderRoutines("missing", listOf(a2, a1)) }
    }

    @Test fun warmupMakesTheSetManualAndKeepsItsValues() = runBlocking {
        val exerciseId = exercise()
        record(exerciseId, 80.0, 6)
        val entry = repo.addRoutineExercise(repo.createRoutine("Push"), exerciseId)
        val (first, second) = rows(entry)
        assertTrue(first.inheritPr)

        repo.setRoutineSetWarmup(first.id, true)

        assertEquals(first.copy(warmup = true, inheritPr = false), repo.snapshot().routineSets.single { it.id == first.id })
        // Back to a working set: it stays manual, with the same values.
        repo.setRoutineSetWarmup(first.id, false)
        assertEquals(first.copy(inheritPr = false), repo.snapshot().routineSets.single { it.id == first.id })
        // Asking for what a set already is changes nothing: an inherited working set keeps following the record.
        repo.setRoutineSetWarmup(second.id, false)
        assertEquals(second, repo.snapshot().routineSets.single { it.id == second.id })
        assertRefused(DataError.ROUTINE_NOT_FOUND) { repo.setRoutineSetWarmup("missing", true) }
    }

    @Test fun inheritingAWarmupMakesAWorkingSet() = runBlocking {
        val exerciseId = exercise()
        record(exerciseId, 80.0, 6)
        val entry = repo.addRoutineExercise(repo.createRoutine("Push"), exerciseId)
        val first = rows(entry).first()
        repo.updateRoutineSet(first.id, 12, 40.0)
        repo.setRoutineSetWarmup(first.id, true)

        repo.inheritRoutineSet(first.id)

        assertEquals(RoutineSet(first.id, entry, 0, 6, 80.0, inheritPr = true, warmup = false),
            repo.snapshot().routineSets.single { it.id == first.id })
    }

    @Test fun synchronizationIgnoresWarmups() = runBlocking {
        val exerciseId = exercise()
        val entry = repo.addRoutineExercise(repo.createRoutine("Push"), exerciseId)
        val (first, second) = rows(entry)
        // A backup can bring a warm-up that also says it follows the record; the screens never write one.
        val imported = first.copy(targetReps = 12, targetWeightKg = 40.0, inheritPr = true, warmup = true)
        database.dao().updateRoutineSet(imported)

        record(exerciseId, 100.0, 5)
        repo.initialize()

        val saved = rows(entry)
        assertEquals(imported, saved[0])
        assertEquals(second.copy(targetReps = 5, targetWeightKg = 100.0), saved[1])
        // The plan's legacy columns mirror the working set, not the warm-up (F19).
        assertEquals(5, repo.routineExercise(entry)!!.targetReps)
        assertEquals(100.0, repo.routineExercise(entry)!!.targetWeightKg!!, 0.0)
    }

    @Test fun mirroredPlanColumnsFollowTheFirstWorkingSet() = runBlocking {
        val (entry, original) = threeSets()
        var plan = repo.routineExercise(entry)!!
        assertEquals(3, plan.plannedSets)
        assertEquals(5 to 100.0, plan.targetReps to plan.targetWeightKg)

        original.drop(1).forEach { repo.setRoutineSetWarmup(it.id, true) }
        repo.updateRoutineSet(original[1].id, 6, 50.0)

        // With warm-ups only, the first row is mirrored, as before warm-ups existed.
        plan = repo.routineExercise(entry)!!
        assertEquals(10 to 40.0, plan.targetReps to plan.targetWeightKg)
    }

    @Test fun addRoutineSetCopiesTheLastSet() = runBlocking {
        val (entry, original) = threeSets()
        val inherited = repo.addRoutineSet(entry)
        assertEquals(original.last().copy(id = inherited, position = 3), rows(entry).last())

        repo.setRoutineSetWarmup(inherited, true)
        repo.updateRoutineSet(inherited, 8, 50.0)
        val warmup = repo.addRoutineSet(entry)

        val added = rows(entry).last()
        assertEquals(RoutineSet(warmup, entry, 4, 8, 50.0, inheritPr = false, warmup = true), added)
        assertEquals(5, repo.routineExercise(entry)!!.plannedSets)
    }

    @Test fun replaceRoutineExerciseKeepsWarmups() = runBlocking {
        val (entry, original) = threeSets()
        val replacement = exercise()
        record(replacement, 70.0, 9)

        repo.replaceRoutineExercise(entry, replacement)

        // A warm-up stays a warm-up, emptied and manual (F18); working sets inherit the new record.
        assertEquals(listOf(
            RoutineSet(original[0].id, entry, 0, 0, null, inheritPr = false, warmup = true),
            RoutineSet(original[1].id, entry, 1, 9, 70.0, inheritPr = true, warmup = false),
            RoutineSet(original[2].id, entry, 2, 9, 70.0, inheritPr = true, warmup = false),
        ), rows(entry))
        assertEquals(replacement, repo.routineExercise(entry)!!.exerciseId)
        assertEquals(9 to 70.0, repo.routineExercise(entry)!!.let { it.targetReps to it.targetWeightKg })
    }

    @Test fun startRoutineCopiesWarmups() = runBlocking {
        val (entry, _) = threeSets()
        val routine = repo.routineExercise(entry)!!.routineId

        val workout = repo.startRoutine(routine)

        val sets = repo.snapshot().let { it.exerciseSets(it.workoutItems(workout).single().id) }
        assertEquals(listOf(true, false, false), sets.map { it.warmup })
        assertEquals(listOf(10 to 40.0, 5 to 100.0, 0 to 0.0), sets.map { it.reps to it.weightKg })
        assertTrue(sets.none { it.completed })
    }

    @Test fun restoreRoutineSetPutsItBackAtItsPositionWithItsId() = runBlocking {
        val (entry, original) = threeSets()
        val (a, b, c) = original
        repo.removeRoutineSet(a.id)
        assertEquals(listOf(b.id to 0, c.id to 1), rows(entry).map { it.id to it.position })

        repo.restoreRoutineSet(entry, 0, a)

        // Same id, values, warm-up flag and mode, at its place; the mirrored columns still follow B.
        assertEquals(original, rows(entry))
        assertEquals(5 to 100.0, repo.routineExercise(entry)!!.let { it.targetReps to it.targetWeightKg })
        assertEquals(3, repo.routineExercise(entry)!!.plannedSets)
        // Past the end, it goes last.
        repo.removeRoutineSet(b.id)
        repo.restoreRoutineSet(entry, 9, b)
        assertEquals(listOf(a, c.copy(position = 1), b.copy(position = 2)), rows(entry))
    }

    @Test fun restoreRoutineSetTwiceRefusesTheSecond() = runBlocking {
        val (entry, original) = threeSets()
        val b = original[1]
        repo.removeRoutineSet(b.id)
        repo.restoreRoutineSet(entry, 1, b)
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSet(entry, 1, b) }
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSet(entry, 0, b) }
        assertEquals(original, rows(entry))
    }

    @Test fun restoreAfterTheEntryIsRemovedIsRefused() = runBlocking {
        val (entry, original) = threeSets()
        val b = original[1]
        repo.removeRoutineSet(b.id)
        repo.removeRoutineExercise(entry)
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSet(entry, 1, b) }
        assertNull(repo.snapshot().routineSets.firstOrNull { it.id == b.id })
    }

    @Test fun restoreRefusesTheFiftyFirstSet() = runBlocking {
        val (entry, original) = threeSets()
        val b = original[1]
        repo.removeRoutineSet(b.id)
        while (rows(entry).size < 50) repo.addRoutineSet(entry)
        // The limit is the one addRoutineSet applies, so the screen shows its text (F9).
        assertRefused(DataError.SET_LIMIT) { repo.restoreRoutineSet(entry, 1, b) }
        assertNull(repo.snapshot().routineSets.firstOrNull { it.id == b.id })
    }
}
