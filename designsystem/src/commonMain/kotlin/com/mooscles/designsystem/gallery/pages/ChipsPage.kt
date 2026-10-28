package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_sample_chip
import com.mooscles.designsystem.resources.gallery_section_chip_kinds
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** Each chip kind, unselected, selected and disabled. */
@Composable
fun ChipsPage() {
    val label = stringResource(Res.string.gallery_sample_chip)
    var selected by remember { mutableStateOf(MChipKind.Filter) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
    ) {
        SectionHeader(stringResource(Res.string.gallery_section_chip_kinds))
        for (kind in MChipKind.entries) {
            Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2), verticalAlignment = Alignment.CenterVertically) {
                MChip(label, selected = selected == kind, onClick = { selected = kind }, kind = kind)
                MChip(label, selected = selected != kind, onClick = { selected = kind }, kind = kind)
                MChip(label, selected = false, onClick = {}, kind = kind, enabled = false)
            }
        }
    }
}
