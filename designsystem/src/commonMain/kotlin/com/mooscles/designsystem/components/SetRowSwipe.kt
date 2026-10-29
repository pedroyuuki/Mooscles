package com.mooscles.designsystem.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.theme.MTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/** How far, as a share of the row's width, a set row must be dragged for the swipe to count. */
const val SetRowSwipeThreshold = 0.35f

/** Where a swiped row can be: at rest, or dragged a whole width toward completing or removing. */
private enum class SwipeAnchor { Center, Complete, Remove }

/**
 * Lets a set row be swiped sideways: released past [SetRowSwipeThreshold] of its width to the
 * right it calls [onComplete], to the left [onRemove]; shorter, it calls nothing. Either way it
 * goes back to the center when the finger lifts (a snappy spring, instant with reduced motion).
 * Crossing the threshold, in either direction, gives the `StepperStep` haptic. Only the release
 * position counts, never the speed, so a quick flick that stays short does nothing. The drag only
 * starts after a horizontal touch slop, so a vertical scroll of the list that starts on the row
 * stays a scroll. With [enabled] false the row does not move. Both actions also need a tap path and
 * an accessibility action (the row's check and its "Remove set" action).
 */
fun Modifier.setRowSwipe(enabled: Boolean, onComplete: () -> Unit, onRemove: () -> Unit): Modifier = composed {
    val haptics = MTheme.haptics
    val reduced = MTheme.reducedMotion
    val snappy = MTheme.motion.springSnappy
    val complete by rememberUpdatedState(onComplete)
    val remove by rememberUpdatedState(onRemove)
    val state = remember { AnchoredDraggableState(SwipeAnchor.Center) }
    val back: AnimationSpec<Float> = if (reduced) snap() else spring(snappy.dampingRatio, snappy.stiffness)
    val fling = remember(state, back) {
        SettleToCenter(state, back) { released ->
            val limit = state.limit()
            when {
                limit <= 0f -> Unit
                released >= limit -> complete()
                released <= -limit -> remove()
            }
        }
    }
    LaunchedEffect(state) {
        // -1, 0 or 1: which side of the threshold the row is on; a tick each time it gets past it.
        snapshotFlow {
            val offset = state.offset
            val limit = state.limit()
            when {
                offset.isNaN() || limit <= 0f -> 0
                offset >= limit -> 1
                offset <= -limit -> -1
                else -> 0
            }
        }.distinctUntilChanged().collect { side -> if (side != 0) haptics.perform(HapticEvent.StepperStep) }
    }
    this
        .onSizeChanged { size ->
            val width = size.width.toFloat()
            state.updateAnchors(
                DraggableAnchors {
                    SwipeAnchor.Center at 0f
                    SwipeAnchor.Complete at width
                    SwipeAnchor.Remove at -width
                },
            )
        }
        .anchoredDraggable(state, Orientation.Horizontal, enabled = enabled, flingBehavior = fling)
        .offset {
            val offset = state.offset
            IntOffset(if (offset.isNaN()) 0 else offset.roundToInt(), 0)
        }
}

/** The swipe threshold in pixels: [SetRowSwipeThreshold] of the width the anchors were set for. */
private fun AnchoredDraggableState<SwipeAnchor>.limit(): Float {
    val width = anchors.positionOf(SwipeAnchor.Complete)
    return if (width.isNaN()) 0f else width * SetRowSwipeThreshold
}

/**
 * Ends every drag at the center: reports where the finger lifted to [onRelease], then animates the
 * offset back to 0 with [spec], ignoring the fling velocity.
 */
private class SettleToCenter(
    private val state: AnchoredDraggableState<SwipeAnchor>,
    private val spec: AnimationSpec<Float>,
    private val onRelease: (Float) -> Unit,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val released = state.offset
        if (released.isNaN()) return 0f
        onRelease(released)
        var current = released
        animate(released, 0f, animationSpec = spec) { value, _ ->
            scrollBy(value - current)
            current = value
        }
        return 0f
    }
}
