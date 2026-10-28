package com.mooscles.designsystem.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

/**
 * Overrides the language the Compose resources resolve in. Only the hidden gallery and the
 * tests use it, following JetBrains' documented workaround
 * (kotlinlang.org/docs/multiplatform/compose-resource-environment.html). No component reads it.
 * [tag] is a BCP 47 language tag such as `pt-BR`; null restores the system language.
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

@Composable
fun ProvideAppLocale(tag: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides tag) {
        // A new key rebuilds the content, so every string is read again in the new language.
        key(tag) { content() }
    }
}
