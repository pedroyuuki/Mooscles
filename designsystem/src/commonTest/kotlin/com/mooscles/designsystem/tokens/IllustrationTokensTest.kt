package com.mooscles.designsystem.tokens

import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.illustration.FigureSkeleton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IllustrationTokensTest {
    /**
     * Phase 4A (spec D3 and "Onde as figuras vivem"): the starting values of the figure renderer.
     * Task 8 tunes them on the pilot poses and pins the final ones here, in the same commit.
     */
    @Test
    fun valuesArePinned() {
        val i = MoosclesIllustration()
        // Fractions of the figure's 100-unit box.
        assertEquals(0.06f, i.figureStroke)
        assertEquals(0.04f, i.figureHeadStroke)
        assertEquals(0.05f, i.figureMargin)
        // The body map's outline: the 1 dp hairline the spike used (sizes.border).
        assertEquals(1.dp, i.bodyMapStroke)
        // A pose that keeps its joints `figureMargin` inside the box keeps its whole stroke inside too.
        assertTrue(i.figureMargin >= i.figureStroke / 2, "the margin holds half a limb stroke")
        // The bones of the 100-unit box (spec: trunk 30, arm 15, forearm 14, thigh 21, shin 20, foot 6, head radius 5).
        assertEquals(
            listOf(30f, 4f, 5f, 15f, 14f, 21f, 20f, 6f),
            listOf(
                FigureSkeleton.TRUNK, FigureSkeleton.NECK, FigureSkeleton.HEAD_RADIUS, FigureSkeleton.UPPER_ARM,
                FigureSkeleton.FOREARM, FigureSkeleton.THIGH, FigureSkeleton.SHIN, FigureSkeleton.FOOT,
            ),
        )
    }

    /** At the 64 dp of a list thumbnail the figure's limbs must read heavier than the body map's hairline. */
    @Test
    fun strokeIsThickerThanTheBodyMapsAtSixtyFourDp() {
        val i = MoosclesIllustration()
        val thumbnail = MoosclesSizes().media
        assertEquals(64.dp, thumbnail)
        assertTrue(thumbnail * i.figureStroke > i.bodyMapStroke, "limb ${thumbnail * i.figureStroke} vs map ${i.bodyMapStroke}")
        assertTrue(thumbnail * i.figureHeadStroke > i.bodyMapStroke, "head ${thumbnail * i.figureHeadStroke} vs map ${i.bodyMapStroke}")
    }
}
