package com.mooscles.designsystem.components

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedbackTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun errorBannerIsAPoliteLiveRegionWithIcon() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                withTheme(dark = dark) { ErrorBanner("Could not save", Modifier.testTag("banner")) }
                waitForIdle()
                val config = onNodeWithTag("banner").fetchSemanticsNode().config
                assertEquals(LiveRegionMode.Polite, config.getOrNull(SemanticsProperties.LiveRegion))
                onNodeWithTag(ErrorBannerIconTag, useUnmergedTree = true).assertExists()
                onNodeWithText("Could not save").assertExists()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun emptyStateHasOnePrimaryAction() = runComposeUiTest {
        var started = 0
        withTheme {
            EmptyState(MIcons.FileText, "No workouts", "Finish one", primaryAction = "Start" to { started++ })
        }
        waitForIdle()
        onNodeWithText("No workouts").assertExists()
        onNodeWithText("Start").performClick()
        assertEquals(1, started)
        // The secondary action is optional and absent, so there is exactly one button.
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun skeletonIsStillWithReducedMotion() = runComposeUiTest {
        mainClock.autoAdvance = false
        withTheme(reducedMotion = true) { Skeleton(Modifier.testTag("s")) }
        waitForIdle()
        val before = onNodeWithTag("s").fetchSemanticsNode().config.getOrNull(SkeletonAlpha)
        mainClock.advanceTimeBy(com.mooscles.designsystem.tokens.MoosclesTiming().skeletonPulse)
        val after = onNodeWithTag("s").fetchSemanticsNode().config.getOrNull(SkeletonAlpha)
        assertEquals(before, after)
        assertEquals(1f, after)
    }
}
