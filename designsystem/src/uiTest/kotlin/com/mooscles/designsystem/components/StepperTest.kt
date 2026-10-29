package com.mooscles.designsystem.components

import com.mooscles.designsystem.format.NumberLocale
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performSemanticsAction
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

    /** A Stepper between two spacers in a scrolling column 300 dp tall, so a vertical drag on a button really scrolls. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.scrollingStepper(haptics: RecordingHaptics, onChange: (Double) -> Unit): () -> ScrollState {
        var scroll: ScrollState? = null
        var value by mutableStateOf(20.0)
        withTheme(haptics = haptics) {
            val state = rememberScrollState().also { scroll = it }
            Column(Modifier.height(300.dp).verticalScroll(state)) {
                Spacer(Modifier.height(100.dp))
                Stepper(value, { value = it; onChange(it) }, step = 2.5, min = 0.0, display = "$value", decreaseLabel = "Decrease", increaseLabel = "Increase")
                Spacer(Modifier.height(2000.dp))
            }
        }
        waitForIdle()
        return { scroll!! }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fastFlickStartingOnAButtonScrollsWithoutAStep() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val changes = mutableListOf<Double>()
        val scroll = scrollingStepper(haptics) { changes += it }
        for (tag in listOf(StepperIncreaseTag, StepperDecreaseTag)) {
            onNodeWithTag(tag).performTouchInput {
                swipeUp(startY = centerY, endY = centerY - 60.dp.toPx(), durationMillis = 100)
            }
            waitForIdle()
        }
        assertTrue(scroll().value > 0, "the drag did not scroll the column")
        assertEquals(emptyList(), changes, "a flick that scrolled changed the value")
        assertEquals(emptyList(), haptics.events.toList())
    }

    /** One second of slow drag, 20 ms per move, so the 400 ms hold delay passes while the finger is still down. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun slowDragStartingOnAButtonScrollsWithoutAStep() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val changes = mutableListOf<Double>()
        val scroll = scrollingStepper(haptics) { changes += it }
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        repeat(50) {
            mainClock.advanceTimeBy(20, ignoreFrameDuration = true)
            onNodeWithTag(StepperIncreaseTag).performTouchInput { moveBy(Offset(0f, -4f)) }
        }
        mainClock.advanceTimeBy(20, ignoreFrameDuration = true)
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(400) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertTrue(scroll().value > 0, "the drag did not scroll the column")
        assertEquals(emptyList(), changes, "a slow drag that scrolled changed the value")
        assertEquals(emptyList(), haptics.events.toList())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun movingAwayDuringAHoldStopsTheRepeat() = runComposeUiTest {
        val changes = mutableListOf<Double>()
        scrollingStepper(RecordingHaptics()) { changes += it }
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        repeat(450) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(listOf(22.5), changes, "the hold did not step once at 400 ms")
        onNodeWithTag(StepperIncreaseTag).performTouchInput { moveBy(Offset(0f, -60.dp.toPx())) }
        repeat(400) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(100) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(listOf(22.5), changes, "the repeat went on after the finger moved away")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tapWithAJitterUnderTouchSlopStillSteps() = runComposeUiTest {
        val changes = mutableListOf<Double>()
        scrollingStepper(RecordingHaptics()) { changes += it }
        onNodeWithTag(StepperIncreaseTag).performTouchInput {
            down(center)
            moveBy(Offset(1f, -1f))
            up()
        }
        waitForIdle()
        assertEquals(listOf(22.5), changes)
    }

    /** A stepper that logs each change and each finish, in order. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.loggingStepper(log: MutableList<String>, start: Double = 20.0, max: Double = Double.POSITIVE_INFINITY) {
        var value by mutableStateOf(start)
        withTheme {
            Stepper(
                value, { value = it; log += "change:$it" }, step = 2.5, min = 0.0, display = "$value",
                decreaseLabel = "Decrease", increaseLabel = "Increase", max = max,
                onValueChangeFinished = { log += "finished" },
            )
        }
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tapFinishesOnceAfterTheStep() = runComposeUiTest {
        val log = mutableListOf<String>()
        loggingStepper(log)
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(listOf("change:22.5", "finished"), log)
        onNodeWithTag(StepperIncreaseTag).requestFocus()
        onNodeWithTag(StepperIncreaseTag).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf("change:22.5", "finished", "change:25.0", "finished"), log)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun holdFinishesOnceOnRelease() = runComposeUiTest {
        val log = mutableListOf<String>()
        loggingStepper(log)
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        // Steps at about 400, 480 and 560 ms; the next would be at 640.
        repeat(600) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(listOf("change:22.5", "change:25.0", "change:27.5"), log, "while held")
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(200) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(listOf("change:22.5", "change:25.0", "change:27.5", "finished"), log)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun semanticActionStepsAndFinishes() = runComposeUiTest {
        val log = mutableListOf<String>()
        loggingStepper(log)
        onNodeWithTag(StepperDecreaseTag).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(listOf("change:17.5", "finished"), log)
    }

    /** A scroll that starts on a button makes no step, so nothing is finished either. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aScrollDoesNotFinish() = runComposeUiTest {
        val changes = mutableListOf<Double>()
        var finished = 0
        var value by mutableStateOf(20.0)
        withTheme {
            Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(100.dp))
                Stepper(
                    value, { value = it; changes += it }, step = 2.5, min = 0.0, display = "$value",
                    decreaseLabel = "Decrease", increaseLabel = "Increase", onValueChangeFinished = { finished++ },
                )
                Spacer(Modifier.height(2000.dp))
            }
        }
        waitForIdle()
        onNodeWithTag(StepperIncreaseTag).performTouchInput { swipeUp(startY = centerY, endY = centerY - 60.dp.toPx(), durationMillis = 100) }
        waitForIdle()
        assertEquals(emptyList(), changes)
        assertEquals(0, finished)
    }

    /** Pre-flight F6: the value stops at [max], where the increase button is disabled. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun maxDisablesIncreaseAndClampsTheLastStep() = runComposeUiTest {
        val log = mutableListOf<String>()
        loggingStepper(log, start = 299.0, max = 300.0)
        onNodeWithTag(StepperIncreaseTag).assertIsEnabled()
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(listOf("change:300.0", "finished"), log)
        onNodeWithTag(StepperIncreaseTag).assertIsNotEnabled()
        onNodeWithTag(StepperDecreaseTag).assertIsEnabled()
    }

    /** Pre-flight F6: the whole stepper has a name and can take the focus ("Editing load of set 4"). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun groupHasItsNameAndTakesFocus() = runComposeUiTest {
        val focus = FocusRequester()
        withTheme {
            Stepper(
                85.0, {}, step = 2.5, min = 0.0, decreaseLabel = "Decrease", increaseLabel = "Increase",
                focusRequester = focus, contentDescription = "Editing load of set 4",
            )
        }
        waitForIdle()
        runOnUiThread { focus.requestFocus() }
        waitForIdle()
        onNodeWithContentDescription("Editing load of set 4").assertIsFocused()
        onNodeWithContentDescription("Increase").assertExists()
        onNodeWithContentDescription("Decrease").assertExists()
    }
}
