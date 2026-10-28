package com.mooscles.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.KeyMoment
import com.mooscles.designsystem.interaction.MotionChoice
import com.mooscles.designsystem.interaction.MotionPolicy
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_complete_set_n
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The "set done" check: a 44 dp disc in a 56 dp target, a checkbox labelled "Complete set N".
 * Checked it is filled with a check mark (an empty ring otherwise), so the state is not by color
 * alone. Checking plays the `SetCompleted` key moment (a spring, or a fade with reduced motion).
 * With [haptics] it gives `SetCompleted`/`SetUnchecked`; [SetRow] turns it off and fires them from
 * the row status, so a personal record can replace the plain one.
 */
@Composable
fun SetCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    setNumber: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptics: Boolean = true,
) {
    val colors = MTheme.colors
    val feedback = MTheme.haptics
    val source = remember { MutableInteractionSource() }
    val label = stringResource(Res.string.ds_complete_set_n, setNumber)
    val reduced = MTheme.reducedMotion
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        val becameChecked = checked && !previous
        previous = checked
        if (!becameChecked) return@LaunchedEffect
        when (val choice = MotionPolicy.keyMoment(KeyMoment.SetCompleted, reduced)) {
            is MotionChoice.Spring -> {
                scale.snapTo(choice.fromScale)
                scale.animateTo(1f, spring(choice.tokens.dampingRatio, choice.tokens.stiffness))
            }
            is MotionChoice.Fade -> {
                alpha.snapTo(0f)
                alpha.animateTo(1f, tween(choice.durationMs))
            }
        }
    }
    val fill = when {
        !checked -> colors.bgSurface
        enabled -> colors.accentSolid
        else -> colors.textDisabled
    }
    val outline = when {
        checked -> fill
        enabled -> colors.borderStrong
        else -> colors.borderSubtle
    }
    Box(
        modifier
            .sizeIn(minWidth = MTheme.targets.workout, minHeight = MTheme.targets.workout)
            .mPressScale(source, enabled)
            .semantics { contentDescription = label }
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = {
                    if (haptics) feedback.perform(if (it) HapticEvent.SetCompleted else HapticEvent.SetUnchecked)
                    onCheckedChange(it)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(MTheme.sizes.checkDisc)
                .mFocusRing(CircleShape, source)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .background(fill, CircleShape)
                .border(MTheme.sizes.border, outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) MIcon(MIcons.Check, contentDescription = null, size = MTheme.sizes.icon24, tint = colors.accentOn)
        }
    }
}
