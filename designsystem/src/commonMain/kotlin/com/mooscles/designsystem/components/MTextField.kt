package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_error_prefix
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** The tag of the field's box, which grows with the text. */
const val TextFieldBoxTag = "text-field-box"

/** The tag of the error icon, present only while [MTextField] has an error. */
const val TextFieldErrorIconTag = "text-field-error-icon"

/**
 * A text field. The border is `border.strong`, or 2 dp `danger.text` with an error, which is
 * always shown as the `CircleAlert` icon plus the message so color is never the only signal.
 * The label sits above the box and is read with the help (or error) text by the editable node.
 * The box is at least `numericFieldHeight` tall and grows with the text, so it never clips.
 */
@Composable
fun MTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    help: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onFocusChange: (Boolean) -> Unit = {},
    textStyle: TextStyle = MTheme.type.bodyLg,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)
    LaunchedEffect(focused) { currentOnFocusChange(focused) }
    val shape = RoundedCornerShape(MTheme.radius.md)
    val hasError = error != null
    val borderWidth = if (hasError) MTheme.sizes.borderError else MTheme.sizes.border
    val borderColor = if (hasError) colors.dangerText else colors.borderStrong
    val errorPrefix = stringResource(Res.string.ds_error_prefix)
    val described = error?.let { "$errorPrefix: $it" } ?: help

    Column(modifier, verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
        MText(label, MTheme.type.labelMd, color = if (enabled) colors.textSecondary else colors.textDisabled)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = textStyle.copy(color = if (enabled) colors.textPrimary else colors.textDisabled),
            keyboardOptions = keyboardOptions,
            interactionSource = source,
            cursorBrush = SolidColor(colors.accentText),
            modifier = Modifier.semantics {
                contentDescription = label
                if (described != null) stateDescription = described
            },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .testTag(TextFieldBoxTag)
                        .heightIn(min = MTheme.sizes.numericFieldHeight)
                        .mFocusRing(shape, source)
                        .background(if (enabled) colors.bgSurface else colors.bgInset, shape)
                        .border(borderWidth, borderColor, shape)
                        .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
        if (hasError) {
            Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2), verticalAlignment = Alignment.Top) {
                MIcon(
                    MIcons.CircleAlert,
                    contentDescription = null,
                    size = MTheme.sizes.icon16,
                    tint = colors.dangerText,
                    modifier = Modifier.testTag(TextFieldErrorIconTag).padding(top = MTheme.space.s1),
                )
                MText(error, MTheme.type.caption, color = colors.dangerText)
            }
        } else if (help != null) {
            MText(help, MTheme.type.caption, color = colors.textSecondary)
        }
    }
}
