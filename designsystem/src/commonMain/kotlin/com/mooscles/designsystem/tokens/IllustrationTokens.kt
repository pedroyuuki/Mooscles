package com.mooscles.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 4A: the strokes of the exercise figures and the body map. The figure values are fractions
 * of the figure's 100-unit box, so a figure reads the same at the 64 dp of a list and at the size
 * of the detail. [figureStroke] is the limbs and trunk (round caps: capsules); [figureHeadStroke]
 * the head ring and the equipment; [figureMargin] how far inside the box a pose keeps its joints
 * and equipment (at least half a [figureStroke], so no stroke is cut). [bodyMapStroke] is the
 * body map's outline, the 1 dp hairline of the spike. Starting values: Task 8 tunes them on the
 * pilot poses and pins the final ones in `IllustrationTokensTest`.
 */
@Immutable
data class MoosclesIllustration(
    val figureStroke: Float = 0.06f,
    val figureHeadStroke: Float = 0.04f,
    val figureMargin: Float = 0.05f,
    val bodyMapStroke: Dp = 1.dp,
)
