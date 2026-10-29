package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_timer_rest
import com.mooscles.designsystem.resources.ds_timer_close
import com.mooscles.designsystem.resources.ds_timer_minus15
import com.mooscles.designsystem.resources.ds_timer_minus15_short
import com.mooscles.designsystem.resources.ds_timer_plus15
import com.mooscles.designsystem.resources.ds_timer_of
import com.mooscles.designsystem.resources.ds_timer_pause
import com.mooscles.designsystem.resources.ds_timer_plus15_short
import com.mooscles.designsystem.resources.ds_timer_restart
import com.mooscles.designsystem.resources.ds_timer_resume
import com.mooscles.designsystem.resources.ds_timer_skip
import com.mooscles.designsystem.resources.ds_timer_up_next
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.tabular
import org.jetbrains.compose.resources.stringResource

const val TimerUpNextTag = "timer-up-next"
const val TimerExpandedTag = "timer-expanded"
const val TimerRingTag = "timer-ring"
const val TimerCloseTag = "timer-close"
const val TimerTitleTag = "timer-title"
const val TimerTotalTag = "timer-total"
const val TimerPauseResumeTag = "timer-pause-resume"
const val TimerRestartTag = "timer-restart"
const val TimerDoneCheckTag = "timer-done-check"

/** The widest time the expanded timer shows; the size of the digits is chosen for it. */
private const val EXPANDED_WIDEST_TIME = "00:00"

/**
 * The share of the ring's clear middle the phase over the time may take; a longer one ("Descanso
 * concluído" at 2x) wraps. Over the time, that width's corners stay inside the clear circle.
 */
private const val RING_STATUS_SHARE = 0.7f

/**
 * The full-screen rest timer. At the top, "Close timer" (`ChevronDown`, [onClose]) and the [title]
 * ("Rest · Bench press · set 4 of 5"); below, a 280 dp plate ring with 60 rim marks, the time in
 * `num.display` and "of 2:00" ([totalText], the full rest; none, no line). The controls sit in the
 * bottom half: the -15 and +15 buttons as 80 dp discs (wider with large text, so the label stays
 * whole; +15 disabled without [canAdd], the 5:00 ceiling), the exercise that comes next ([upNext],
 * under the component's own "Up next"), "Skip rest" (tonal), "Pause"/"Resume" ([onPauseResume],
 * secondary; disabled once done) and "Restart" ([onRestart], text). It fills the height it is given
 * and scrolls when the content is taller (large text). Same phases, haptics and live region as
 * [RestTimerDock]; presentational too. Done, a check takes the time's place under "Rest complete",
 * in the full ring. Over the time, the ring names only the phase ("Rest", "Paused", "Rest
 * complete"), never [RestTimerVisual.label]: the exercise is in the [title]. The time stays on one line inside the clear middle of the ring: when the font
 * scale would make it wider, it shrinks to fit, but never below `num.lg`.
 */
