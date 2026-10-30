package com.mooscles.designsystem.components

import com.mooscles.designsystem.illustration.figurePalette
import com.mooscles.designsystem.tokens.Contrast
import com.mooscles.designsystem.tokens.MoosclesColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The token choices of the critique round, measured in both themes. */
class ComponentColorsTest {
    private val themes = listOf("light" to MoosclesColors.Light, "dark" to MoosclesColors.Dark)

    private fun atLeast(min: Double, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, what: String) {
        val ratio = Contrast.ratio(fg, bg)
        assertTrue(ratio >= min, "$what is $ratio:1, below $min:1")
    }

    @Test
    fun errorSurfaceIsLegibleAndNotGreen() {
        for ((name, colors) in themes) {
            val error = ErrorSurfaceColors.of(colors)
            assertEquals(colors.dangerSubtle, error.surface)
            atLeast(4.5, error.content, error.surface, "$name message and action")
            atLeast(3.0, error.icon, error.surface, "$name alert icon")
            atLeast(3.0, error.outline, error.surface, "$name action outline")
            // The banner's action is MButtonVariant.Secondary, drawn in exactly these two tokens.
            assertEquals(colors.textPrimary, error.content, name)
            assertEquals(colors.borderStrong, error.outline, name)
            for (green in listOf(colors.accentText, colors.accentSolid, colors.accentInverseText)) assertNotEquals(green, error.content, name)
        }
    }

    @Test
    fun errorToastLooksDifferentFromAnInfoToast() {
        for ((name, colors) in themes) {
            val info = toastColors(MToastKind.Info, colors)
            val error = toastColors(MToastKind.Error, colors)
            assertNull(info.icon, name)
            assertNotNull(error.icon, name)
            assertNotEquals(info.surface, error.surface, name)
            atLeast(4.5, info.content, info.surface, "$name info message")
            atLeast(4.5, info.action, info.surface, "$name info action")
            atLeast(4.5, error.content, error.surface, "$name error message")
            atLeast(4.5, error.action, error.surface, "$name error action")
            atLeast(3.0, error.icon!!, error.surface, "$name error icon")
        }
    }

    /** Phase 4A: every color of an exercise figure is a 3:1 graphic on the thumbnail tile and on the pages it sits on. */
    @Test
    fun figureColorsStandOutOnTheirBackgrounds() {
        for ((name, colors) in themes) {
            val palette = figurePalette(colors)
            for ((surface, bg) in listOf("inset" to colors.bgInset, "surface" to colors.bgSurface, "base" to colors.bgBase)) {
                atLeast(3.0, palette.body, bg, "$name figure on $surface")
                atLeast(3.0, palette.far, bg, "$name far limbs on $surface")
                atLeast(3.0, palette.equipment, bg, "$name equipment on $surface")
                atLeast(3.0, palette.highlight, bg, "$name highlight on $surface")
            }
        }
    }

    @Test
    fun skeletonStandsOutMoreThanTheWhiteItReplaced() {
        for ((name, colors) in themes) {
            for ((surface, bg) in listOf("base" to colors.bgBase, "surface" to colors.bgSurface, "raised" to colors.bgRaised)) {
                val now = Contrast.ratio(skeletonFill(colors), bg)
                atLeast(1.2, skeletonFill(colors), bg, "$name skeleton on $surface")
                if (bg != colors.bgRaised) assertTrue(now > Contrast.ratio(colors.bgRaised, bg), "$name skeleton on $surface is no better than bg.raised")
            }
        }
    }

    @Test
    fun pausedRingIsNotTheRunningGreenAndKeepsItsContrast() {
        for ((name, colors) in themes) {
            assertEquals(colors.timerProgress, ringBarColor(TimerPhase.Running, colors), name)
            assertEquals(colors.timerProgress, ringBarColor(TimerPhase.Done, colors), name)
            val paused = ringBarColor(TimerPhase.Paused, colors)
            assertNotEquals(colors.timerProgress, paused, name)
            atLeast(3.0, paused, colors.bgRaised, "$name paused arc on the dock")
            atLeast(3.0, paused, colors.bgBase, "$name paused arc on the page")
            atLeast(3.0, paused, colors.timerTrack, "$name paused arc on the track")
        }
    }

    @Test
    fun emptyValueIsAnEmDash() {
        assertEquals("—", EMPTY_VALUE)
    }
}
