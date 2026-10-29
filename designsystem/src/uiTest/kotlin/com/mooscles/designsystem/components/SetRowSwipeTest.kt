package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetRowSwipeTest {
    private class Calls {
        var completes = 0
        var removes = 0
    }

    /** A 400 dp row with the swipe; returns its left edge at rest. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.swipeRow(haptics: RecordingHaptics, calls: Calls, enabled: Boolean = true): Float {
        withTheme(haptics = haptics) {
            Box(Modifier.width(400.dp).height(56.dp).setRowSwipe(enabled, { calls.completes++ }, { calls.removes++ }).testTag("swipe"))
        }
        waitForIdle()
        return onNodeWithTag("swipe").fetchSemanticsNode().boundsInRoot.left
    }

    /** Drags the row by [fraction] of its width (negative to the left), slowly enough to be a drag, and lets go. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.drag(fraction: Float) {
        onNodeWithTag("swipe").performTouchInput {
            swipe(start = center, end = Offset(centerX + width * fraction, centerY), durationMillis = 400)
        }
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.assertBackAtRest(left: Float) {
        val now = onNodeWithTag("swipe").fetchSemanticsNode().boundsInRoot.left
        assertEquals(left, now, 0.5f, "the row did not go back to the center")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rightPastThirtyFivePercentCompletes() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls)
        drag(0.4f)
        assertEquals(1, calls.completes)
        assertEquals(0, calls.removes)
        assertEquals(listOf(HapticEvent.StepperStep), haptics.events.toList())
        assertBackAtRest(left)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun shortOfTheThresholdSettlesBack() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls)
        drag(0.3f)
        drag(-0.3f)
        assertEquals(0, calls.completes)
        assertEquals(0, calls.removes)
        assertEquals(emptyList(), haptics.events.toList())
        assertBackAtRest(left)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun leftPastTheThresholdRemoves() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls)
        drag(-0.4f)
        assertEquals(0, calls.completes)
        assertEquals(1, calls.removes)
        assertEquals(listOf(HapticEvent.StepperStep), haptics.events.toList())
        assertBackAtRest(left)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun disabledDoesNothing() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls, enabled = false)
        mainClock.autoAdvance = false
        onNodeWithTag("swipe").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(width * 0.05f, 0f)) }
        }
        mainClock.advanceTimeBy(100)
        // Halfway across the row the finger is still down, and the row has not moved.
        assertBackAtRest(left)
        onNodeWithTag("swipe").performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
        drag(-0.4f)
        assertEquals(0, calls.completes)
        assertEquals(0, calls.removes)
        assertEquals(emptyList(), haptics.events.toList())
        assertBackAtRest(left)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun verticalDragScrollsTheList() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        var list: LazyListState? = null
        withTheme(haptics = haptics) {
            val state = rememberLazyListState().also { list = it }
            LazyColumn(Modifier.height(300.dp).width(400.dp), state = state) {
                items(30) { index ->
                    Box(
                        Modifier.width(400.dp).height(56.dp)
                            .setRowSwipe(true, { calls.completes++ }, { calls.removes++ })
                            .testTag("row-$index"),
                    )
                }
            }
        }
        waitForIdle()
        // Up the list with a little sideways drift, as a thumb does.
        onNodeWithTag("row-2").performTouchInput {
            swipe(start = center, end = Offset(centerX + 12.dp.toPx(), centerY - 150.dp.toPx()), durationMillis = 400)
        }
        waitForIdle()
        onNodeWithTag("row-4").performTouchInput { swipeUp(startY = centerY, endY = centerY - 100.dp.toPx(), durationMillis = 150) }
        waitForIdle()
        val scrolled = list!!.firstVisibleItemIndex > 0 || list!!.firstVisibleItemScrollOffset > 0
        assertTrue(scrolled, "the list did not scroll")
        assertEquals(0, calls.completes)
        assertEquals(0, calls.removes)
        assertEquals(emptyList(), haptics.events.toList())
    }
}
