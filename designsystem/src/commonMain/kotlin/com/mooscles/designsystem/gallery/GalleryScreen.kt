package com.mooscles.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_back
import com.mooscles.designsystem.resources.gallery_title
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The gallery: a list of pages and the open page. Back closes the page, then the gallery.
 * Controls arrive with the component tasks.
 */
@Composable
fun GalleryScreen(onBack: () -> Unit, initialPage: GalleryPage? = null) {
    var open by remember { mutableStateOf(initialPage) }
    Column(Modifier.fillMaxSize().background(MTheme.colors.bgBase)) {
        Row(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = MTheme.targets.min)
                .clickable(role = Role.Button) { if (open == null) onBack() else open = null }
                .padding(horizontal = MTheme.space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                stringResource(Res.string.gallery_back),
                style = MTheme.type.labelLg.copy(color = MTheme.colors.accentText),
            )
        }
        val page = open
        if (page == null) {
            Column(
                Modifier.padding(horizontal = MTheme.space.s4),
                verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
            ) {
                BasicText(
                    stringResource(Res.string.gallery_title),
                    style = MTheme.type.titleLg.copy(color = MTheme.colors.textPrimary),
                )
                for (entry in GalleryPage.entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = MTheme.targets.min)
                            .testTag(galleryEntryTag(entry))
                            .clickable(role = Role.Button) { open = entry },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            stringResource(entry.title),
                            style = MTheme.type.titleSm.copy(color = MTheme.colors.textPrimary),
                        )
                    }
                }
            }
        } else {
            page.Content()
        }
    }
}
