package com.mooscles.designsystem

/** The emulator's test input sends a real pointer cancel. */
internal const val INJECTS_TOUCH_CANCEL = true

/** The emulator's test input drives a long press and a drag (phase 4B spike S2). */
internal const val INJECTS_DRAG = true

/**
 * Puts the emulator back in touch mode after a test that asked for keyboard mode
 * (`InputModeManager.requestInputMode(Keyboard)`). Touch mode is the display's, not the test's:
 * left off, a cleared focus goes back to the first focusable in the tests that run after
 * (`FocusRingTest` failed that way in the full suite), and common code cannot turn it back on.
 */
internal fun restoreTouchMode() {
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
}
