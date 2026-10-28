package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_duration_and
import com.mooscles.designsystem.resources.ds_minutes
import com.mooscles.designsystem.resources.ds_seconds
import com.mooscles.designsystem.resources.ds_timer_announce
import com.mooscles.designsystem.resources.ds_timer_done
import com.mooscles.designsystem.resources.ds_timer_minus15
import com.mooscles.designsystem.resources.ds_timer_minus15_short
import com.mooscles.designsystem.resources.ds_timer_paused
import com.mooscles.designsystem.resources.ds_timer_plus15
import com.mooscles.designsystem.resources.ds_timer_plus15_short
import com.mooscles.designsystem.resources.ds_timer_skip
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

const val TimerDockTag = "timer-dock"
const val TimerTimeTag = "timer-time"
const val TimerMinusTag = "timer-minus15"
const val TimerPlusTag = "timer-plus15"
const val TimerSkipTag = "timer-skip"
const val TimerAnnounceTag = "timer-announce"

/** From this font scale on, the dock always stacks its buttons under the time (it also stacks when they do not fit beside it). */
private const val STACK_FONT_SCALE = 1.5f

/** A tick is the clock moving down by up to this many seconds; anything else is an adjustment. */
private const val TICK_SECONDS = 2

/** The widest time the dock shows, so the layout is chosen from a value that does not change every second. */
private const val DOCK_WIDEST_TIME = "00:00"

/** The text of the spoken duration, "1 minute and 24 seconds". Zero stays plural. */
@Composable
internal fun spokenDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 && seconds > 0 -> stringResource(
            Res.string.ds_duration_and,
            pluralCount(Res.plurals.ds_minutes, minutes),
            pluralCount(Res.plurals.ds_seconds, seconds),
        )
        minutes > 0 -> pluralCount(Res.plurals.ds_minutes, minutes)
        else -> pluralCount(Res.plurals.ds_seconds, seconds)
    }
}

/** Remembers what was last announced so that only a start, an adjustment or the end changes it. */
private class Announcement {
    private var phase: TimerPhase? = null
    private var remaining = Int.MIN_VALUE
    var text = ""
        private set

    fun update(visual: RestTimerVisual, fresh: String): String {
        val ticked = phase == TimerPhase.Running && visual.phase == TimerPhase.Running &&
            remaining - visual.remainingSeconds in 0..TICK_SECONDS
        val unchanged = phase == visual.phase && (visual.phase != TimerPhase.Running || remaining == visual.remainingSeconds)
        if (!ticked && !unchanged) text = fresh
        phase = visual.phase
        remaining = visual.remainingSeconds
        return text
    }
}

/**
 * The live-region text of a timer. It changes when the timer starts, when ±15 s or a skip moves the
 * clock, and when it ends, never on an ordinary tick, so a screen reader is not read to every second.
 */
@Composable
internal fun rememberTimerAnnouncement(visual: RestTimerVisual): String {
    val holder = remember { Announcement() }
    val fresh = when (visual.phase) {
        TimerPhase.Done -> stringResource(Res.string.ds_timer_done)
        TimerPhase.Paused -> stringResource(Res.string.ds_timer_paused)
        TimerPhase.Running -> stringResource(Res.string.ds_timer_announce, spokenDuration(visual.remainingSeconds))
    }
    return holder.update(visual, fresh)
}

/** The 1 dp node that carries the polite live region: it adds nothing to the layout. */
@Composable
internal fun TimerAnnouncer(text: String) {
    Box(
        Modifier
            .size(MTheme.sizes.border)
            .testTag(TimerAnnounceTag)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}

/** The status line of a timer: the end, the pause, or its name. */
@Composable
internal fun timerStatus(visual: RestTimerVisual): String = when (visual.phase) {
    TimerPhase.Done -> stringResource(Res.string.ds_timer_done)
    TimerPhase.Paused -> stringResource(Res.string.ds_timer_paused)
    TimerPhase.Running -> visual.label
}

/** The ±15 s button, 56 dp at least (a disc of `timer.disc` in the expanded timer). [description] is its label. */
@Composable
internal fun TimerAdjustButton(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MTheme.radius.md),
    size: Dp = MTheme.targets.workout,
    square: Boolean = false,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .then(if (square) Modifier.size(size) else Modifier.sizeIn(minWidth = size, minHeight = size))
            .mPressScale(source, true)
            .semantics { contentDescription = description }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .mFocusRing(shape, source)
            .background(colors.bgRaised, shape)
            .border(MTheme.sizes.border, colors.borderStrong, shape)
            .padding(horizontal = MTheme.space.s3),
        contentAlignment = Alignment.Center,
    ) {
        MText(text, MTheme.type.labelLg)
    }
}

