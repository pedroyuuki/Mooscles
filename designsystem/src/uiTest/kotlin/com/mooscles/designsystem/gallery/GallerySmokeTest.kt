package com.mooscles.designsystem.gallery

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.withTheme
import kotlin.test.Test

class GallerySmokeTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyPageRendersInBothThemes() {
        for (dark in listOf(false, true)) {
            for (page in GalleryPage.entries) {
                runComposeUiTest {
                    withTheme(dark = dark) { page.Content() }
                    waitForIdle()
                    onNodeWithTag(galleryPageTag(page)).assertExists("${page.name} dark=$dark")
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theListOpensAPage() {
        runComposeUiTest {
            withTheme { GalleryScreen(onBack = {}) }
            waitForIdle()
            onNodeWithTag(galleryEntryTag(GalleryPage.Foundations)).assertExists().performClick()
            waitForIdle()
            onNodeWithTag(galleryPageTag(GalleryPage.Foundations)).assertExists()
        }
    }
}
