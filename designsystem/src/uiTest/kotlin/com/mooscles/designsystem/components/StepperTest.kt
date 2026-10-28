package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepperTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun stepChangesValueAndTicks() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var value by mutableStateOf(20.0)
        withTheme(haptics = haptics) {
            Stepper(value, { value = it }, step = 2.5, min = 0.0, display = "$value", decreaseLabel = "Decrease", increaseLabel = "Increase")
        }
        waitForIdle()
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(22.5, value)
        assertEquals(listOf(HapticEvent.StepperStep), haptics.events.toList())
        onNodeWithTag(StepperDecreaseTag).performClick()
        waitForIdle()
        assertEquals(20.0, value)
        assertEquals(listOf(HapticEvent.StepperStep, HapticEvent.StepperStep), haptics.events.toList())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun minimumDisablesDecrease() = runComposeUiTest {
        withTheme { Stepper(0.0, {}, step = 2.5, min = 0.0, display = "0", decreaseLabel = "Decrease", increaseLabel = "Increase") }
        waitForIdle()
        onNodeWithTag(StepperDecreaseTag).assertIsNotEnabled()
        onNodeWithTag(StepperIncreaseTag).assertIsEnabled()
    }

    /** The margins (not the exact 399/400/480/560 ms) absorb the frame the press takes to reach the effect. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun holdRepeatsAfterFourHundredThenEveryEighty() = runComposeUiTest {
        var steps = 0
        var value by mutableStateOf(0.0)
        withTheme {
            Stepper(value, { value = it; steps++ }, step = 1.0, min = 0.0, display = "$value", decreaseLabel = "Decrease", increaseLabel = "Increase")
        }
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(300)
        assertEquals(0, steps, "before 400 ms")
        mainClock.advanceTimeBy(150)
        assertEquals(1, steps, "at 450 ms")
        mainClock.advanceTimeBy(50)
        assertEquals(2, steps, "at 500 ms")
        mainClock.advanceTimeBy(90)
        assertEquals(3, steps, "at 590 ms")
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        mainClock.advanceTimeBy(500)
        assertEquals(3, steps, "releasing adds nothing")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun buttonsAreLabelledWithTheStep() = runComposeUiTest {
        withTheme {
            Stepper(
                20.0, {}, step = 2.5, min = 0.0, display = "20",
                decreaseLabel = "Decrease load by 2.5 kilograms", increaseLabel = "Increase load by 2.5 kilograms",
            )
        }
        waitForIdle()
        onNodeWithContentDescription("Increase load by 2.5 kilograms").assertExists()
        onNodeWithContentDescription("Decrease load by 2.5 kilograms").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun buttonsMeetTheWorkoutTarget() = runComposeUiTest {
        withTheme { Stepper(20.0, {}, step = 2.5, min = 0.0, display = "20", decreaseLabel = "Decrease", increaseLabel = "Increase") }
        waitForIdle()
        val sizes = MoosclesSizes()
        for (tag in listOf(StepperDecreaseTag, StepperIncreaseTag)) {
            val touch = onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            val height = with(density) { sizes.stepperButtonHeight.toPx() }
            val width = with(density) { sizes.stepperButtonWidth.toPx() }
            assertTrue(touch.height >= height - 0.5f, "$tag height ${touch.height} < $height")
            assertTrue(touch.width >= width - 0.5f, "$tag width ${touch.width} < $width")
        }
    }
}
