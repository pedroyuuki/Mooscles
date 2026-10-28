package com.mooscles.designsystem.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mooscles.designsystem.gallery.pages.ButtonsPage
import com.mooscles.designsystem.gallery.pages.ChipsPage
import com.mooscles.designsystem.gallery.pages.FoundationsPage
import com.mooscles.designsystem.gallery.pages.IconsPage
import com.mooscles.designsystem.gallery.pages.RowsPage
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_page_buttons
import com.mooscles.designsystem.resources.gallery_page_chips
import com.mooscles.designsystem.resources.gallery_page_foundations
import com.mooscles.designsystem.resources.gallery_page_icons
import com.mooscles.designsystem.resources.gallery_page_rows
import org.jetbrains.compose.resources.StringResource

/** The pages of the design gallery. Each batch of components adds its own entry. */
enum class GalleryPage(val title: StringResource) {
    Foundations(Res.string.gallery_page_foundations),
    Icons(Res.string.gallery_page_icons),
    Buttons(Res.string.gallery_page_buttons),
    Chips(Res.string.gallery_page_chips),
    Rows(Res.string.gallery_page_rows),
}

fun galleryPageTag(page: GalleryPage): String = "gallery-page-${page.name}"

fun galleryEntryTag(page: GalleryPage): String = "gallery-entry-${page.name}"

/** The page body, tagged so tests can find it. */
@Composable
fun GalleryPage.Content() {
    Box(Modifier.testTag(galleryPageTag(this))) {
        when (this@Content) {
            GalleryPage.Foundations -> FoundationsPage()
            GalleryPage.Icons -> IconsPage()
            GalleryPage.Buttons -> ButtonsPage()
            GalleryPage.Chips -> ChipsPage()
            GalleryPage.Rows -> RowsPage()
        }
    }
}
