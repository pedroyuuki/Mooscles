package com.mooscles.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 4A: the strokes of the exercise figures and the body map. The figure values are fractions
 * of the figure's 100-unit box, so a figure reads the same at the 64 dp of a list and at the size
 * of the detail. [figureStroke] is the limbs and trunk (round caps: capsules); [figureHeadStroke]
 * the head ring and the equipment; [figureMargin] how far inside the box a pose keeps its joints
 * and equipment (at least half a [figureStroke], so no stroke is cut). [bodyMapStroke] is the
 * body map's outline, the 1 dp hairline of the spike. Final after the pilot poses (Task 8, on the
 * emulator at 64 dp and at half a phone's width): the starting values held, pinned in
 * `IllustrationTokensTest`.
 */
@Immutable
data class MoosclesIllustration(
    val figureStroke: Float = 0.06f,
    val figureHeadStroke: Float = 0.04f,
    val figureMargin: Float = 0.05f,
    val bodyMapStroke: Dp = 1.dp,
)

/**
 * Phase 4A: the theme colors of an exercise figure beyond the semantic tokens. [farLimb] is the
 * far arm and leg (spec "Esqueleto": the far side "num token mais apagado, escolhido no S1"): a
 * body tone, fainter than the near limbs (`text.secondary`) and apart from the equipment
 * (`text.tertiary`), so a far leg on a bench does not read as part of the bench. Iron 57 in both
 * themes: lighter than the equipment in the light theme, darker in the dark one, and 3:1 on the
 * tile and the pages in both (`ComponentColorsTest`).
 */
@Immutable
data class MoosclesFigureColors(val farLimb: Color) {
    companion object {
        val Light = MoosclesFigureColors(farLimb = Primitives.ferro57)
        val Dark = MoosclesFigureColors(farLimb = Primitives.ferro57)
    }
}
