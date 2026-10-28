package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RestTimerDockTest {
    private fun visual(seconds: Int, phase: TimerPhase = TimerPhase.Running, total: Int = 120) =
        RestTimerVisual(seconds, total, phase, "Rest")

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.announcement(): String =
        onNodeWithTag(TimerAnnounceTag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun timeIsTabularAndNeverEllipsized() = runComposeUiTest {
        withTheme(fontScale = 2f) {
            Box(Modifier.width(320.dp)) { RestTimerDock(visual(600), {}, {}, {}) }
        }
        waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        val node = onNodeWithTag(TimerTimeTag, useUnmergedTree = true).fetchSemanticsNode()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results), "no text layout result")
        val layout = results.first()
        assertEquals("tnum", layout.layoutInput.style.fontFeatureSettings)
        assertShownWhole(layout)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun announcementChangesOnlyOnStartAdjustAndFinish() = runComposeUiTest {
        var state by mutableStateOf(visual(84))
        withTheme { ProvideAppLocale("en") { RestTimerDock(state, {}, {}, {}) } }
        waitForIdle()
        val start = announcement()
        assertEquals("Rest, 1 minute and 24 seconds remaining", start)
        state = visual(83)
        waitForIdle()
        assertEquals(start, announcement())
        state = visual(82)
        waitForIdle()
        assertEquals(start, announcement())
        state = visual(97)
        waitForIdle()
        val adjusted = announcement()
        assertNotEquals(start, adjusted)
        state = visual(0, TimerPhase.Done)
        waitForIdle()
        assertNotEquals(adjusted, announcement())
        assertEquals("Rest complete", announcement())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun finishFiresRestFinishedOnce() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var state by mutableStateOf(visual(1))
        withTheme(haptics = haptics) { RestTimerDock(state, {}, {}, {}) }
        waitForIdle()
        assertEquals(emptyList(), haptics.events)
        state = visual(0, TimerPhase.Done)
        waitForIdle()
        state = visual(0, TimerPhase.Done, total = 121)
        waitForIdle()
        assertEquals(listOf(HapticEvent.RestFinished), haptics.events)
    }

    /** Switching between the dock and the expanded timer during the done hold does not buzz again. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun switchingViewsWhileDoneDoesNotRepeatRestFinished() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var state by mutableStateOf(visual(1))
        var expanded by mutableStateOf(false)
        withTheme(haptics = haptics) {
            if (expanded) RestTimerExpanded(state, null, {}, {}, {}) else RestTimerDock(state, {}, {}, {})
        }
        waitForIdle()
        state = visual(0, TimerPhase.Done)
        waitForIdle()
        expanded = true
        waitForIdle()
        expanded = false
        waitForIdle()
        assertEquals(listOf(HapticEvent.RestFinished), haptics.events)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun plusAndMinusTick() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var minus = 0
        var plus = 0
        withTheme(haptics = haptics) { RestTimerDock(visual(84), { minus++ }, { plus++ }, {}) }
        waitForIdle()
        onNodeWithTag(TimerMinusTag).performClick()
        onNodeWithTag(TimerPlusTag).performClick()
        waitForIdle()
        assertEquals(1, minus)
        assertEquals(1, plus)
        assertEquals(listOf(HapticEvent.StepperStep, HapticEvent.StepperStep), haptics.events)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun actionsMeetTheWorkoutTarget() = runComposeUiTest {
        withTheme { RestTimerDock(visual(84), {}, {}, {}) }
        waitForIdle()
        val target = with(density) { MoosclesTargets().workout.toPx() }
        for (tag in listOf(TimerMinusTag, TimerPlusTag, TimerSkipTag)) {
            val touch = onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            assertTrue(touch.height >= target - 0.5f && touch.width >= target - 0.5f, "$tag ${touch.width} x ${touch.height} < $target")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun buttonsStackAtOneAndAHalf() {
        runComposeUiTest {
            withTheme(fontScale = 1f) { Box(Modifier.width(360.dp)) { RestTimerDock(visual(84), {}, {}, {}) } }
            waitForIdle()
            val minus = onNodeWithTag(TimerMinusTag).fetchSemanticsNode().boundsInRoot
            val plus = onNodeWithTag(TimerPlusTag).fetchSemanticsNode().boundsInRoot
            assertEquals(minus.top, plus.top, "side by side at 1.0")
        }
        runComposeUiTest {
            withTheme(fontScale = 1.5f) { Box(Modifier.width(360.dp)) { RestTimerDock(visual(84), {}, {}, {}) } }
            waitForIdle()
            val minus = onNodeWithTag(TimerMinusTag).fetchSemanticsNode().boundsInRoot
            val plus = onNodeWithTag(TimerPlusTag).fetchSemanticsNode().boundsInRoot
            val skip = onNodeWithTag(TimerSkipTag).fetchSemanticsNode().boundsInRoot
            assertEquals(minus.left, plus.left, "same column")
            assertTrue(plus.top >= minus.bottom - 0.5f, "plus under minus")
            assertTrue(skip.top >= plus.bottom - 0.5f, "skip under plus")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun timeNeverWrapsOrClipsAtAnyScaleOrWidth() {
        for (width in listOf(360, 320)) {
            for (scale in listOf(1f, 1.3f, 1.4f, 1.5f)) {
                runComposeUiTest {
                    withTheme(fontScale = scale) { Box(Modifier.width(width.dp)) { RestTimerDock(visual(600), {}, {}, {}) } }
                    waitForIdle()
                    val results = mutableListOf<TextLayoutResult>()
                    val node = onNodeWithTag(TimerTimeTag, useUnmergedTree = true).fetchSemanticsNode()
                    val action = node.config[SemanticsActions.GetTextLayoutResult].action
                    assertTrue(action != null && action(results), "no text layout result")
                    val where = "width $width scale $scale"
                    assertShownWhole(results.first(), where)
                    val dock = onNodeWithTag(TimerDockTag).fetchSemanticsNode().boundsInRoot
                    val time = node.boundsInRoot
                    assertTrue(time.left >= dock.left && time.right <= dock.right, "$where: time outside the dock")
                    // The buttons never leave the dock either.
                    for (tag in listOf(TimerMinusTag, TimerPlusTag, TimerSkipTag)) {
                        val button = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                        assertTrue(button.right <= dock.right + 1f, "$where: $tag outside the dock")
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun visibleLabelsAreInTheAccessibleNames() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { RestTimerDock(visual(84), {}, {}, {}) } }
        waitForIdle()
        onNodeWithTag(TimerMinusTag).assertContentDescriptionEquals("Minus 15 seconds")
        onNodeWithTag(TimerPlusTag).assertContentDescriptionEquals("Plus 15 seconds")
        onNodeWithText("\u221215 s", useUnmergedTree = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pausedShowsThePauseIconAndRunningDoesNot() {
        for ((phase, paused) in listOf(TimerPhase.Paused to true, TimerPhase.Running to false, TimerPhase.Done to false)) {
            runComposeUiTest {
                withTheme { ProvideAppLocale("en") { RestTimerDock(visual(60, phase), {}, {}, {}) } }
                waitForIdle()
                val icon = onNodeWithTag(TimerPausedIconTag, useUnmergedTree = true)
                if (paused) icon.assertExists() else icon.assertDoesNotExist()
            }
        }
    }
}
