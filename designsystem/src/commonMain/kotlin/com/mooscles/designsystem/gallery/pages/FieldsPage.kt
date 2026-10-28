package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.components.MTextField
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_sample_error
import com.mooscles.designsystem.resources.gallery_sample_help
import com.mooscles.designsystem.resources.gallery_sample_label
import com.mooscles.designsystem.resources.gallery_sample_row
import com.mooscles.designsystem.resources.gallery_sample_suffix
import com.mooscles.designsystem.resources.gallery_section_text_fields
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** The text field, full width: empty, with help, with an error, with a suffix and disabled. */
@Composable
fun FieldsPage() {
    val label = stringResource(Res.string.gallery_sample_label)
    val help = stringResource(Res.string.gallery_sample_help)
    val error = stringResource(Res.string.gallery_sample_error)
    var plain by remember { mutableStateOf(TextFieldValue("")) }
    var withHelp by remember { mutableStateOf(TextFieldValue("")) }
    var invalid by remember { mutableStateOf(TextFieldValue("")) }
    var withSuffix by remember { mutableStateOf(TextFieldValue("2:00")) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s4),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_text_fields))
        // Every sample at the full width, as fields are used in a form.
        val wide = Modifier.fillMaxWidth()
        MTextField(plain, { plain = it }, label, wide)
        MTextField(withHelp, { withHelp = it }, label, wide, help = help)
        MTextField(invalid, { invalid = it }, label, wide, error = error)
        MTextField(withSuffix, { withSuffix = it }, stringResource(Res.string.gallery_sample_row), wide, suffix = stringResource(Res.string.gallery_sample_suffix))
        MTextField(TextFieldValue(label), {}, label, wide, enabled = false)
    }
}
