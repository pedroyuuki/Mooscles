package com.mooscles.designsystem.spike

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.components.MTextField
import com.mooscles.designsystem.components.SpikeLabelStrategy
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TextFieldEditSpikeTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyStrategyStaysEditable() {
        for (strategy in SpikeLabelStrategy.entries) {
            runComposeUiTest {
                var value by mutableStateOf(TextFieldValue(""))
                withTheme {
                    MTextField(
                        value, { value = it }, "Default rest duration",
                        labelStrategy = strategy, singleLine = true,
                    )
                }
                waitForIdle()
                val field = onNode(hasSetTextAction())
                assertNotNull(field.fetchSemanticsNode().config.getOrNull(SemanticsActions.SetText), "$strategy SetText")
                field.performTextInput("2:30")
                waitForIdle()
                assertEquals("2:30", value.text, "$strategy value")
                assertEquals("2:30", field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text, "$strategy EditableText")
                assertNotNull(field.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnImeAction), "$strategy ImeAction action")
            }
        }
    }
}
