package com.mooscles.screens.workout

import androidx.compose.runtime.saveable.SaverScope
import com.mooscles.ui.SetField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveWorkoutScreenStateTest {
    private val scope = SaverScope { true }

    private fun roundTrip(state: ActiveWorkoutScreenState): ActiveWorkoutScreenState {
        val saved = with(ActiveWorkoutScreenState.Saver) { scope.save(state) }
        return requireNotNull(saved?.let { ActiveWorkoutScreenState.Saver.restore(it) })
    }

    @Test
    fun saverKeepsEveryField() {
        // An id with a colon, as the saved form splits on the first one.
        val state = ActiveWorkoutScreenState(
            cleared = setOf("s1" to SetField.REPS, "a:b" to SetField.LOAD),
            optimistic = mapOf("s1" to true, "s:2" to false),
            editing = SetEditTarget("s:3", SetField.REPS, keyboard = true, error = true),
        )
        val restored = roundTrip(state)
        assertEquals(state.cleared, restored.cleared)
        assertEquals(state.optimistic, restored.optimistic)
        assertEquals(state.editing, restored.editing)

        val empty = roundTrip(ActiveWorkoutScreenState())
        assertEquals(emptySet(), empty.cleared)
        assertEquals(emptyMap(), empty.optimistic)
        assertNull(empty.editing)
    }
}
