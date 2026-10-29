package com.mooscles.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals

class RestTimerRingMathTest {
    @Test
    fun progressIsLinear() {
        assertEquals(0.5f, ringProgress(30_000, 60_000, reduced = false))
        assertEquals(1f, ringProgress(60_000, 60_000, reduced = false))
        assertEquals(0f, ringProgress(0, 60_000, reduced = false))
        assertEquals(0.25f, ringProgress(15_000, 60_000, reduced = false))
        // Clamped: more time left than the total (after +15 s) is a full ring, and no total is none.
        assertEquals(1f, ringProgress(70_000, 60_000, reduced = false))
        assertEquals(1f, ringProgress(70_000, 60_000, reduced = true))
        assertEquals(0f, ringProgress(30_000, 0, reduced = false))
        assertEquals(0f, ringProgress(30_000, 0, reduced = true))
    }

    @Test
    fun reducedMotionStepsBySecond() {
        // 59.4 s left shows 1:00 on the clock, so the ring is at exactly 60/120.
        assertEquals(60f / 120f, ringProgress(59_400, 120_000, reduced = true))
        assertEquals(59_400f / 120_000f, ringProgress(59_400, 120_000, reduced = false))
        assertEquals(ringProgress(59_001, 120_000, reduced = true), ringProgress(60_000, 120_000, reduced = true))
    }

    @Test
    fun sixtyTicksSixDegreesApart() {
        val angles = tickAngles()
        assertEquals(60, angles.size)
        assertEquals(0f, angles.first())
        for (i in 1 until angles.size) assertEquals(6f, angles[i] - angles[i - 1])
        assertEquals(354f, angles.last())
    }
}
