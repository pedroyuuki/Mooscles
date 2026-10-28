package com.mooscles.designsystem.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

actual object LocalAppLocale {
    private const val LANGUAGES_KEY = "AppleLanguages"
    private val systemTag = NSLocale.preferredLanguages.first() as String
    private val local = staticCompositionLocalOf { systemTag }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val defaults = NSUserDefaults.standardUserDefaults
        if (value == null) {
            defaults.removeObjectForKey(LANGUAGES_KEY)
        } else {
            defaults.setObject(listOf(value), LANGUAGES_KEY)
        }
        return local.provides(value ?: systemTag)
    }
}
