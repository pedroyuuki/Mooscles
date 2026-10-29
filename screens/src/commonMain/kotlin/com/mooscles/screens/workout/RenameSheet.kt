package com.mooscles.screens.workout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MTextField
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_menu_rename
import com.mooscles.screens.resources.workout_rename_field
import com.mooscles.screens.resources.workout_save
import org.jetbrains.compose.resources.stringResource

/** The test tags of the rename sheet. */
internal object RenameSheetTags {
    const val FIELD = "workout-rename-field"
    const val SAVE = "workout-rename-save"
}

/**
 * "Rename workout" (plan decision 16): an `MBottomSheet` with the name in an `MTextField`, which
 * starts as [name], and "Save". The `MDialog` has no field, hence the sheet. Saving a name that
 * is not blank calls [onRename] with it trimmed, unless it is the same name, and closes; "Save"
 * is off while the field is blank; the keyboard's Done saves the same way. What is typed survives
 * recreation.
 */
@Composable
internal fun RenameSheet(name: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(name, selection = TextRange(name.length)))
    }
    var saved by remember { mutableStateOf(false) }
    val trimmed = value.text.trim()
    val save: () -> Unit = {
        if (!saved && trimmed.isNotEmpty()) {
            saved = true
            if (trimmed != name) onRename(trimmed)
            onDismiss()
        }
    }
    val space = MTheme.space
    MBottomSheet(onDismiss = onDismiss) {
        MText(
            stringResource(Res.string.workout_menu_rename),
            MTheme.type.titleMd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.s4)
                .padding(bottom = space.s2)
                .semantics { heading() },
        )
        MTextField(
            value = value,
            onValueChange = { value = it },
            label = stringResource(Res.string.workout_rename_field),
            modifier = Modifier.fillMaxWidth().padding(horizontal = space.s4).testTag(RenameSheetTags.FIELD),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            singleLine = true,
        )
        MButton(
            stringResource(Res.string.workout_save),
            onClick = save,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.s4)
                .padding(top = space.s4, bottom = space.s6)
                .testTag(RenameSheetTags.SAVE),
            variant = MButtonVariant.Primary,
            size = MButtonSize.L,
            enabled = trimmed.isNotEmpty(),
        )
    }
}
