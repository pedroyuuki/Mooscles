package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Counts recompositions in tests: each call of [RecompositionProbe] reports its key once per
 * composition it takes part in. Null in production, so the probe costs one CompositionLocal read.
 */
internal val LocalRecompositionProbe = staticCompositionLocalOf<((String) -> Unit)?> { null }

/**
 * Reports [key] to [LocalRecompositionProbe] after every composition of the caller. Cards, rows,
 * the clock, the dock, the bar and the tabs call it with fixed keys (`card-<id>`, `row-<id>`,
 * `clock`, `dock`, `bar`, `tabs`).
 */
@Composable
internal fun RecompositionProbe(key: String) {
    val probe = LocalRecompositionProbe.current ?: return
    SideEffect { probe(key) }
}
