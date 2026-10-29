package com.mooscles.screens.workout

import androidx.compose.runtime.saveable.SaverScope
import com.mooscles.ui.SetField
import com.mooscles.ui.SetUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActiveWorkoutScreenStateTest {
    private val scope = SaverScope { true }

    private fun roundTrip(state: ActiveWorkoutScreenState): ActiveWorkoutScreenState {
        val saved = with(ActiveWorkoutScreenState.Saver) { scope.save(state) }
        return requireNotNull(saved?.let { ActiveWorkoutScreenState.Saver.restore(it) })
    }

    @Test
    fun saverKeepsClearedAndEditingButNotOptimistic() {
        // An id with a colon, as the saved form splits on the first one.
        val state = ActiveWorkoutScreenState(
            cleared = setOf("s1" to SetField.REPS, "a:b" to SetField.LOAD),
            optimistic = mapOf("s1" to true, "s:2" to false),
            editing = SetEditTarget("s:3", SetField.REPS, keyboard = true, error = true),
        )
        val restored = roundTrip(state)
        assertEquals(state.cleared, restored.cleared)
        assertEquals(state.editing, restored.editing)
        // A write lost with the process must not come back as a full disc: the database decides.
        assertEquals(emptyMap(), restored.optimistic)
        assertFalse(restored.completedOf(SetUi("s1")))

        val empty = roundTrip(ActiveWorkoutScreenState())
        assertEquals(emptySet(), empty.cleared)
        assertEquals(emptyMap(), empty.optimistic)
        assertNull(empty.editing)
    }

    @Test
    fun droppingTheOptimisticValueShowsTheDatabaseAgain() {
        val pending = SetUi("s1", reps = 8, weightKg = 80.0)
        val done = SetUi("s2", reps = 8, weightKg = 80.0, completed = true)
        val state = ActiveWorkoutScreenState()
        state.expect("s1", true)
        state.expect("s2", false)
        assertTrue(state.completedOf(pending))
        assertFalse(state.completedOf(done))

        // The check of s1 failed: no echo will settle it.
        state.dropOptimistic("s1")
        assertFalse(state.completedOf(pending))
        assertFalse(state.completedOf(done), "only s1 was dropped")
        state.dropOptimistic("unknown")
        assertEquals(mapOf("s2" to false), state.optimistic)

        state.dropAllOptimistic()
        assertEquals(emptyMap(), state.optimistic)
        assertTrue(state.completedOf(done))
    }
}
