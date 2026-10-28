package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.RestTimerDock
import com.mooscles.designsystem.components.RestTimerExpanded
import com.mooscles.designsystem.components.RestTimerVisual
import com.mooscles.designsystem.components.TimerPhase
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_timer_rest
import com.mooscles.designsystem.resources.gallery_finish_rest
import com.mooscles.designsystem.resources.gallery_sample_exercise_next
import com.mooscles.designsystem.resources.gallery_section_timer
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

private const val TOTAL = 120
private const val STEP = 15
private const val SAMPLE_REMAINING = 84

/** The dock running, paused and done, and the expanded ring, whose ±15 s buttons work. */
@Composable
fun TimerPage() {
    val label = stringResource(Res.string.ds_timer_rest)
    val next = stringResource(Res.string.gallery_sample_exercise_next)
    var finished by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(SAMPLE_REMAINING) }
    fun visual(phase: TimerPhase, seconds: Int) = RestTimerVisual(seconds, TOTAL, phase, label)
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s4),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_timer))
        RestTimerDock(visual(TimerPhase.Running, remaining), { remaining = (remaining - STEP).coerceAtLeast(0) }, { remaining = (remaining + STEP).coerceAtMost(TOTAL) }, {})
        RestTimerDock(visual(TimerPhase.Paused, SAMPLE_REMAINING), {}, {}, {})
        if (finished) {
            RestTimerDock(visual(TimerPhase.Done, 0), {}, {}, {})
        } else {
            MButton(stringResource(Res.string.gallery_finish_rest), { finished = true }, variant = MButtonVariant.Secondary)
        }
        RestTimerExpanded(
            visual(TimerPhase.Running, remaining), next,
            { remaining = (remaining - STEP).coerceAtLeast(0) }, { remaining = (remaining + STEP).coerceAtMost(TOTAL) }, {},
            modifier = Modifier.padding(top = MTheme.space.s4),
        )
    }
}
