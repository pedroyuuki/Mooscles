package com.mooscles.designsystem.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mooscles.designsystem.gallery.pages.FoundationsPage
import com.mooscles.designsystem.gallery.pages.IconsPage
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_page_foundations
import com.mooscles.designsystem.resources.gallery_page_icons
import org.jetbrains.compose.resources.StringResource

/** The pages of the design gallery. Each batch of components adds its own entry. */
enum class GalleryPage(val title: StringResource) {
    Foundations(Res.string.gallery_page_foundations),
    Icons(Res.string.gallery_page_icons),
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
        }
    }
}
