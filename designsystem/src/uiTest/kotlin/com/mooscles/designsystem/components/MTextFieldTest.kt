package com.mooscles.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MTextFieldTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun errorShowsIconAndMessageNotOnlyColor() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                withTheme(dark = dark) { MTextField(TextFieldValue(""), {}, "Name", error = "Enter a name") }
                waitForIdle()
                onNodeWithTag(TextFieldErrorIconTag, useUnmergedTree = true).assertExists()
                onNode(hasText("Enter a name"), useUnmergedTree = true).assertExists()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun typedValueIsTheEditableTextAndErrorIsReported() = runComposeUiTest {
        withTheme { MTextField(TextFieldValue("Squat"), {}, "Name", error = "Enter a name") }
        waitForIdle()
        val config = onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertEquals("Squat", config.getOrNull(SemanticsProperties.EditableText)?.text)
        assertEquals("Error: Enter a name", config.getOrNull(SemanticsProperties.Error))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun focusCallbackDoesNotFireOnFirstComposition() = runComposeUiTest {
        val calls = mutableListOf<Boolean>()
        withTheme { MTextField(TextFieldValue(""), {}, "Name", onFocusChange = { calls += it }) }
        waitForIdle()
        assertEquals(emptyList(), calls)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun disabledIsNotEditable() = runComposeUiTest {
        withTheme { MTextField(TextFieldValue("x"), {}, "Name", enabled = false) }
        waitForIdle()
        onNode(hasSetTextAction()).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun labelAndHelpAreReadWithTheField() = runComposeUiTest {
        withTheme { MTextField(TextFieldValue(""), {}, "Name", help = "Shown in your library") }
        waitForIdle()
        val config = onNode(hasSetTextAction()).fetchSemanticsNode().config
        val texts = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertTrue("Name" in texts && "Shown in your library" in texts, "texts: $texts")
        assertNull(config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull(config.getOrNull(SemanticsProperties.Error))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun grownHeightAtTwoHundredPercentWithoutClipping() = runComposeUiTest {
        withTheme(fontScale = 2f) {
            MTextField(TextFieldValue("Barbell"), {}, "Name", modifier = Modifier.testTag("f"))
        }
        waitForIdle()
        val box = onNodeWithTag(TextFieldBoxTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val minPx = with(density) { MoosclesSizes().numericFieldHeight.toPx() }
        assertTrue(box.height >= minPx, "box ${box.height} < min $minPx")
        val text = onNode(hasText("Barbell"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(box.height >= text.height, "box ${box.height} clips text ${text.height}")
        assertTrue(box.height > minPx, "the box did not grow with 200% text")
    }
}
