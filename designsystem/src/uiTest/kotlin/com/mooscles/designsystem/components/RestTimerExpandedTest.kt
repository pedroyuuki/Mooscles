package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import kotlin.test.assertEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class RestTimerExpandedTest {
    private val visual = RestTimerVisual(84, 120, TimerPhase.Running, "Rest")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun discsAreEightyAndLabelled() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { RestTimerExpanded(visual, null, {}, {}, {}) } }
        waitForIdle()
        val disc = with(density) { MoosclesSizes().timerDisc.toPx() }
        for (tag in listOf(TimerMinusTag, TimerPlusTag)) {
            val touch = onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            assertTrue(kotlin.math.abs(touch.width - disc) < 1f && kotlin.math.abs(touch.height - disc) < 1f, "$tag ${touch.width} x ${touch.height} != $disc")
        }
        onNodeWithTag(TimerMinusTag).assertContentDescriptionEquals("Minus 15 seconds")
        onNodeWithTag(TimerPlusTag).assertContentDescriptionEquals("Plus 15 seconds")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsUpNextWhenGiven() = runComposeUiTest {
        val next: String? = "Incline press"
        withTheme { ProvideAppLocale("en") { RestTimerExpanded(visual, next, {}, {}, {}) } }
        waitForIdle()
        onNodeWithTag(TimerUpNextTag).assertExists()
        onNodeWithText("Incline press", useUnmergedTree = true).assertExists()
        onNodeWithText("Up next", useUnmergedTree = true).assertExists()
        onNodeWithText("Skip rest", useUnmergedTree = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hidesUpNextWhenAbsent() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { RestTimerExpanded(visual, null, {}, {}, {}) } }
        waitForIdle()
        onNodeWithTag(TimerUpNextTag).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun plusAndMinusTick() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var minus = 0
        var plus = 0
        withTheme(haptics = haptics) { RestTimerExpanded(visual, null, { minus++ }, { plus++ }, {}) }
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
    fun timeFitsInsideTheRingAtTwoHundredPercent() {
        for (scale in listOf(1f, 1.3f, 2f)) {
            runComposeUiTest {
                withTheme(fontScale = scale) {
                    Box(Modifier.width(360.dp)) { RestTimerExpanded(RestTimerVisual(600, 900, TimerPhase.Running, "Rest"), null, {}, {}, {}) }
                }
                waitForIdle()
                val results = mutableListOf<TextLayoutResult>()
                val node = onNodeWithTag(TimerTimeTag, useUnmergedTree = true).fetchSemanticsNode()
                val action = node.config[SemanticsActions.GetTextLayoutResult].action
                assertTrue(action != null && action(results), "no text layout result")
                assertShownWhole(results.first(), "scale $scale")
                val ring = onNodeWithTag(TimerRingTag).fetchSemanticsNode().boundsInRoot
                val time = node.boundsInRoot
                val clear = with(density) { (MoosclesSizes().expandedRing - (MoosclesSizes().tickLength + 4.dp + MoosclesSizes().expandedRingStroke) * 2).toPx() }
                assertTrue(time.width <= clear + 1f, "scale $scale: time ${time.width} wider than the clear ring $clear")
                assertTrue(time.left >= ring.left && time.right <= ring.right, "scale $scale: time outside the ring")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun discLabelsAreShownWholeAtTwoHundredPercent() = runComposeUiTest {
        withTheme(fontScale = 2f) {
            ProvideAppLocale("en") {
                Box(Modifier.width(360.dp)) { RestTimerExpanded(visual, null, {}, {}, {}) }
            }
        }
        waitForIdle()
        val disc = with(density) { MoosclesSizes().timerDisc.toPx() }
        for ((tag, label) in listOf(TimerMinusTag to "\u221215 s", TimerPlusTag to "+15 s")) {
            val results = mutableListOf<TextLayoutResult>()
            val node = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val action = node.config[SemanticsActions.GetTextLayoutResult].action
            assertTrue(action != null && action(results), "no text layout result for $label")
            assertShownWhole(results.first(), tag)
            val touch = onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            assertTrue(touch.width >= disc - 0.5f && touch.height >= disc - 0.5f, "$tag ${touch.width} x ${touch.height} smaller than $disc")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun restFinishedOnlyOnTheTurnToDoneNotWhenShownAlreadyDone() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var state by mutableStateOf(RestTimerVisual(0, 120, TimerPhase.Done, "Rest"))
        withTheme(haptics = haptics) { RestTimerExpanded(state, null, {}, {}, {}) }
        waitForIdle()
        assertEquals(emptyList(), haptics.events, "shown already done")
        state = RestTimerVisual(30, 120, TimerPhase.Running, "Rest")
        waitForIdle()
        state = RestTimerVisual(0, 120, TimerPhase.Done, "Rest")
        waitForIdle()
        assertEquals(listOf(HapticEvent.RestFinished), haptics.events)
    }
}
