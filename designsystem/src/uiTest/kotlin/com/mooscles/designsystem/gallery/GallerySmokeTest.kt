package com.mooscles.designsystem.gallery

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_section_buttons
import com.mooscles.designsystem.resources.gallery_section_chip_kinds
import com.mooscles.designsystem.resources.gallery_section_colors
import com.mooscles.designsystem.resources.gallery_section_icons
import com.mooscles.designsystem.resources.gallery_section_overlays
import com.mooscles.designsystem.resources.gallery_section_rows
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

class GallerySmokeTest {
    /** A string each page shows once rendered. Every new page must add its entry here. */
    private val knownText: Map<GalleryPage, StringResource> = mapOf(
        GalleryPage.Foundations to Res.string.gallery_section_colors,
        GalleryPage.Icons to Res.string.gallery_section_icons,
        GalleryPage.Buttons to Res.string.gallery_section_buttons,
        GalleryPage.Chips to Res.string.gallery_section_chip_kinds,
        GalleryPage.Rows to Res.string.gallery_section_rows,
        GalleryPage.Overlays to Res.string.gallery_section_overlays,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyPageRendersInBothThemes() {
        assertEquals(GalleryPage.entries.toSet(), knownText.keys, "Add a known text for the new page")
        for (dark in listOf(false, true)) {
            for (page in GalleryPage.entries) {
                runComposeUiTest {
                    withTheme(dark = dark) { page.Content() }
                    waitForIdle()
                    onNodeWithText(getString(knownText.getValue(page))).assertExists("${page.name} dark=$dark")
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theListShowsEveryTitleAndOpensAPage() {
        runComposeUiTest {
            withTheme { GalleryScreen(onBack = {}) }
            waitForIdle()
            for (page in GalleryPage.entries) onNodeWithText(getString(page.title)).assertExists(page.name)
            onNodeWithTag(galleryEntryTag(GalleryPage.Foundations)).performClick()
            waitForIdle()
            onNodeWithText(getString(knownText.getValue(GalleryPage.Foundations))).assertExists()
        }
    }
}