@Composable
fun RestTimerExpanded(
    visual: RestTimerVisual,
    upNext: String?,
    onMinus15: () -> Unit,
    onPlus15: () -> Unit,
    onSkip: () -> Unit,
    title: String,
    totalText: String?,
    onPauseResume: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    canAdd: Boolean = true,
) {
    val colors = MTheme.colors
    val haptics = MTheme.haptics
    val density = LocalDensity.current
    val space = MTheme.space
    val progress = rememberRingProgress(visual)
    val announcement = rememberTimerAnnouncement(visual)
    // Inside the ring, only the phase (board "Descanso e recorde"): the title already names the
    // exercise, and a dock label ("Rest · Bench press") is wider than the ring's clear middle.
    val status = when (visual.phase) {
        TimerPhase.Running -> stringResource(Res.string.ds_timer_rest)
        else -> timerStatus(visual)
    }
    val done = visual.phase == TimerPhase.Done
    val paused = visual.phase == TimerPhase.Paused
    RestFinishedHaptic(done)
    val minusLabel = stringResource(Res.string.ds_timer_minus15)
    val plusLabel = stringResource(Res.string.ds_timer_plus15)
    val minusShort = stringResource(Res.string.ds_timer_minus15_short)
    val plusShort = stringResource(Res.string.ds_timer_plus15_short)
    val skipLabel = stringResource(Res.string.ds_timer_skip)
    val closeLabel = stringResource(Res.string.ds_timer_close)
    val toggleLabel = stringResource(if (paused) Res.string.ds_timer_resume else Res.string.ds_timer_pause)
    val restartLabel = stringResource(Res.string.ds_timer_restart)
    val total = totalText?.let { stringResource(Res.string.ds_timer_of, it) }
    val disc = MTheme.sizes.timerDisc

    val measurer = rememberTextMeasurer()
    val direction = LocalLayoutDirection.current
    val sizes = MTheme.sizes
    val full = MTheme.type.numDisplay
    val inner = sizes.expandedRing - (sizes.tickLength + MTheme.space.s1 + sizes.expandedRingStroke) * 2
    // The shrink is worked out on the drawn size (dp), not on sp: with Android's nonlinear font
    // scaling a smaller sp grows by a larger factor, so scaling the sp would not shrink the width
    // by the same share.
    val timeStyle = with(density) {
        val smallest = MTheme.type.numLg.fontSize.toDp() / full.fontSize.toDp()
        val width = measurer.measure(
            EXPANDED_WIDEST_TIME, full, softWrap = false, maxLines = 1, layoutDirection = direction, density = density,
        ).size.width.toDp()
        val fit = (inner / width).coerceIn(smallest, 1f)
        if (fit < 1f) {
            full.copy(fontSize = (full.fontSize.toDp() * fit).toSp(), lineHeight = (full.lineHeight.toDp() * fit).toSp())
        } else {
            full
        }
    }

    BoxWithConstraints(modifier.testTag(TimerExpandedTag)) {
        // Given a height (a screen), it fills it, the controls start at its middle and the whole
        // scrolls when taller; in a scrolling parent (the gallery) it simply stacks.
        val bounded = constraints.hasBoundedHeight
        val viewport = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (bounded) Modifier.fillMaxHeight().verticalScroll(rememberScrollState()) else Modifier),
        ) {
            Column(Modifier.fillMaxWidth().then(if (bounded) Modifier.heightIn(min = viewport) else Modifier)) {
                // The top half: close, title and the ring, centered in what is left of it.
                Column(
                    Modifier.fillMaxWidth().then(if (bounded) Modifier.heightIn(min = viewport / 2) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(space.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MIconButton(MIcons.ChevronDown, closeLabel, onClose, Modifier.testTag(TimerCloseTag))
                        MText(
                            title, MTheme.type.titleSm,
                            modifier = Modifier.weight(1f).testTag(TimerTitleTag), color = colors.textPrimary,
                        )
                    }
                    Spacer(Modifier.height(space.s4))
                    Spacer(Modifier.weight(1f))
                    RestTimerRing(
                        { progress.value }, sizes.expandedRing, sizes.expandedRingStroke, ticks = true, phase = visual.phase,
                        modifier = Modifier.testTag(TimerRingTag),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MText(
                                status, MTheme.type.labelLg.copy(textAlign = TextAlign.Center),
                                modifier = Modifier.widthIn(max = inner * RING_STATUS_SHARE), color = colors.textSecondary,
                            )
                            if (done) {
                                // Done: the check where the time was, in the full ring (spec "Expandido");
                                // "0:00" would say nothing more, and the ring keeps its room at large text.
                                MIcon(
                                    MIcons.CircleCheck, contentDescription = null,
                                    modifier = Modifier.testTag(TimerDoneCheckTag),
                                    size = sizes.checkDisc, tint = colors.timerProgress,
                                )
                            } else {
                                MText(
                                    MoosclesNumbers.clock(visual.remainingSeconds),
                                    timeStyle,
                                    modifier = Modifier.testTag(TimerTimeTag),
                                    color = if (paused) colors.textSecondary else colors.textPrimary,
                                    softWrap = false,
                                    maxLines = 1,
                                )
                            }
                            if (total != null) {
                                MText(
                                    total, MTheme.type.labelLg.tabular(),
                                    modifier = Modifier.testTag(TimerTotalTag), color = colors.textSecondary,
                                    softWrap = false, maxLines = 1,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(space.s4))
                    Spacer(Modifier.weight(1f))
                }
                if (bounded) Spacer(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth().padding(bottom = space.s4),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space.s6),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.s6), verticalAlignment = Alignment.CenterVertically) {
                        TimerAdjustButton(
                            minusShort, minusLabel, { haptics.perform(HapticEvent.StepperStep); onMinus15() },
                            Modifier.testTag(TimerMinusTag), shape = MTheme.radius.full, size = disc,
                        )
                        TimerAdjustButton(
                            plusShort, plusLabel, { haptics.perform(HapticEvent.StepperStep); onPlus15() },
                            Modifier.testTag(TimerPlusTag), shape = MTheme.radius.full, size = disc, enabled = canAdd,
                        )
                    }
                    if (upNext != null) {
                        Column(
                            Modifier.fillMaxWidth().testTag(TimerUpNextTag).semantics(mergeDescendants = true) {},
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            MText(stringResource(Res.string.ds_timer_up_next), MTheme.type.labelCaps, color = colors.textSecondary)
                            MText(
                                upNext, MTheme.type.titleMd.copy(textAlign = TextAlign.Center),
                                modifier = Modifier.fillMaxWidth(), color = colors.textPrimary,
                            )
                        }
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MTheme.targets.gap)) {
                        MButton(
                            skipLabel, onSkip,
                            modifier = Modifier.fillMaxWidth().testTag(TimerSkipTag),
                            variant = MButtonVariant.Tonal,
                            size = MButtonSize.L,
                            icon = MIcons.SkipForward,
                        )
                        MButton(
                            toggleLabel, onPauseResume,
                            modifier = Modifier.fillMaxWidth().testTag(TimerPauseResumeTag),
                            variant = MButtonVariant.Secondary,
                            size = MButtonSize.L,
                            icon = if (paused) MIcons.Play else MIcons.Pause,
                            enabled = !done,
                        )
                        MButton(
                            restartLabel, onRestart,
                            modifier = Modifier.fillMaxWidth().testTag(TimerRestartTag),
                            variant = MButtonVariant.Text,
                            size = MButtonSize.L,
                        )
                    }
                }
            }
        }
        TimerAnnouncer(announcement)
    }
}
