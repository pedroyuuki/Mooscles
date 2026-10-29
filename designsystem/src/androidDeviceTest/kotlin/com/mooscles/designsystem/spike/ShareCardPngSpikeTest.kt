package com.mooscles.designsystem.spike

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareCardPngSpikeTest {

    private fun png(image: ImageBitmap): ByteArray {
        val out = ByteArrayOutputStream()
        check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out))
        return out.toByteArray()
    }

    private fun paint(i: SpikeInputs) = paintSpikeCard(spikeFixture(), i.measurer, i.styles, i.density)

    @Test
    fun pngMatchesThePreviewBitmap() = runBlocking {
        val i = captureSpikeInputs()
        val pages = withContext(Dispatchers.Default) { paint(i) }
        var mismatches = 0
        pages.forEachIndexed { index, page ->
            val decoded = BitmapFactory.decodeByteArray(png(page), 0, png(page).size)
            assertEquals(page.width, decoded.width)
            assertEquals(page.height, decoded.height)
            val expected = IntArray(page.width * page.height)
            page.readPixels(expected)
            val actual = IntArray(page.width * page.height)
            decoded.getPixels(actual, 0, page.width, 0, 0, page.width, page.height)
            mismatches += expected.indices.count { expected[it] != actual[it] }
            println("SPIKE png page=$index ${page.width}x${page.height} bytes=${png(page).size}")
        }
        println("SPIKE png mismatchedPixels=$mismatches pages=${pages.size}")
        assertEquals(0, mismatches)
    }

    @Test
    fun paintAndEncodeUnder500ms() = runBlocking {
        val i = captureSpikeInputs()
        val times = (1..5).map {
            val start = SystemClock.elapsedRealtime()
            withContext(Dispatchers.Default) { paint(i).forEach { png(it) } }
            SystemClock.elapsedRealtime() - start
        }
        val median = times.sorted()[2]
        println("SPIKE time runsMs=$times medianMs=$median")
        assertTrue(median < 500, "median $median ms")
    }

    @Test
    fun mainThreadStaysResponsive() = runBlocking {
        val i = captureSpikeInputs()
        val main = Handler(Looper.getMainLooper())
        var onMain = true
        var latency = -1L
        val painting = withContext(Dispatchers.Default) {
            onMain = Looper.getMainLooper().isCurrentThread
            val posted = SystemClock.elapsedRealtime()
            val done = CountDownLatch(1)
            main.post { latency = SystemClock.elapsedRealtime() - posted; done.countDown() }
            paint(i).forEach { png(it) }
            done.await(5, TimeUnit.SECONDS)
        }
        println("SPIKE main drawThreadIsMain=$onMain postedRunnableLatencyMs=$latency")
        assertFalse(onMain)
        assertTrue(painting && latency in 0..99, "latency $latency ms")
    }
}
