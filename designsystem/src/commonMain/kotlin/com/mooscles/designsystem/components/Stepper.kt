package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.theme.MTheme
import kotlinx.coroutines.delay

const val StepperDecreaseTag = "stepper-decrease"
const val StepperIncreaseTag = "stepper-increase"
const val StepperValueTag = "stepper-value"

/**
 * A value changed in steps of [step] with a button on each side (64 x 56 dp each, `num.lg` value
 * between). A short tap is one step on release; holding is one step after `timing.stepperRepeatDelay`
 * and then one every `timing.stepperRepeatInterval`, and releasing adds nothing. Every step gives the
 * `StepperStep` haptic. The value never goes below [min], where the decrease button is disabled.
 * [display] is the already localized text of [value].
 */
@Composable
fun Stepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    step: Double,
    min: Double,
    display: String,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val haptics = MTheme.haptics
    val change: (Int) -> Unit = { direction ->
        val next = (currentValue + direction * step).coerceAtLeast(min)
        if (next != currentValue) {
            haptics.perform(HapticEvent.StepperStep)
            currentOnChange(next)
        }
    }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(MTheme.targets.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(MIcons.Minus, decreaseLabel, enabled = value > min, tag = StepperDecreaseTag) { change(-1) }
        MText(display, MTheme.type.numLg, modifier = Modifier.testTag(StepperValueTag))
        StepperButton(MIcons.Plus, increaseLabel, enabled = true, tag = StepperIncreaseTag) { change(1) }
    }
}

/** Not a plain click: the same press that waits for the hold also ends in a click, which the hold swallows. */
private class HoldState {
    var repeated = false
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tag: String,
    step: () -> Unit,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hold = remember { HoldState() }
    val currentStep by rememberUpdatedState(step)
    val repeatDelay = MTheme.timing.stepperRepeatDelay
    val repeatInterval = MTheme.timing.stepperRepeatInterval
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            hold.repeated = false
            delay(repeatDelay)
            hold.repeated = true
            currentStep()
            while (true) {
                delay(repeatInterval)
                currentStep()
            }
        }
    }
    val shape = RoundedCornerShape(MTheme.radius.md)
    Box(
        Modifier
            .testTag(tag)
            .sizeIn(minWidth = MTheme.sizes.stepperButtonWidth, minHeight = MTheme.sizes.stepperButtonHeight)
            .mPressScale(source, enabled)
            .semantics { contentDescription = label }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button) {
                if (!hold.repeated) currentStep()
                hold.repeated = false
            }
            .mFocusRing(shape, source)
            .background(if (enabled) colors.bgSurface else colors.bgInset, shape)
            .border(MTheme.sizes.border, if (enabled) colors.borderStrong else colors.borderSubtle, shape),
        contentAlignment = Alignment.Center,
    ) {
        MIcon(icon, contentDescription = null, size = MTheme.sizes.icon24, tint = if (enabled) colors.textPrimary else colors.textDisabled)
    }
}
