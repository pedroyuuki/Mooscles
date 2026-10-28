package com.mooscles.designsystem.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.tokens.MoosclesSizes
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
        onNodeWithTag(TimerMinusTag).assertContentDescriptionEquals("Subtract 15 seconds")
        onNodeWithTag(TimerPlusTag).assertContentDescriptionEquals("Add 15 seconds")
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
}
