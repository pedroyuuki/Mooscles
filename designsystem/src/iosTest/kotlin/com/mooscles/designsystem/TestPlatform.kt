package com.mooscles.designsystem

/**
 * The Skiko test input drops `cancel()` (`InputDispatcher.skiko.kt`: "desktop don't have cancel
 * events as Android does"), so a cancelled gesture stays down for the rest of the test.
 */
internal const val INJECTS_TOUCH_CANCEL = false

/** Nothing to do on the iOS simulator: its input mode belongs to the test's own scene. */
internal fun restoreTouchMode() = Unit
