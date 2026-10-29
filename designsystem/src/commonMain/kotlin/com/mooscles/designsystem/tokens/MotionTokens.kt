package com.mooscles.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The four control points of a cubic-bezier curve, kept so tests can read them back. */
data class BezierPoints(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    fun easing(): CubicBezierEasing = CubicBezierEasing(x1, y1, x2, y2)
}

/** A spring: bounce only in the three key moments (set done, record, workout end). */
data class SpringTokens(val dampingRatio: Float, val stiffness: Float)

/** Durations (ms), curves, springs and the scale and offset constants of the motion spec. */
data class MoosclesMotion(
    val instant: Int = 0,
    val press: Int = 120,
    val fast: Int = 150,
    val base: Int = 220,
    val slow: Int = 300,
    val slowExit: Int = 200,
    val easeOutPoints: BezierPoints = BezierPoints(0.23f, 1f, 0.32f, 1f),
    val easeInOutPoints: BezierPoints = BezierPoints(0.77f, 0f, 0.175f, 1f),
    val easeDrawerPoints: BezierPoints = BezierPoints(0.32f, 0.72f, 0f, 1f),
    val springSnappy: SpringTokens = SpringTokens(dampingRatio = 1.0f, stiffness = 700f),
    val springSet: SpringTokens = SpringTokens(dampingRatio = 0.6f, stiffness = 600f),
    val springPr: SpringTokens = SpringTokens(dampingRatio = 0.5f, stiffness = 400f),
    val springFinish: SpringTokens = SpringTokens(dampingRatio = 0.7f, stiffness = 300f),
    val pressScale: Float = 0.96f,
    val setCheckFrom: Float = 0.92f,
    val prFrom: Float = 0.9f,
    val prRise: Dp = 8.dp,
) {
    val easeOut: CubicBezierEasing get() = easeOutPoints.easing()
    val easeInOut: CubicBezierEasing get() = easeInOutPoints.easing()
    val easeDrawer: CubicBezierEasing get() = easeDrawerPoints.easing()
}

/** Durations in ms that are not animation curves: repeat rates, toast and hold times. */
data class MoosclesTiming(
    val stepperRepeatDelay: Long = 400,
    val stepperRepeatInterval: Long = 80,
    val toastInfo: Long = 4_000,
    val toastAction: Long = 6_000,
    val prToast: Long = 4_000,
    val timerDoneHold: Long = 3_000,
    /** How long the timer's live region keeps its text after an announcement; empty after, so it is not a screen-reader stop. */
    val timerAnnounceHold: Long = 1_500,
    val skeletonPulse: Long = 1_200,
    val illustrationFrame: Long = 900,
    val hapticDoubleGap: Long = 80,
    /** Phase 3 (D9): after the last pending set of an exercise is checked, the wait before its card collapses and the next opens. */
    val cardAdvance: Long = 300,
)
