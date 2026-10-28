package com.mooscles.designsystem.i18n

import androidx.compose.runtime.mutableIntStateOf

/**
 * Counts how many times an applied language override was undone. The platform reads the default
 * locale at composition time, which is not observable, so nothing would recompose after a
 * restore. [MoosclesDesignTheme][com.mooscles.designsystem.theme.MoosclesDesignTheme] keys its
 * content on this value, so the tree re-resolves its strings. It changes only when an override
 * existed, so normal app use never rebuilds.
 */
internal object LocaleEpoch {
    val value = mutableIntStateOf(0)

    fun bump() {
        value.intValue += 1
    }
}
