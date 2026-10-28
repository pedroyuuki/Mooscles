package com.mooscles.designsystem.format

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Formats [n] with the plural [res]. Zero stays plural ("0 séries"), as everywhere in the app:
 * CLDR puts 0 in `one` for Portuguese, so the quantity passed to the plural rule is 2.
 */
@Composable
fun pluralCount(res: PluralStringResource, n: Int): String =
    pluralStringResource(res, if (n == 0) 2 else n, n)
