package com.mooscles.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupCodecTest {
    private fun sample(muscle: String = "CHEST") = BackupFile(
        format = BackupCodec.FORMAT,
        formatVersion = BackupCodec.FORMAT_VERSION,
        exportedAt = "2026-10-27T12:00:00Z",
        app = BackupApp("1.4.5", 16, "android"),
        preferences = BackupPreferences(false, "system", true, 90, "default"),
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
            .replaceFirst("\"name\":\"Bench press\"", "\"name\":\"Bench press\",\"extra\":1")
        assertTrue(text.contains("\"Bench press\",\"extra\":1"))
        assertEquals(sample(), BackupCodec.decode(text))
    }

    @Test
    fun encodeIsCompact() {
        val text = BackupCodec.encode(sample())
        assertTrue(text.startsWith("{\"format\":\"mooscles-backup\",\"formatVersion\":1,"))
        assertTrue('\n' !in text, "compact output has no line breaks")
    }

    @Test
    fun decodeRejectsNewerVersion() {
        val e = assertFailsWith<BackupException.NewerVersion> {
            BackupCodec.decode("""{"format":"mooscles-backup","formatVersion":2,"data":"different"}""")
        }
        assertEquals(2, e.found)
    }

    @Test
    fun decodeRejectsOtherFormat() {
        assertFailsWith<BackupException.NotABackup> {
            BackupCodec.decode("""{"format":"something","formatVersion":1}""")
        }
        assertFailsWith<BackupException.NotABackup> { BackupCodec.decode("[]") }
    }

    @Test
    fun decodeRejectsMalformedJson() {
        listOf("{", "", """{"format":"mooscles-backup","formatVersion":1}""").forEach { text ->
            assertFailsWith<BackupException.Corrupted>(text) { BackupCodec.decode(text) }
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
        assertFailsWith<BackupException.Invalid> { file.data.toEntities() }
    }
}
