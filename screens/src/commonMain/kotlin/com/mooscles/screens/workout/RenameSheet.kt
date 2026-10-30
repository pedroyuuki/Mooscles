package com.mooscles.screens.workout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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

/** For how many frames the sheet keeps asking for the focus (about half a second). */
private const val FOCUS_FRAMES = 30

/** The test tags of the rename sheet. */
internal object RenameSheetTags {
    const val FIELD = "workout-rename-field"
    const val SAVE = "workout-rename-save"
}

/**
 * A name in an `MBottomSheet` (plan decision 15): the field starts as [name] and the button is [confirmLabel]. It began
 * as "Rename workout" (the defaults, phase 3 decision 16) and also serves a new or renamed routine and a new or
 * renamed folder, with their [title], [confirmLabel] and [fieldLabel]. The `MDialog` has no field, hence the sheet.
 * Saving a name that is not blank calls [onRename] with it trimmed, unless it is the same name (a rename that changes
 * nothing writes nothing), and closes; with [submitUnchanged] the same name is submitted too, which a "New …" sheet
 * needs, since its default name is the name to create. The button is off while the field is blank; the keyboard's Done
 * saves the same way. With [selectAll] the name starts selected and the field takes the focus (the keyboard opens), so
 * typing replaces it. What is typed survives recreation.
 */
@Composable
internal fun RenameSheet(
    name: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(Res.string.workout_menu_rename),
    confirmLabel: String = stringResource(Res.string.workout_save),
    fieldLabel: String = stringResource(Res.string.workout_rename_field),
    selectAll: Boolean = false,
    submitUnchanged: Boolean = false,
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(name, selection = if (selectAll) TextRange(0, name.length) else TextRange(name.length)))
    }
    var saved by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val trimmed = value.text.trim()
    val save: () -> Unit = {
        if (!saved && trimmed.isNotEmpty()) {
            saved = true
            if (submitUnchanged || trimmed != name) onRename(trimmed)
            onDismiss()
        }
    }
    val space = MTheme.space
    MBottomSheet(onDismiss = onDismiss) {
        if (selectAll) {
            // In the sheet's own window composition, after the sheet has taken the focus for itself (it does, on
            // its first frame): ask for the field's focus again whenever it is not there, for a few frames.
            LaunchedEffect(Unit) {
                repeat(FOCUS_FRAMES) {
                    withFrameNanos { }
                    if (!focused) runCatching { focus.requestFocus() }
                }
            }
        }
        MText(
            title,
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
            label = fieldLabel,
            modifier = Modifier.fillMaxWidth().padding(horizontal = space.s4).focusRequester(focus).onFocusChanged { focused = it.isFocused }.testTag(RenameSheetTags.FIELD),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            singleLine = true,
        )
        MButton(
            confirmLabel,
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
