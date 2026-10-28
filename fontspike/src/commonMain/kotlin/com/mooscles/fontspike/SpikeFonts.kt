package com.mooscles.fontspike

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.mooscles.fontspike.resources.Res
import com.mooscles.fontspike.resources.barlow_condensed_bold
import com.mooscles.fontspike.resources.barlow_condensed_medium
import com.mooscles.fontspike.resources.barlow_condensed_semibold
import com.mooscles.fontspike.resources.inter_variable
import org.jetbrains.compose.resources.Font

/** Inter variable at one weight, with the `wght` axis set explicitly (S2). */
@Composable
fun spikeInter(weight: Int): FontFamily = FontFamily(
    Font(
        Res.font.inter_variable,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    ),
)

/** Barlow Condensed static files at 500, 600 and 700. */
@Composable
fun spikeBarlow(): FontFamily = FontFamily(
    Font(Res.font.barlow_condensed_medium, FontWeight.Medium),
    Font(Res.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(Res.font.barlow_condensed_bold, FontWeight.Bold),
)
