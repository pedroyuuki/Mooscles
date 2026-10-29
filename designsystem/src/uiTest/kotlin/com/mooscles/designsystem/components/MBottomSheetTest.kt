package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MBottomSheetTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scrimTapAndBackClose() {
        runComposeUiTest {
            var dismissed = 0
            withTheme { MBottomSheet(onDismiss = { dismissed++ }) { MText("Sheet", MTheme.type.bodyLg) } }
            waitForIdle()
            onNodeWithTag(SheetScrimTag).performTouchInput { click(Offset(2f, 2f)) }
            waitForIdle()
            assertEquals(1, dismissed, "a tap on the scrim closes")
        }
        runComposeUiTest {
            var dismissed = 0
            withTheme { MBottomSheet(onDismiss = { dismissed++ }) { MText("Sheet", MTheme.type.bodyLg) } }
            waitForIdle()
            onNodeWithTag(SheetTag).performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertEquals(1, dismissed, "back/escape closes")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun heightIsCappedAtNinetyPercent() = runComposeUiTest {
        withTheme {
            MBottomSheet(onDismiss = {}) { Spacer(Modifier.fillMaxWidth().height(4_000.dp)) }
        }
        waitForIdle()
        val screen = onNodeWithTag(SheetScrimTag).getBoundsInRoot().let { (it.bottom - it.top).value }
        val sheet = onNodeWithTag(SheetTag).getBoundsInRoot().let { (it.bottom - it.top).value }
        assertTrue(sheet <= screen * 0.9f + 1f, "sheet $sheet of screen $screen")
        assertTrue(sheet >= screen * 0.9f - 1f, "tall content fills the cap: sheet $sheet of screen $screen")
    }

    /**
     * The scrim is clickable, so it is named ("Dismiss", as Material's "Close sheet"): a screen
     * reader never lands on a nameless control. It is read after the content (traversal index 1).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scrimIsNamedDismissAndReadLast() = runComposeUiTest {
        withTheme { MBottomSheet(onDismiss = {}) { MText("Sheet", MTheme.type.bodyLg) } }
        waitForIdle()
        onNodeWithTag(SheetScrimTag).assertContentDescriptionEquals("Dismiss")
        assertEquals(1f, onNodeWithTag(SheetScrimTag).fetchSemanticsNode().config[SemanticsProperties.TraversalIndex])
    }
}
