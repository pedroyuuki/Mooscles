package com.mooscles.designsystem

/**
 * The Skiko test input drops `cancel()` (`InputDispatcher.skiko.kt`: "desktop don't have cancel
 * events as Android does"), so a cancelled gesture stays down for the rest of the test.
 */
internal const val INJECTS_TOUCH_CANCEL = false

/**
 * The Skiko test input is expected to drive a long press and a drag. Phase 4B (plan decision 2):
 * if the simulator's CI run shows it does not, this turns false and the drag tests stand down there
 * (the accessibility actions still run).
 */
internal const val INJECTS_DRAG = true

/** Nothing to do on the iOS simulator: its input mode belongs to the test's own scene. */
internal fun restoreTouchMode() = Unit
