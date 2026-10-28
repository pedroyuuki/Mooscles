package com.mooscles.designsystem.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SizesTimingElevationTest {
    @Test
    fun everySizeMatchesPlanDecisionSeven() {
        val s = MoosclesSizes()
        // Plan decision 7 (dp), by the F15 property names.
        assertEquals(
            listOf(16, 20, 24, 28, 48, 56, 56, 48, 40, 44, 72, 48, 64, 56, 48, 4, 280, 12, 8, 2, 80, 64, 32, 4, 56, 1, 2, 2, 2).map { it.dp },
            listOf(
                s.icon16, s.icon20, s.icon24, s.icon28, s.iconButton48, s.iconButton56,
                s.buttonL, s.buttonM, s.buttonS, s.checkDisc,
                s.numericFieldWidth, s.numericFieldHeight, s.stepperButtonWidth, s.stepperButtonHeight,
                s.dockRing, s.dockRingStroke, s.expandedRing, s.expandedRingStroke,
                s.tickLength, s.tickWidth, s.timerDisc, s.media,
                s.sheetHandleWidth, s.sheetHandleHeight, s.rowMin,
                s.border, s.borderError, s.focusRingWidth, s.focusRingOffset,
            ),
        )
        assertEquals(60, s.tickCount)
        assertEquals(0.9f, s.sheetMaxFraction)
        assertEquals(0.3f, s.progressIndeterminateFraction)
        // Not in decision 7: the dialog card width, added in Task 10 (the spec gives none).
        assertEquals(360.dp, s.dialogMaxWidth)
    }

    @Test
    fun everyTimingMatchesPlanDecisionSeven() {
        val t = MoosclesTiming()
        // Plan decision 7, in ms.
        assertEquals(
            listOf(400L, 80L, 4_000L, 6_000L, 4_000L, 3_000L, 1_200L, 900L, 80L),
            listOf(
                t.stepperRepeatDelay, t.stepperRepeatInterval, t.toastInfo, t.toastAction, t.prToast,
                t.timerDoneHold, t.skeletonPulse, t.illustrationFrame, t.hapticDoubleGap,
            ),
        )
    }

    @Test
    fun elevationLevelsMatchTheSpec() {
        val e = MoosclesElevation()
        // Spec "elevation": light shadows 1, 6 and 12 dp; dark hairline only on level 2, rgba(255,255,255,.06).
        assertEquals(listOf(1.dp, 6.dp, 12.dp), listOf(e.e1.shadowLight, e.e2.shadowLight, e.e3.shadowLight))
        assertNull(e.e1.hairlineDark)
        assertEquals(Color(0x0FFFFFFF), e.e2.hairlineDark)
        assertNull(e.e3.hairlineDark)
    }
}
