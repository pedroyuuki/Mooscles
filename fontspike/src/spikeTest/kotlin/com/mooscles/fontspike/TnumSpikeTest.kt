package com.mooscles.fontspike

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * S1 (tabular figures) and S2 (Inter weight axis), measured with the platform text
 * stack: run on the iOS simulator (iosSimulatorArm64Test) and on the emulator
 * (connectedAndroidDeviceTest). Widths are in px at Density(2f) and 48 sp.
 */
class TnumSpikeTest {

    @Test
    fun interWithoutTnumIsProportional() {
        val (ones, eights) = measure(
            Request("1111", Face.INTER, 400, tnum = false),
            Request("8888", Face.INTER, 400, tnum = false),
        )
        report("interWithoutTnumIsProportional", "inter400 1111=$ones 8888=$eights")
        assertTrue(abs(ones - eights) > TOLERANCE, "Inter 400 without tnum should be proportional: $ones vs $eights")
    }

    @Test
    fun barlowIsReallyLoaded() {
        val (barlow, inter, default) = measure(
            Request("8888", Face.BARLOW, 600, tnum = false),
            Request("8888", Face.INTER, 600, tnum = false),
            Request("8888", Face.DEFAULT, 600, tnum = false),
        )
        report("barlowIsReallyLoaded", "8888 barlow600=$barlow inter600=$inter default600=$default")
        assertTrue(barlow < 0.85f * inter, "Barlow 600 ($barlow) should be narrower than 0.85 x Inter 600 ($inter)")
        assertTrue(abs(barlow - default) > TOLERANCE, "Barlow 600 ($barlow) matches FontFamily.Default ($default)")
    }

    @Test
    fun interWithTnumIsTabular() {
        val weights = listOf(400, 600, 700)
        val widths = measure(
            *weights.flatMap { w ->
                listOf(Request("1111", Face.INTER, w, tnum = true), Request("8888", Face.INTER, w, tnum = true))
            }.toTypedArray(),
        )
        val failures = mutableListOf<String>()
        weights.forEachIndexed { i, w ->
            val ones = widths[2 * i]
            val eights = widths[2 * i + 1]
            report("interWithTnumIsTabular", "inter$w tnum 1111=$ones 8888=$eights")
            if (abs(ones - eights) > TOLERANCE) failures += "Inter $w: $ones vs $eights"
        }
        assertTrue(failures.isEmpty(), "Inter with tnum is not tabular: $failures")
    }

    @Test
    fun barlowWithTnumIsTabular() {
        val weights = listOf(500, 600, 700)
        val widths = measure(
            *weights.flatMap { w ->
                listOf(Request("1111", Face.BARLOW, w, tnum = true), Request("8888", Face.BARLOW, w, tnum = true))
            }.toTypedArray(),
        )
        val failures = mutableListOf<String>()
        weights.forEachIndexed { i, w ->
            val ones = widths[2 * i]
            val eights = widths[2 * i + 1]
            report("barlowWithTnumIsTabular", "barlow$w tnum 1111=$ones 8888=$eights")
            if (abs(ones - eights) > TOLERANCE) failures += "Barlow $w: $ones vs $eights"
        }
        assertTrue(failures.isEmpty(), "Barlow with tnum is not tabular: $failures")
    }

    @Test
    fun barlowTimerKeepsWidthWithTnum() {
        val (zero, nine) = measure(
            Request("0:00", Face.BARLOW, 600, tnum = true),
            Request("9:59", Face.BARLOW, 600, tnum = true),
        )
        report("barlowTimerKeepsWidthWithTnum", "barlow600 tnum 0:00=$zero 9:59=$nine")
        assertTrue(abs(zero - nine) <= TOLERANCE, "Barlow 600 timer changes width with tnum: $zero vs $nine")
    }

    /** No assertion: the default (no tnum) figures, for the report (decides A versus A'). */
    @Test
    fun barlowDefaultFiguresAreRecorded() {
        val texts = listOf("1111", "8888", "0:00", "9:59") + (0..9).map { it.toString() }
        for (w in listOf(500, 600, 700)) {
            val widths = measure(*texts.map { Request(it, Face.BARLOW, w, tnum = false) }.toTypedArray())
            report(
                "barlowDefaultFiguresAreRecorded",
                "barlow$w no-tnum " + texts.zip(widths).joinToString(" ") { (t, v) -> "$t=$v" },
            )
        }
    }

    @Test
    fun interWeightAxisApplies() {
        val (regular, bold) = measure(
            Request("8888", Face.INTER, 400, tnum = false),
            Request("8888", Face.INTER, 700, tnum = false),
        )
        report("interWeightAxisApplies", "inter 8888 w400=$regular w700=$bold")
        assertTrue(abs(regular - bold) > TOLERANCE, "Inter wght axis did not change the width: $regular vs $bold")
    }

    private enum class Face { INTER, BARLOW, DEFAULT }

    private class Request(val text: String, val face: Face, val weight: Int, val tnum: Boolean)

    /** Measures every request in one composition and returns the unconstrained line widths in px. */
    @OptIn(ExperimentalTestApi::class)
    private fun measure(vararg requests: Request): List<Float> {
        var widths: List<Float> = emptyList()
        runComposeUiTest {
            setContent {
                val measurer = rememberTextMeasurer()
                widths = requests.map { request ->
                    val family = when (request.face) {
                        Face.INTER -> spikeInter(request.weight)
                        Face.BARLOW -> spikeBarlow()
                        Face.DEFAULT -> FontFamily.Default
                    }
                    measurer.measure(
                        request.text,
                        TextStyle(
                            fontFamily = family,
                            fontWeight = FontWeight(request.weight),
                            fontSize = 48.sp,
                            fontFeatureSettings = if (request.tnum) "tnum" else null,
                        ),
                        density = Density(2f),
                    ).multiParagraph.intrinsics.maxIntrinsicWidth
                }
            }
            waitForIdle()
        }
        check(widths.size == requests.size) { "Measured ${widths.size} of ${requests.size} texts" }
        return widths
    }

    private fun report(test: String, line: String) = println("SPIKE $test: $line")

    private companion object {
        const val TOLERANCE = 0.5f
    }
}
