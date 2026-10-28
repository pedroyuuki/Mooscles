package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
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
}
