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
}
