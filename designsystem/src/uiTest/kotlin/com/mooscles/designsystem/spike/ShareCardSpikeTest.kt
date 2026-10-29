package com.mooscles.designsystem.spike

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.mooscles.designsystem.theme.MoosclesDesignTheme
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.rememberMoosclesTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SpikeInputs(val measurer: TextMeasurer, val styles: SpikeCardStyles, val density: Density)

/** Resolves the fonts and styles in a composition, then leaves it: drawing runs outside. */
@OptIn(ExperimentalTestApi::class)
internal fun captureSpikeInputs(): SpikeInputs {
    var inputs: SpikeInputs? = null
    runComposeUiTest {
        setContent {
            MoosclesDesignTheme(dark = true) {
                val measurer = rememberTextMeasurer()
                val t = rememberMoosclesTypography()
                val density = Density(LocalDensity.current.density, 1f)
                inputs = SpikeInputs(
                    measurer,
                    SpikeCardStyles(
                        brand = t.labelCaps.copy(fontSize = t.labelCaps.fontSize * 2.4f),
                        title = t.numLg.copy(fontSize = t.numLg.fontSize * 1.6f),
                        subtitle = t.bodyLg.copy(fontSize = t.bodyLg.fontSize * 1.8f),
                        number = t.numDisplay,
                        numberLabel = t.labelCaps.copy(fontSize = t.labelCaps.fontSize * 1.5f),
                        rowName = t.titleMd.copy(fontSize = t.titleMd.fontSize * 2.2f, lineHeight = t.titleMd.fontSize * 2.7f),
                        rowDetail = t.numMd.copy(fontSize = t.numMd.fontSize * 1.6f),
                        record = t.numSm.copy(fontSize = t.numSm.fontSize * 1.8f),
                    ),
                    density,
                )
            }
        }
        waitForIdle()
    }
    return checkNotNull(inputs)
}

class ShareCardSpikeTest {

    @Test
    fun paintsOffTheMainThreadAt1080() = runTest {
        val i = captureSpikeInputs()
        val pages = withContext(Dispatchers.Default) { paintSpikeCard(spikeFixture(), i.measurer, i.styles, i.density) }
        println("SPIKE paint pages=${pages.size} sizes=${pages.map { "${it.width}x${it.height}" }}")
        assertTrue(pages.isNotEmpty())
        pages.forEach { assertEquals(1080, it.width); assertTrue(it.height > 0) }
    }

    @Test
    fun textIsDrawn() = runTest {
        val i = captureSpikeInputs()
        val pages = withContext(Dispatchers.Default) { paintSpikeCard(spikeFixture(), i.measurer, i.styles, i.density) }
        val bg = MoosclesColors.Dark.bgBase
        val brandBand = pages.first().toPixelMap(0, 72, 1080, 80)
        var differing = 0
        for (x in 0 until 1080) for (y in 0 until 80) if (brandBand[x, y] != bg) differing++
        println("SPIKE textIsDrawn differingPixelsInBrandBand=$differing")
        assertTrue(differing > 0, "brand band is blank")
    }

    @Test
    fun layoutSignature() = runTest {
        val i = captureSpikeInputs()
        val card = spikeFixture()
        val pages = withContext(Dispatchers.Default) { paintSpikeCard(card, i.measurer, i.styles, i.density) }
        val rows = card.exercises.map { spikeRowLines(it.name, i.measurer, i.styles, i.density) }
        println("SPIKE layout pages=${pages.size} rows=$rows")
    }
}
