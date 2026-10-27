package com.mooscles.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTextsTest {
    @Test fun countsUseSingularOnlyForOne() {
        assertEquals("1 workout, 1 routine and 1 custom exercise", BackupTexts.counts(1, 1, 1))
        assertEquals("2 workouts, 3 routines and 4 custom exercises", BackupTexts.counts(2, 3, 4))
    }

    @Test fun zeroStaysPlural() {
        assertEquals("0 workouts, 0 routines and 0 custom exercises", BackupTexts.counts(0, 0, 0))
    }

    @Test fun exportedMessageNamesEveryCount() {
        assertEquals("Exported 12 workouts, 1 routine and 0 custom exercises", BackupTexts.exported(12, 1, 0))
    }

    @Test fun importedMessageUsesAddedCounts() {
        val summary = ImportSummary(
            workoutsAdded = 1, workoutsKept = 5, routinesAdded = 2, routinesKept = 7,
            customExercisesAdded = 1, restoresPreferences = false,
        )
        assertEquals("Imported 1 workout, 2 routines and 1 custom exercise", BackupTexts.imported(summary))
    }

    @Test fun summaryOfSpecExample() {
        assertEquals(
            "Add 180 workouts, 4 routines and 3 custom exercises. 28 workouts are already on this phone and will be kept as they are.",
            BackupTexts.summary(ImportSummary(180, 28, 4, 0, 3, false)),
        )
        assertEquals("Add 180 workouts, 4 routines and 3 custom exercises.", BackupTexts.summary(ImportSummary(180, 0, 4, 0, 3, false)))
    }

    @Test fun summaryMentionsSingleKeptWorkoutInSingular() {
        assertEquals(
            "Add 2 workouts, 0 routines and 0 custom exercises. 1 workout is already on this phone and will be kept as it is.",
            BackupTexts.summary(ImportSummary(2, 1, 0, 0, 0, false)),
        )
    }

    @Test fun summaryMentionsSeveralKeptWorkoutsInPlural() {
        assertEquals(
            "Add 1 workout, 1 routine and 1 custom exercise. 5 workouts are already on this phone and will be kept as they are.",
            BackupTexts.summary(ImportSummary(1, 5, 1, 0, 1, false)),
        )
    }

    @Test fun summaryMentionsRestoredPreferences() {
        assertEquals(
            "Add 0 workouts, 0 routines and 0 custom exercises. Your units, theme and rest settings will also be restored.",
            BackupTexts.summary(ImportSummary(0, 0, 0, 0, 0, true)),
        )
        assertEquals(
            "Add 3 workouts, 0 routines and 0 custom exercises. 2 workouts are already on this phone and will be kept as they are. Your units, theme and rest settings will also be restored.",
            BackupTexts.summary(ImportSummary(3, 2, 0, 0, 0, true)),
        )
    }
}
