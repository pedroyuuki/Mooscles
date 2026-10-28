package com.mooscles.designsystem.i18n

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {
    private var defaultLocale: Locale? = null
    private var defaultConfiguration: Configuration? = null

    actual val current: String
        @Composable get() = Locale.getDefault().toLanguageTag()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        val resources = LocalContext.current.resources
        if (defaultLocale == null) {
            defaultLocale = Locale.getDefault()
            defaultConfiguration = Configuration(configuration)
        }
        if (value == null) {
            // Back to the system language: restore the default locale and the saved configuration.
            Locale.setDefault(defaultLocale!!)
            val restored = Configuration(defaultConfiguration!!)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(restored, resources.displayMetrics)
            return LocalConfiguration.provides(restored)
        }
        val new = Locale.forLanguageTag(value)
        Locale.setDefault(new)
        val updated = Configuration(configuration).apply { setLocale(new) }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(updated, resources.displayMetrics)
        return LocalConfiguration.provides(updated)
    }
}
