package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_timer_minus15
import com.mooscles.designsystem.resources.ds_timer_minus15_short
import com.mooscles.designsystem.resources.ds_timer_plus15
import com.mooscles.designsystem.resources.ds_timer_plus15_short
import com.mooscles.designsystem.resources.ds_timer_skip
import com.mooscles.designsystem.resources.ds_timer_up_next
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

const val TimerUpNextTag = "timer-up-next"
const val TimerExpandedTag = "timer-expanded"
const val TimerRingTag = "timer-ring"

/** The widest time the expanded timer shows; the size of the digits is chosen for it. */
private const val EXPANDED_WIDEST_TIME = "00:00"

/**
 * The full-screen rest timer: a 280 dp plate ring with 60 rim marks, the time in `num.display`,
 * the -15 and +15 buttons as 80 dp discs, the exercise that comes next ([upNext], when there is
 * one) and "Skip rest". Same phases, haptics and live region as [RestTimerDock]; presentational too.
 * The time stays on one line inside the clear middle of the ring: when the font scale would make it
 * wider, it shrinks to fit, but never below `num.lg`.
 */
@Composable
fun RestTimerExpanded(
    visual: RestTimerVisual,
    upNext: String?,
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
    val minusLabel = stringResource(Res.string.ds_timer_minus15)
    val plusLabel = stringResource(Res.string.ds_timer_plus15)
    val minusShort = stringResource(Res.string.ds_timer_minus15_short)
    val plusShort = stringResource(Res.string.ds_timer_plus15_short)
    val skipLabel = stringResource(Res.string.ds_timer_skip)
    val disc = MTheme.sizes.timerDisc

    val measurer = rememberTextMeasurer()
    val direction = LocalLayoutDirection.current
    val sizes = MTheme.sizes
    val full = MTheme.type.numDisplay
    val inner = sizes.expandedRing - (sizes.tickLength + MTheme.space.s1 + sizes.expandedRingStroke) * 2
    val smallest = MTheme.type.numLg.fontSize.value / full.fontSize.value
    val fit = with(density) {
        val width = measurer.measure(
            EXPANDED_WIDEST_TIME, full, softWrap = false, maxLines = 1, layoutDirection = direction, density = density,
        ).size.width.toDp()
        (inner / width).coerceIn(smallest, 1f)
    }
    val timeStyle = if (fit < 1f) full.copy(fontSize = full.fontSize * fit, lineHeight = full.lineHeight * fit) else full

    Box(modifier.testTag(TimerExpandedTag)) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s6),
        ) {
            RestTimerRing(
                { progress.value }, sizes.expandedRing, sizes.expandedRingStroke, ticks = true,
                modifier = Modifier.testTag(TimerRingTag),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MText(status, MTheme.type.labelLg, color = colors.textSecondary)
                    MText(
                        MoosclesNumbers.clock(visual.remainingSeconds),
                        timeStyle,
                        modifier = Modifier.testTag(TimerTimeTag),
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s6), verticalAlignment = Alignment.CenterVertically) {
                TimerAdjustButton(
                    minusShort, minusLabel, { haptics.perform(HapticEvent.StepperStep); onMinus15() },
                    Modifier.testTag(TimerMinusTag), shape = MTheme.radius.full, size = disc, square = true,
                )
                TimerAdjustButton(
                    plusShort, plusLabel, { haptics.perform(HapticEvent.StepperStep); onPlus15() },
                    Modifier.testTag(TimerPlusTag), shape = MTheme.radius.full, size = disc, square = true,
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
            MButton(
                skipLabel, onSkip,
                modifier = Modifier.fillMaxWidth().testTag(TimerSkipTag),
                variant = MButtonVariant.Secondary,
                size = MButtonSize.L,
                icon = MIcons.SkipForward,
            )
        }
        TimerAnnouncer(announcement)
    }
}
