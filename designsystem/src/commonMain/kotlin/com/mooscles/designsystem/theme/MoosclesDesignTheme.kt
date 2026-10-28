package com.mooscles.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.mooscles.designsystem.interaction.MoosclesHaptics
import com.mooscles.designsystem.interaction.NoHaptics
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.rememberMoosclesTypography

/**
 * Provides every design system token to [content]. [reducedMotion] defaults to the system
 * setting; tests and the gallery may override it. [haptics] stays [NoHaptics] until the
 * platform implementations land.
 */
@Composable
fun MoosclesDesignTheme(
    dark: Boolean,
    reducedMotion: Boolean = rememberSystemReducedMotion(),
    haptics: MoosclesHaptics = NoHaptics,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) MoosclesColors.Dark else MoosclesColors.Light
    CompositionLocalProvider(
        LocalMoosclesColors provides colors,
        LocalMoosclesTypography provides rememberMoosclesTypography(),
        LocalMoosclesReducedMotion provides reducedMotion,
        LocalMoosclesHaptics provides haptics,
        LocalMoosclesDark provides dark,
        LocalMContentColor provides colors.textPrimary,
        content = content,
    )
}
