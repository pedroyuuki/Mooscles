package com.mooscles.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.theme.MTheme

/** The opacity the skeleton is drawn with; exposed so tests can read it. */
val SkeletonAlpha = SemanticsPropertyKey<Float>("SkeletonAlpha")
private var SemanticsPropertyReceiver.skeletonAlpha by SkeletonAlpha

/**
 * A loading placeholder on `bg.inset`. Its opacity pulses over `timing.skeletonPulse`; with
 * reduced motion it stays still. Give it a size with [modifier].
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MTheme.radius.xs),
) {
    val low = MTheme.sizes.skeletonMinAlpha
    val alpha = if (MTheme.reducedMotion) {
        1f
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 1f,
            targetValue = low,
            animationSpec = infiniteRepeatable(
                tween(MTheme.timing.skeletonPulse.toInt() / 2, easing = MTheme.motion.easeInOut),
                RepeatMode.Reverse,
            ),
            label = "skeletonAlpha",
        ).value
    }
    Box(
        modifier
            .semantics { skeletonAlpha = alpha }
            .alpha(alpha)
            .background(MTheme.colors.bgInset, shape),
    )
}
