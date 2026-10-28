package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.withTheme
import kotlin.test.Test

class MChipTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun selectedShowsACheckIconNotOnlyColor() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                var selected by mutableStateOf(false)
                withTheme(dark = dark) { MChip("Chest", selected = selected, onClick = {}) }
                waitForIdle()
                onNodeWithTag(ChipCheckTag, useUnmergedTree = true).assertDoesNotExist()
                runOnUiThread { selected = true }
                waitForIdle()
                onNodeWithTag(ChipCheckTag, useUnmergedTree = true).assertExists()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyKindExposesItsRoleAndState() = runComposeUiTest {
        var selected by mutableStateOf(false)
        withTheme {
          androidx.compose.foundation.layout.Column {
            MChip("Filter", selected, onClick = { selected = !selected }, kind = MChipKind.Filter, modifier = Modifier.testTag("filter"))
            MChip("Set", selected, onClick = {}, kind = MChipKind.SetType, modifier = Modifier.testTag("set"))
            MChip("Period", selected, onClick = {}, kind = MChipKind.Segmented, modifier = Modifier.testTag("seg"))
          }
        }
        waitForIdle()
        fun role(tag: String, role: Role) = onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, role))
        role("filter", Role.Checkbox)
        role("set", Role.RadioButton)
        role("seg", Role.Tab)
        onNodeWithTag("filter").assertIsOff()
        onNodeWithTag("set").assertIsNotSelected()
        onNodeWithTag("seg").assertIsNotSelected()
        onNodeWithTag("filter").performClick()
        waitForIdle()
        onNodeWithTag("filter").assertIsOn()
        onNodeWithTag("set").assertIsSelected()
        onNodeWithTag("seg").assertIsSelected()
    }
}
