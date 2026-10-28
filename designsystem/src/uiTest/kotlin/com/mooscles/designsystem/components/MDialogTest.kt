package com.mooscles.designsystem.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MDialogTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun confirmIsTheLastActionAndCallsConfirm() = runComposeUiTest {
        var confirmed = 0
        var dismissed = 0
        withTheme {
            MDialog(
                title = "Finish?",
                text = "Saved to history.",
                confirmLabel = "Finish",
                onConfirm = { confirmed++ },
                onDismiss = { dismissed++ },
                dismissLabel = "Keep going",
            )
        }
        waitForIdle()
        val dismissBounds = onNodeWithTag(DialogDismissTag).getBoundsInRoot()
        val confirmBounds = onNodeWithTag(DialogConfirmTag).getBoundsInRoot()
        assertTrue(confirmBounds.left >= dismissBounds.right, "the confirmation is at the end")
        onNodeWithTag(DialogConfirmTag).performClick()
        waitForIdle()
        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backAndScrimDismiss() {
        runComposeUiTest {
            var dismissed = 0
            withTheme { MDialog("Finish?", "Saved.", "Finish", onConfirm = {}, onDismiss = { dismissed++ }) }
            waitForIdle()
            onNodeWithTag(DialogTag).performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertEquals(1, dismissed, "the back/escape key dismisses")
        }
        runComposeUiTest {
            var dismissed = 0
            withTheme { MDialog("Finish?", "Saved.", "Finish", onConfirm = {}, onDismiss = { dismissed++ }) }
            waitForIdle()
            onNodeWithTag(DialogScrimTag).performTouchInput { click(Offset(2f, 2f)) }
            waitForIdle()
            assertEquals(1, dismissed, "a tap on the scrim dismisses")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun destructiveUsesTheFilledDangerButton() = runComposeUiTest {
        withTheme { MDialog("Delete?", "Gone.", "Delete", onConfirm = {}, onDismiss = {}, destructive = true) }
        waitForIdle()
        onNodeWithTag(DialogConfirmDangerTag).assertExists()
        onNodeWithTag(DialogConfirmTag).assertDoesNotExist()
    }
}
