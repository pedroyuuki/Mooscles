package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumericFieldTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun isLabelledAndReportsItsErrorOnItsOwnNode() = runComposeUiTest {
        withTheme { NumericField("85", {}, NumericKind.Load, "Load", error = "Too heavy") }
        waitForIdle()
        val config = onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertEquals("85", config.getOrNull(SemanticsProperties.EditableText)?.text)
        assertEquals(listOf("Load"), config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals("Error: Too heavy", config.getOrNull(SemanticsProperties.Error))
        onNode(androidx.compose.ui.test.hasText("Too heavy"), useUnmergedTree = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun targetIsFiftySixAndTheBoxSeventyTwoByFortyEight() = runComposeUiTest {
        withTheme { NumericField("", {}, NumericKind.Reps, "Reps") }
        waitForIdle()
        val touch = onNode(hasSetTextAction()).fetchSemanticsNode().touchBoundsInRoot
        val target = with(density) { MoosclesTargets().workout.toPx() }
        assertTrue(touch.height >= target - 0.5f, "touch ${touch.height} < $target")
        val box = onNodeWithTag(NumericFieldBoxTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val sizes = MoosclesSizes()
        assertEquals(with(density) { sizes.numericFieldWidth.toPx() }, box.width, 0.5f)
        assertEquals(with(density) { sizes.numericFieldHeight.toPx() }, box.height, 0.5f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onlyTextThatCanBecomeANumberIsPassedOn() = runComposeUiTest {
        var text by mutableStateOf("")
        withTheme { NumericField(text, { text = it }, NumericKind.Load, "Load") }
        waitForIdle()
        onNode(hasSetTextAction()).performTextInput("82,5")
        waitForIdle()
        assertEquals("82,5", text)
        onNode(hasSetTextAction()).performTextInput("kg")
        waitForIdle()
        assertEquals("82,5", text)
    }
}
