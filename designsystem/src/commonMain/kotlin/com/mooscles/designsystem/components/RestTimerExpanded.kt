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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_timer_minus15
import com.mooscles.designsystem.resources.ds_timer_plus15
import com.mooscles.designsystem.resources.ds_timer_skip
import com.mooscles.designsystem.resources.ds_timer_up_next
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

const val TimerUpNextTag = "timer-up-next"
const val TimerExpandedTag = "timer-expanded"

/**
 * The full-screen rest timer: a 280 dp plate ring with 60 rim marks, the time in `num.display`,
 * the -15 and +15 buttons as 80 dp discs, the exercise that comes next ([upNext], when there is
 * one) and "Skip rest". Same phases, haptics and live region as [RestTimerDock]; presentational too.
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
    val progress = rememberRingProgress(visual)
    val announcement = rememberTimerAnnouncement(visual)
    val status = timerStatus(visual)
    val done = visual.phase == TimerPhase.Done
    LaunchedEffect(done) { if (done) haptics.perform(HapticEvent.RestFinished) }
    val minusLabel = stringResource(Res.string.ds_timer_minus15)
    val plusLabel = stringResource(Res.string.ds_timer_plus15)
    val skipLabel = stringResource(Res.string.ds_timer_skip)
    val disc = MTheme.sizes.timerDisc
    Box(modifier.testTag(TimerExpandedTag)) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s6),
    ) {
        RestTimerRing({ progress.value }, MTheme.sizes.expandedRing, MTheme.sizes.expandedRingStroke, ticks = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MText(status, MTheme.type.labelLg, color = colors.textSecondary)
                MText(
                    MoosclesNumbers.clock(visual.remainingSeconds),
                    MTheme.type.numDisplay,
                    modifier = Modifier.testTag(TimerTimeTag),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s6), verticalAlignment = Alignment.CenterVertically) {
            TimerAdjustButton(
                "−15", minusLabel, { haptics.perform(HapticEvent.StepperStep); onMinus15() },
                Modifier.testTag(TimerMinusTag), shape = MTheme.radius.full, size = disc, square = true,
            )
            TimerAdjustButton(
                "+15", plusLabel, { haptics.perform(HapticEvent.StepperStep); onPlus15() },
                Modifier.testTag(TimerPlusTag), shape = MTheme.radius.full, size = disc, square = true,
            )
        }
        if (upNext != null) {
            Column(
                Modifier.fillMaxWidth().testTag(TimerUpNextTag).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MText(stringResource(Res.string.ds_timer_up_next), MTheme.type.labelCaps, color = colors.textSecondary)
                MText(upNext, MTheme.type.titleMd.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth(), color = colors.textPrimary)
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
