package com.mooscles.screens.workout

import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.SetRecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which record the active workout announces (spec D17, D18): only a record the presentation put on
 * a set completed on this screen, once per value.
 */
class RecordAnnouncerTest {
    private val bench = ExerciseUi("bench", "Supino reto", "Chest", "Barbell", convention = "Total load")
    private val row = ExerciseUi("row", "Remada curvada", "Back", "Barbell", convention = "Total load")
    private val weight = SetRecordUi(RecordKind.WEIGHT, previousKg = 85.0)

    private fun workout(vararg entries: Pair<ExerciseUi, List<SetUi>>) = WorkoutUi(
        id = "w1",
        name = "Treino A",
        startedAt = 0L,
        exercises = entries.mapIndexed { i, (exercise, sets) -> WorkoutExerciseUi("e${i + 1}", exercise, sets) },
    )

    @Test
    fun announcesOnlySetsCompletedHere() {
        val state = workout(
            bench to listOf(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight)),
            row to listOf(SetUi("r1", reps = 8, weightKg = 70.0, completed = true, record = SetRecordUi(RecordKind.REPS, previousReps = 6))),
        )
        assertEquals(
            RecordEvent(key = "r1:70.0:8:REPS", setId = "r1", exerciseName = "Remada curvada", record = SetRecordUi(RecordKind.REPS, previousReps = 6), weightKg = 70.0, reps = 8),
            RecordAnnouncer.next(state, completedHere = setOf("r1"), announced = emptySet()),
        )
        assertEquals("s1:87.5:6:WEIGHT", RecordAnnouncer.next(state, completedHere = setOf("s1"), announced = emptySet())?.key)
        // A set completed here without a record (or a first record, which never has one) says nothing.
        val plain = workout(bench to listOf(SetUi("s1", reps = 6, weightKg = 87.5, completed = true)))
        assertNull(RecordAnnouncer.next(plain, completedHere = setOf("s1"), announced = emptySet()))
    }

    @Test
    fun recordsPresentAtOpenAreNotAnnounced() {
        // Resuming the workout or restarting the app: the records were already there.
        val state = workout(
            bench to listOf(
                SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight),
                SetUi("s2", reps = 6, weightKg = 80.0, completed = true),
            ),
        )
        assertNull(RecordAnnouncer.next(state, completedHere = emptySet(), announced = emptySet()))
        // Completing another set here does not announce the old one.
        assertNull(RecordAnnouncer.next(state, completedHere = setOf("s2"), announced = emptySet()))
    }

    @Test
    fun aLaterSetBeatingItAnnouncesAgainWithTheNewValue() {
        val first = workout(
            bench to listOf(
                SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight),
                SetUi("s2", reps = 5, weightKg = 90.0),
            ),
        )
        val announced = setOf(RecordAnnouncer.next(first, setOf("s1"), emptySet())!!.key)
        // The record moves to the heavier set; "before" is still the history's best.
        val later = workout(
            bench to listOf(
                SetUi("s1", reps = 6, weightKg = 87.5, completed = true),
                SetUi("s2", reps = 5, weightKg = 90.0, completed = true, record = weight),
            ),
        )
        val event = RecordAnnouncer.next(later, setOf("s1", "s2"), announced)
        assertEquals("s2:90.0:5:WEIGHT", event?.key)
        assertEquals(90.0, event?.weightKg)
        assertEquals(5, event?.reps)
        assertEquals(85.0, event?.record?.previousKg)
        // The same set edited to a new value beats it again: a new key, a new notice.
        val edited = workout(bench to listOf(SetUi("s1", reps = 6, weightKg = 92.5, completed = true, record = weight)))
        assertEquals("s1:92.5:6:WEIGHT", RecordAnnouncer.next(edited, setOf("s1"), announced)?.key)
    }

    @Test
    fun losingARecordIsSilent() {
        val announced = setOf("s1:87.5:6:WEIGHT")
        // Unchecked, or edited below the record: the presentation takes the record away.
        val unchecked = workout(bench to listOf(SetUi("s1", reps = 6, weightKg = 87.5, completed = false)))
        assertNull(RecordAnnouncer.next(unchecked, setOf("s1"), announced))
        val lighter = workout(bench to listOf(SetUi("s1", reps = 6, weightKg = 80.0, completed = true)))
        assertNull(RecordAnnouncer.next(lighter, setOf("s1"), announced))
        // Coming back to the value already announced says nothing again.
        val back = workout(bench to listOf(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight)))
        assertNull(RecordAnnouncer.next(back, setOf("s1"), announced))
    }

    @Test
    fun theSameRecordIsAnnouncedOnce() {
        val state = workout(bench to listOf(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight)))
        val event = RecordAnnouncer.next(state, setOf("s1"), emptySet())
        assertEquals("s1:87.5:6:WEIGHT", event?.key)
        // Every later emission (a scroll, a recreation, another write) finds it announced.
        assertNull(RecordAnnouncer.next(state, setOf("s1"), setOf(event!!.key)))
        assertNull(RecordAnnouncer.next(state.copy(name = "Treino B"), setOf("s1"), setOf(event.key)))
    }
}
