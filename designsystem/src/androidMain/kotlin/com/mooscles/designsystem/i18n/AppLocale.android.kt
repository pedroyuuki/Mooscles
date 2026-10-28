package com.mooscles.designsystem.i18n

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {
    // Captured when the first override is applied and cleared when it is undone.
    private var savedLocale: Locale? = null
    private var savedConfiguration: Configuration? = null
    private var resources: Resources? = null

    actual val current: String
        @Composable get() = Locale.getDefault().toLanguageTag()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        if (value == null) {
            restore()
            return LocalConfiguration.provides(configuration)
        }
        val res = LocalContext.current.resources
        if (savedLocale == null) {
            savedLocale = Locale.getDefault()
            savedConfiguration = Configuration(configuration)
            resources = res
        }
        val new = Locale.forLanguageTag(value)
        Locale.setDefault(new)
        val updated = Configuration(configuration).apply { setLocale(new) }
        @Suppress("DEPRECATION")
        res.updateConfiguration(updated, res.displayMetrics)
        return LocalConfiguration.provides(updated)
    }

    actual fun restore() {
        val locale = savedLocale ?: return
        Locale.setDefault(locale)
        @Suppress("DEPRECATION")
        resources?.updateConfiguration(savedConfiguration, resources?.displayMetrics)
        savedLocale = null
        savedConfiguration = null
        resources = null
        LocaleEpoch.bump()
    }
}
