package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.padding
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_error_prefix
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** The tag of the visible box of a [NumericField]. */
const val NumericFieldBoxTag = "numeric-field-box"

/**
 * A number entered during a workout: 72 x 48 dp (at least; it grows with the number and the
 * text size, never clipping it), `num.md`, a decimal or number keyboard by [kind]. The target is
 * 56 dp tall. While empty it shows [suggested] in `text.secondary`. The label is
 * [accessibilityLabel] on the field's own semantics (there is no visible label); an [error] adds
 * a 2 dp danger border, the alert icon and the message, and is also reported as `error`.
 * Text that cannot lead to a number of [kind] is not passed to [onValueChange].
 */
@Composable
fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    kind: NumericKind,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    suggested: String? = null,
    error: String? = null,
    enabled: Boolean = true,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(MTheme.radius.md)
    val hasError = error != null
    val errorPrefix = stringResource(Res.string.ds_error_prefix)
    val textColor = if (enabled) colors.textPrimary else colors.textDisabled
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = value,
            onValueChange = { if (NumericInput.isTypable(it, kind)) onValueChange(it) },
            modifier = Modifier.semantics {
                contentDescription = accessibilityLabel
                if (error != null) error("$errorPrefix: $error")
            },
            enabled = enabled,
            singleLine = true,
            textStyle = MTheme.type.numMd.copy(color = textColor, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (kind == NumericKind.Load) KeyboardType.Decimal else KeyboardType.Number,
            ),
            interactionSource = source,
            cursorBrush = SolidColor(colors.accentText),
            decorationBox = { inner ->
                // The outer box is the 56 dp target; the drawn box inside it is 48 dp tall.
                Box(
                    Modifier.sizeIn(minWidth = MTheme.sizes.numericFieldWidth, minHeight = MTheme.targets.workout),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .testTag(NumericFieldBoxTag)
                            .sizeIn(minWidth = MTheme.sizes.numericFieldWidth, minHeight = MTheme.sizes.numericFieldHeight)
                            .mFocusRing(shape, source)
                            .background(if (enabled) colors.bgSurface else colors.bgInset, shape)
                            .border(
                                if (hasError) MTheme.sizes.borderError else MTheme.sizes.border,
                                if (hasError) colors.dangerText else colors.borderStrong,
                                shape,
                            )
                            .padding(horizontal = MTheme.space.s2),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (value.isEmpty() && suggested != null) {
                            MText(suggested, MTheme.type.numMd, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics {})
                        }
                        inner()
                    }
                }
            },
        )
        if (error != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1), verticalAlignment = Alignment.Top) {
                MIcon(MIcons.CircleAlert, contentDescription = null, size = MTheme.sizes.icon16, tint = colors.dangerText)
                MText(error, MTheme.type.caption, color = colors.dangerText)
            }
        }
    }
}
