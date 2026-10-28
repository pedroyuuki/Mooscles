package com.mooscles.designsystem.i18n

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_licenses_title
import com.mooscles.designsystem.withTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import org.jetbrains.compose.resources.stringResource

class AppLocaleTest {
    /** Puts the system language back, so a failed test cannot leak a language into the next one. */
    @OptIn(ExperimentalTestApi::class)
    @AfterTest
    fun restoreSystemLanguage() {
        runComposeUiTest {
            withTheme { ProvideAppLocale(null) { } }
            waitForIdle()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun providingPortugueseResolvesPortugueseStrings() {
        runComposeUiTest {
            withTheme { ProvideAppLocale("pt-BR") { BasicText(stringResource(Res.string.ds_licenses_title)) } }
            waitForIdle()
            onNodeWithText("Licenças de código aberto").assertExists()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun providingNullRestoresTheSystemLanguage() {
        runComposeUiTest {
            var tag by mutableStateOf<String?>("pt-BR")
            withTheme { ProvideAppLocale(tag) { BasicText(stringResource(Res.string.ds_licenses_title)) } }
            waitForIdle()
            onNodeWithText("Licenças de código aberto").assertExists()
            tag = "en"
            waitForIdle()
            onNodeWithText("Open-source licenses").assertExists()
            tag = null
            waitForIdle()
            // The emulator and the simulator run in English, so the system language is English.
            onNodeWithText("Open-source licenses").assertExists()
        }
    }
}
