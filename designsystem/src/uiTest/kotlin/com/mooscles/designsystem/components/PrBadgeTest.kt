package com.mooscles.designsystem.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.withTheme
import kotlin.test.Test

class PrBadgeTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun isReadAsPersonalRecord() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { PrBadge() } }
        waitForIdle()
        onNodeWithTag(PrBadgeTag).assertContentDescriptionEquals("personal record")
        onNodeWithText("PR", useUnmergedTree = true).assertExists()
    }
}
