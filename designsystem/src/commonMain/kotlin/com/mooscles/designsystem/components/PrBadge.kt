package com.mooscles.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.KeyMoment
import com.mooscles.designsystem.interaction.MotionChoice
import com.mooscles.designsystem.interaction.MotionPolicy
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_pr_label
import com.mooscles.designsystem.resources.ds_pr_spoken
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** The tag of the badge, for tests. */
const val PrBadgeTag = "pr-badge"

/**
 * The personal record badge: a pill with the record icon and "PR", so it is never color alone.
 * It is read as "personal record". [soft] uses the quiet `pr.subtle` fill instead of `pr.solid`.
 * With [animateIn] it appears with the `PersonalRecord` key moment (a spring, or a fade with reduced motion).
 */
@Composable
fun PrBadge(modifier: Modifier = Modifier, soft: Boolean = false, animateIn: Boolean = false) {
    val colors = MTheme.colors
    val spoken = stringResource(Res.string.ds_pr_spoken)
    val label = stringResource(Res.string.ds_pr_label)
    val container = if (soft) colors.prSubtle else colors.prSolid
    val content = if (soft) colors.prText else colors.prOn
    val choice = MotionPolicy.keyMoment(KeyMoment.PersonalRecord, MTheme.reducedMotion)
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val rise = remember { Animatable(0f) }
    val riseFrom = (choice as? MotionChoice.Spring)?.riseDp ?: Dp(0f)
    val riseFromPx = with(LocalDensity.current) { riseFrom.toPx() }
    LaunchedEffect(animateIn) {
        if (!animateIn) return@LaunchedEffect
        when (choice) {
            is MotionChoice.Spring -> {
                scale.snapTo(choice.fromScale)
                rise.snapTo(riseFromPx)
                val spec = spring<Float>(choice.tokens.dampingRatio, choice.tokens.stiffness)
                scale.animateTo(1f, spec)
                rise.animateTo(0f, spec)
            }
            is MotionChoice.Fade -> {
                alpha.snapTo(0f)
                alpha.animateTo(1f, tween(choice.durationMs))
            }
        }
    }
    Row(
        modifier
            .testTag(PrBadgeTag)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                translationY = rise.value
            }
            .background(container, MTheme.radius.full)
            .padding(horizontal = MTheme.space.s2, vertical = MTheme.space.s1)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MIcon(MIcons.PlateRecord, contentDescription = null, size = MTheme.sizes.icon16, tint = content)
        MText(label, MTheme.type.labelMd, color = content)
    }
}
