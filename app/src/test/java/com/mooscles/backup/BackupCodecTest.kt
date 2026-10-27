package com.mooscles.backup

import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.WorkoutStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private fun sample(muscle: String = "CHEST") = BackupFile(
        format = BackupCodec.FORMAT,
        formatVersion = BackupCodec.FORMAT_VERSION,
        exportedAt = "2026-10-27T12:00:00Z",
        app = BackupApp("1.4.5", 16, "android"),
        preferences = BackupPreferences(false, "SYSTEM", true, 90, "default"),
        data = BackupData(
            exercises = listOf(ExerciseRow("e1", "Bench press", muscle, "BARBELL", "TOTAL_LOAD", true)),
            routineFolders = listOf(RoutineFolderRow("default", "My routines", 0)),
            routines = listOf(RoutineRow("r1", "Push", 0, "default")),
            routineExercises = listOf(
                RoutineExerciseRow("re1", "r1", "e1", 0, 3, 8, 22.75, 120),
                RoutineExerciseRow("re2", "r1", "e1", 1, 2, 0, null, null),
            ),
            routineSets = listOf(
                RoutineSetRow("rs1", "re1", 0, 8, 0.1 + 0.2, true),
                RoutineSetRow("rs2", "re1", 1, 0, null, false),
            ),
            workouts = listOf(
                WorkoutRow("w1", "Push", 1000L, 2000L, "FINISHED", "r1"),
                WorkoutRow("w2", "Open", 3000L, null, "ACTIVE", null),
            ),
            workoutExercises = listOf(
                WorkoutExerciseRow("we1", "w1", "e1", "Bench press", muscle, "BARBELL", "TOTAL_LOAD", 0, 90),
            ),
            workoutSets = listOf(WorkoutSetRow("ws1", "we1", 0, 8, 22.75, true, false, false)),
            routineTargetCheckpoints = listOf(
                CheckpointRow("e1", "TOTAL_LOAD", 22.75, 8),
                CheckpointRow("e1", "BODYWEIGHT", null, 5),
            ),
        ),
    )

    @Test
    fun encodeThenDecodeReturnsEqualFile() {
        val file = sample()
        assertEquals(file, BackupCodec.decode(BackupCodec.encode(file)))
    }

    @Test
    fun decodeIgnoresUnknownFields() {
        val text = BackupCodec.encode(sample())
            .replaceFirst("{", "{\"extra\": 1,")
            .replaceFirst("\"name\": \"Bench press\"", "\"name\": \"Bench press\", \"extra\": 1")
        assertTrue(text.contains("\"extra\""))
        assertEquals(sample(), BackupCodec.decode(text))
    }

    @Test
    fun decodeRejectsNewerVersion() {
        val e = assertThrows(BackupException.NewerVersion::class.java) {
            BackupCodec.decode("""{"format":"mooscles-backup","formatVersion":2,"data":"different"}""")
        }
        assertEquals(2, e.found)
    }

    @Test
    fun decodeRejectsOtherFormat() {
        assertThrows(BackupException.NotABackup::class.java) {
            BackupCodec.decode("""{"format":"something","formatVersion":1}""")
        }
        assertThrows(BackupException.NotABackup::class.java) { BackupCodec.decode("[]") }
    }

    @Test
    fun decodeRejectsMalformedJson() {
        listOf("{", "", """{"format":"mooscles-backup","formatVersion":1}""").forEach { text ->
            assertThrows(text, BackupException.Corrupted::class.java) { BackupCodec.decode(text) }
        }
    }

    @Test
    fun decodeAcceptsUtf8Bom() {
        val file = sample()
        assertEquals(file, BackupCodec.decode("\uFEFF" + BackupCodec.encode(file)))
    }

    @Test
    fun unknownEnumIsInvalid() {
        val file = BackupCodec.decode(BackupCodec.encode(sample(muscle = "TAIL")))
        assertThrows(BackupException.Invalid::class.java) { file.data.toEntities() }
    }

    @Test
    fun v1SampleFixtureStaysReadable() {
        val text = javaClass.classLoader!!.getResourceAsStream("backup/v1-sample.json")!!
            .readBytes().toString(Charsets.UTF_8)
        val file = BackupCodec.decode(text)
        assertEquals(1, file.formatVersion)
        val e = file.data.toEntities()
        assertEquals(1, e.exercises.size)
        assertEquals("Bench press", e.exercises.single().name)
        assertEquals(MuscleGroup.CHEST, e.exercises.single().muscle)
        assertEquals(1, e.routineFolders.size)
        assertEquals("My routines", e.routineFolders.single().name)
        assertEquals(1, e.routines.size)
        assertEquals("Push", e.routines.single().name)
        assertEquals(2, e.routineExercises.size)
        assertEquals(22.75, e.routineExercises.first().targetWeightKg!!, 0.0)
        assertNull(e.routineExercises.last().restSeconds)
        assertEquals(2, e.routineSets.size)
        assertEquals(8, e.routineSets.first().targetReps)
        assertEquals(2, e.workouts.size)
        assertEquals(WorkoutStatus.FINISHED, e.workouts.first().status)
        assertEquals(1, e.workoutExercises.size)
        assertEquals(LoadConvention.TOTAL_LOAD, e.workoutExercises.single().convention)
        assertEquals(1, e.workoutSets.size)
        assertEquals(22.75, e.workoutSets.single().weightKg, 0.0)
        assertEquals(2, e.checkpoints.size)
        assertEquals(LoadConvention.BODYWEIGHT, e.checkpoints.last().convention)
    }
}
