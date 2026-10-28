package com.mooscles.designsystem.components

import com.mooscles.designsystem.format.NumberLocale
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
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

    /** Timing is read from the test clock at each step, 1 ms at a time, so no assertion sits on a frame boundary. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun holdRepeatsAfterFourHundredThenEveryEighty() = runComposeUiTest {
        val times = mutableListOf<Long>()
        var value by mutableStateOf(0.0)
        withTheme {
            Stepper(value, { value = it; times += mainClock.currentTime }, step = 1.0, min = 0.0, display = "$value", decreaseLabel = "Decrease", increaseLabel = "Increase")
        }
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        val start = mainClock.currentTime
        repeat(700) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertTrue(times.size >= 4, "steps at ${times.map { it - start }}")
        val offsets = times.map { it - start }
        assertTrue(offsets[0] in 398..416, "first repeat at ${offsets[0]} ms, expected about 400")
        for (i in 1 until offsets.size) {
            val gap = offsets[i] - offsets[i - 1]
            assertTrue(gap in 64..96, "gap $i is $gap ms, expected about 80")
        }
        val before = times.size
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(400) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(before, times.size, "a step happened after the finger lifted")
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun keyboardActivatesTheFocusedButton() = runComposeUiTest {
        var value by mutableStateOf(0.0)
        withTheme { Stepper(value, { value = it }, step = 1.0, min = 0.0, display = "$value", decreaseLabel = "Decrease", increaseLabel = "Increase") }
        waitForIdle()
        onNodeWithTag(StepperIncreaseTag).requestFocus()
        waitForIdle()
        onNodeWithTag(StepperIncreaseTag).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(1.0, value)
        onNodeWithTag(StepperIncreaseTag).performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()
        assertEquals(2.0, value)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun defaultDisplayIsTheDomainDecimalNeverDoubleToString() {
        for ((locale, value, expected) in listOf(
            Triple(NumberLocale.EN, 20.0, "20"), Triple(NumberLocale.EN, 82.5, "82.5"),
            Triple(NumberLocale.PT_BR, 20.0, "20"), Triple(NumberLocale.PT_BR, 82.5, "82,5"),
        )) {
            runComposeUiTest {
                withTheme {
                    Stepper(value, {}, step = 2.5, min = 0.0, decreaseLabel = "Decrease", increaseLabel = "Increase", numberLocale = locale)
                }
                waitForIdle()
                onNodeWithTag(StepperValueTag).assertTextEquals(expected)
            }
        }
    }
}