/**
 * The rest timer docked above the bottom bar: a 48 dp ring, the name, the time (`num.dock`,
 * tabular, one line, never cut), then -15, +15 and skip, each 56 dp. The buttons stack under the
 * time from a font scale of 1.5, or sooner when the measured width of the widest time plus the
 * buttons does not fit the dock. When the phase turns [TimerPhase.Done] it gives the `RestFinished`
 * haptic once and says "Rest complete"; how long that stays up is the caller's (3 s,
 * `timing.timerDoneHold`). A hidden polite live region speaks the start, each adjustment and the
 * end, not every second. The component is presentational and owns no clock: the caller passes a new
 * [visual] each second, which recomposes the dock (the ring sweep is read while drawing).
 */
@Composable
fun RestTimerDock(
    visual: RestTimerVisual,
    onMinus15: () -> Unit,
    onPlus15: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MTheme.colors
    val haptics = MTheme.haptics
    val density = LocalDensity.current
    val progress = rememberRingProgress(visual)
    val announcement = rememberTimerAnnouncement(visual)
    val status = timerStatus(visual)
    val done = visual.phase == TimerPhase.Done
    LaunchedEffect(done) { if (done) haptics.perform(HapticEvent.RestFinished) }
    val minus = { haptics.perform(HapticEvent.StepperStep); onMinus15() }
    val plus = { haptics.perform(HapticEvent.StepperStep); onPlus15() }
    val minusLabel = stringResource(Res.string.ds_timer_minus15)
    val plusLabel = stringResource(Res.string.ds_timer_plus15)
    val minusShort = stringResource(Res.string.ds_timer_minus15_short)
    val plusShort = stringResource(Res.string.ds_timer_plus15_short)
    val skipLabel = stringResource(Res.string.ds_timer_skip)
    val shape = RoundedCornerShape(MTheme.radius.lg)

    val measurer = rememberTextMeasurer()
    val direction = LocalLayoutDirection.current
    val timeStyle = MTheme.type.numDock
    val buttonStyle = MTheme.type.labelLg
    val padding = MTheme.space.s3
    val ring = MTheme.sizes.dockRing
    val target = MTheme.targets.workout
    val gap = MTheme.targets.gap
    val needed = with(density) {
        fun width(text: String, style: androidx.compose.ui.text.TextStyle) =
            measurer.measure(text, style, softWrap = false, maxLines = 1, layoutDirection = direction, density = density).size.width.toDp()
        val time = width(DOCK_WIDEST_TIME, timeStyle)
        val adjust = maxOf(target, maxOf(width(minusShort, buttonStyle), width(plusShort, buttonStyle)) + padding * 2)
        padding * 2 + ring + padding * 2 + time + adjust * 2 + target + gap * 2
    }

    BoxWithConstraints(modifier.testTag(TimerDockTag)) {
        val fits = !constraints.hasBoundedWidth || maxWidth >= needed
        val stacked = density.fontScale >= STACK_FONT_SCALE || !fits
        val buttons: @Composable (Modifier) -> Unit = { each ->
            TimerAdjustButton(minusShort, minusLabel, minus, each.testTag(TimerMinusTag))
            TimerAdjustButton(plusShort, plusLabel, plus, each.testTag(TimerPlusTag))
        }
        Column(
            Modifier.fillMaxWidth().background(colors.bgRaised, shape).padding(padding),
            verticalArrangement = Arrangement.spacedBy(padding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(padding)) {
                RestTimerRing({ progress.value }, ring, MTheme.sizes.dockRingStroke, ticks = false) {
                    MIcon(
                        if (done) MIcons.CircleCheck else MIcons.Timer,
                        contentDescription = null,
                        size = MTheme.sizes.icon20,
                        tint = if (done) colors.timerProgress else colors.textSecondary,
                    )
                }
                Column(Modifier.weight(1f)) {
                    MText(status, MTheme.type.labelMd, color = colors.textSecondary)
                    MText(
                        MoosclesNumbers.clock(visual.remainingSeconds),
                        timeStyle,
                        modifier = Modifier.testTag(TimerTimeTag),
                        softWrap = false,
                        maxLines = 1,
                    )
                }
                if (!stacked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        buttons(Modifier)
                        MIconButton(
                            MIcons.SkipForward, skipLabel, onSkip,
                            modifier = Modifier.testTag(TimerSkipTag),
                            size = MTheme.sizes.iconButton56,
                        )
                    }
                }
            }
            if (stacked) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    buttons(Modifier.fillMaxWidth())
                    MButton(
                        skipLabel, onSkip,
                        modifier = Modifier.fillMaxWidth().testTag(TimerSkipTag),
                        variant = MButtonVariant.Tonal,
                        size = MButtonSize.L,
                        icon = MIcons.SkipForward,
                    )
                }
            }
        }
        TimerAnnouncer(announcement)
    }
}
