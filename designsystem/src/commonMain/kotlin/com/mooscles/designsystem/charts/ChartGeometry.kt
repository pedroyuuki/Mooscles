package com.mooscles.designsystem.charts

import kotlin.math.abs

/** The pure geometry of the charts: picking what a finger is on and placing a value on the axis. */
internal object ChartGeometry {
    /**
     * The index of the x position in [xs] closest to [touchX]; on a tie, the leftmost one. Null when
     * there is nothing to pick.
     */
    fun nearest(xs: List<Float>, touchX: Float): Int? {
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        for ((i, x) in xs.withIndex()) {
            val distance = abs(x - touchX)
            val closer = distance < bestDistance
            val tieToTheLeft = distance == bestDistance && best != null && x < xs[best]
            if (closer || tieToTheLeft) {
                best = i
                bestDistance = distance
            }
        }
        return best
    }

    /** The y pixel of [value] when [range] runs from [bottom] (its min) up to [top] (its max). */
    fun toPx(value: Double, range: ChartRange, top: Float, bottom: Float): Float {
        val span = range.max - range.min
        if (span <= 0.0) return (top + bottom) / 2
        val fraction = ((value - range.min) / span).toFloat()
        return bottom - fraction * (bottom - top)
    }
}
