package com.mooscles.designsystem.components

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The clock is driven by hand. The timer starts on the first frame that shows the toast, so
 * times are measured from after two frames (at most 32 ms late) and checked with a margin of
 * one more frame; `motion.fast` is the exit animation.
 */
@OptIn(ExperimentalTestApi::class)
class MToastTest {
    private class Run(val state: MToastHostState = MToastHostState()) {
        var result: MToastResult? = null
    }

    private fun ComposeUiTest.start(run: Run, message: String, kind: MToastKind = MToastKind.Info, action: String? = null) {
        mainClock.autoAdvance = false
        withTheme {
            LaunchedEffect(Unit) { run.result = run.state.show(message, kind, action) }
            MToastHost(run.state, Modifier.fillMaxSize())
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithTag(ToastTag).assertExists()
    }

    @Test
    fun infoDismissesAfterFourSeconds() = runComposeUiTest {
        val run = Run()
        start(run, "Saved")
        val timing = MoosclesTiming()
        mainClock.advanceTimeBy(timing.toastInfo - 100)
        onNodeWithTag(ToastTag).assertExists()
        assertNull(run.result)
        mainClock.advanceTimeBy(100 + MoosclesMotion().fast + 100L)
        onNodeWithTag(ToastTag).assertDoesNotExist()
        assertEquals(MToastResult.Dismissed, run.result)
    }

    @Test
    fun actionToastLastsSixSecondsAndReportsAction() {
        runComposeUiTest {
            val run = Run()
            start(run, "Set removed", action = "Undo")
            val timing = MoosclesTiming()
            mainClock.advanceTimeBy(timing.toastInfo + 500)
            onNodeWithTag(ToastTag).assertExists()
            mainClock.advanceTimeBy(timing.toastAction - timing.toastInfo - 500 - 100)
            onNodeWithTag(ToastTag).assertExists()
            mainClock.advanceTimeBy(100 + MoosclesMotion().fast + 100L)
            onNodeWithTag(ToastTag).assertDoesNotExist()
            assertEquals(MToastResult.Dismissed, run.result)
        }
        runComposeUiTest {
            val run = Run()
            start(run, "Set removed", action = "Undo")
            onNodeWithTag(ToastActionTag).performClick()
            mainClock.advanceTimeBy(MoosclesMotion().fast + 200L)
            assertEquals(MToastResult.ActionPerformed, run.result)
            onNodeWithTag(ToastTag).assertDoesNotExist()
        }
    }

    @Test
    fun toastIsAPoliteLiveRegionWithTheMessage() = runComposeUiTest {
        start(Run(), "Saved")
        onNodeWithTag(ToastTag).assertTextContains("Saved")
        onNodeWithTag(ToastTag).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun errorToastStaysUntilClosed() = runComposeUiTest {
        val run = Run()
        start(run, "Could not save", kind = MToastKind.Error)
        mainClock.advanceTimeBy(60_000)
        onNodeWithTag(ToastTag).assertExists()
        assertNull(run.result)
        onNodeWithTag(ToastCloseTag).performClick()
        mainClock.advanceTimeBy(MoosclesMotion().fast + 200L)
        onNodeWithTag(ToastTag).assertDoesNotExist()
        assertEquals(MToastResult.Dismissed, run.result)
    }

    @Test
    fun errorToastShowsTheAlertIconAndInfoDoesNot() {
        runComposeUiTest {
            start(Run(), "Could not save", kind = MToastKind.Error)
            onNodeWithTag(ToastErrorIconTag, useUnmergedTree = true).assertContentDescriptionEquals("Error")
            onNodeWithTag(ToastTag).assertTextContains("Could not save")
        }
        runComposeUiTest {
            start(Run(), "Saved")
            onNodeWithTag(ToastErrorIconTag, useUnmergedTree = true).assertDoesNotExist()
        }
    }
}
