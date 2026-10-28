package com.mooscles.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import com.mooscles.designsystem.interaction.MoosclesHaptics
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.theme.MoosclesDesignTheme

/**
 * Sets [content] inside the design system theme. [fontScale] replaces the density's font
 * scale, so a test can render at 2x text. v2 queues the composition: call `waitForIdle()`.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.withTheme(
    dark: Boolean = false,
    fontScale: Float = 1f,
    reducedMotion: Boolean = false,
    haptics: MoosclesHaptics = RecordingHaptics(),
    content: @Composable () -> Unit,
) {
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            MoosclesDesignTheme(dark = dark, reducedMotion = reducedMotion, haptics = haptics, content = content)
        }
    }
}
