package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.theme.MTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.withTimeoutOrNull

const val StepperDecreaseTag = "stepper-decrease"
const val StepperIncreaseTag = "stepper-increase"
const val StepperValueTag = "stepper-value"

/**
 * A value changed in steps of [step] with a button on each side (64 x 56 dp each, `num.lg` value
 * between). A short tap is one step on release; holding is one step after `timing.stepperRepeatDelay`
 * and then one every `timing.stepperRepeatInterval`, and releasing adds nothing. Moving the finger past
 * touch slop, off the button or into a scroll cancels the press (no step, no more repeats), so a scroll
 * that starts on a button never changes the value. Every step gives the
 * `StepperStep` haptic. The value stays within [min] and [max], where the decrease or the increase
 * button is disabled.
 *
 * [onValueChangeFinished] is called once when a run of steps ends: after the step of a tap, a key
 * press or a screen reader's activation, and when the finger lifts (or the press is cancelled)
 * after a hold, however many steps it made. A press that made no step does not call it. A caller
 * that saves on every step can instead save only here.
 *
 * [contentDescription] names the whole stepper ("Editing load of set 4"); with [focusRequester]
 * the stepper itself is focusable, so the caller can move focus to it (its buttons stay focusable
 * on their own). [display] is the text of [value]; by default the domain's plain decimal
 * ([MoosclesNumbers.plain], `20`, `82.5`) with the separators of [numberLocale] (`82,5` in pt-BR),
 * never `20.0`.
 */
@Composable
fun Stepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    step: Double,
    min: Double,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    numberLocale: NumberLocale = currentNumberLocale(),
    display: String = MoosclesNumbers.localize(MoosclesNumbers.plain(value), numberLocale),
    max: Double = Double.POSITIVE_INFINITY,
    onValueChangeFinished: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    contentDescription: String? = null,
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val haptics = MTheme.haptics
    // A step; true when it changed the value. The value the caller gets is always within min..max.
    val change: (Int) -> Boolean = { direction ->
        val next = (currentValue + direction * step).coerceAtMost(max).coerceAtLeast(min)
        if (next != currentValue) {
            haptics.perform(HapticEvent.StepperStep)
            currentOnChange(next)
            true
        } else {
            false
        }
    }
    val finished = { currentOnFinished() }
    var group: Modifier = Modifier
    if (contentDescription != null) group = group.semantics { this.contentDescription = contentDescription }
    if (focusRequester != null) group = group.focusRequester(focusRequester).focusable()
    Row(
        modifier.then(group),
        horizontalArrangement = Arrangement.spacedBy(MTheme.targets.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(MIcons.Minus, decreaseLabel, enabled = value > min, tag = StepperDecreaseTag, step = { change(-1) }, finished = finished)
        MText(display, MTheme.type.numLg, modifier = Modifier.testTag(StepperValueTag))
        StepperButton(MIcons.Plus, increaseLabel, enabled = value < max, tag = StepperIncreaseTag, step = { change(1) }, finished = finished)
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tag: String,
    step: () -> Boolean,
    finished: () -> Unit,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val currentStep by rememberUpdatedState(step)
    val currentFinished by rememberUpdatedState(finished)
    val repeatDelay = MTheme.timing.stepperRepeatDelay
    val repeatInterval = MTheme.timing.stepperRepeatInterval
    val shape = RoundedCornerShape(MTheme.radius.md)
    Box(
        Modifier
            .testTag(tag)
            .sizeIn(minWidth = MTheme.sizes.stepperButtonWidth, minHeight = MTheme.sizes.stepperButtonHeight)
            .mPressScale(source, enabled)
            .semantics {
                contentDescription = label
                role = Role.Button
                if (enabled) onClick { if (currentStep()) currentFinished(); true } else disabled()
            }
            .onKeyEvent {
                // One step per press, on key up; Enter, NumPad Enter and Space.
                val activates = it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Spacebar
                if (enabled && activates && it.type == KeyEventType.KeyUp) {
                    if (currentStep()) currentFinished()
                    true
                } else {
                    false
                }
            }
            .focusable(enabled, source)
            .pointerInput(enabled, repeatDelay, repeatInterval) {
                if (!enabled) return@pointerInput
                // The timing starts at the finger going down and ends synchronously when it lifts,
                // not at a recomposition. The press ends without a step (and stops repeating) when
                // the finger moves past touch slop, leaves the button, or a scrolling parent
                // consumes the movement: a scroll that starts on a button never changes the value.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val press = PressInteraction.Press(down.position)
                    source.tryEmit(press)
                    var released = false
                    // Whether this press changed the value: then it ends with onValueChangeFinished, once.
                    var stepped = false
                    try {
                        val first = withTimeoutOrNull(repeatDelay) { awaitReleaseOrCancel(down) }
                        if (first != null) {
                            if (first) {
                                released = true
                                stepped = currentStep()
                            }
                        } else {
                            stepped = currentStep()
                            while (true) {
                                val end = withTimeoutOrNull(repeatInterval) { awaitReleaseOrCancel(down) }
                                if (end == null) {
                                    stepped = currentStep() || stepped
                                } else {
                                    released = end
                                    break
                                }
                            }
                        }
                    } finally {
                        source.tryEmit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        if (stepped) currentFinished()
                    }
                }
            }
            .mFocusRing(shape, source)
            .background(if (enabled) colors.bgSurface else colors.bgInset, shape)
            .border(MTheme.sizes.border, if (enabled) colors.borderStrong else colors.borderSubtle, shape),
        contentAlignment = Alignment.Center,
    ) {
        MIcon(icon, contentDescription = null, size = MTheme.sizes.icon24, tint = if (enabled) colors.textPrimary else colors.textDisabled)
    }
}

/**
 * Suspends until the press ends: `true` when every pointer lifted on the button, `false` when it is
 * cancelled because a pointer moved past touch slop from [down], left the button, or had its
 * movement consumed by another node (a scrolling parent consumes on the Main pass, after this
 * child, so that is checked on the Final pass). A consumed down (a clickable parent) does not cancel.
 */
private suspend fun AwaitPointerEventScope.awaitReleaseOrCancel(down: PointerInputChange): Boolean {
    val slop = viewConfiguration.touchSlop
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.fastAll { !it.pressed }) {
            return event.changes.fastAll { !it.isOutOfBounds(size, extendedTouchPadding) }
        }
        val cancelled = event.changes.fastAny {
            it.isOutOfBounds(size, extendedTouchPadding) ||
                (it.id == down.id && (it.position - down.position).getDistance() > slop) ||
                (it.isConsumed && it.positionChangedIgnoreConsumed())
        }
        if (cancelled) return false
        val final = awaitPointerEvent(PointerEventPass.Final)
        if (final.changes.fastAny { it.isConsumed && it.positionChangedIgnoreConsumed() }) return false
    }
}
