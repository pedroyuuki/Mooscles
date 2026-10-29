package com.mooscles.screens.format

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.withScreen
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DateTextsTest {
    private val utc = TimeZone.UTC
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime(y, mo, d, h, mi).toInstant(utc).toEpochMilliseconds()

    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun shortDateBothLanguages() = runComposeUiTest {
        val ms = at(2026, 10, 21, 9, 0)
        var en = ""
        var pt = ""
        withScreen(locale = "en") { en = shortDate(ms, utc) }
        waitForIdle()
        withScreen(locale = "pt-BR") { pt = shortDate(ms, utc) }
        waitForIdle()
        assertEquals("Oct 21", en)
        assertEquals("21 out", pt)
    }

    // 2024-10-29 is a Tuesday (the plan's example date).
    @Test
    fun summaryRangePortuguese() = runComposeUiTest {
        var text = ""
        withScreen(locale = "pt-BR") { text = summaryDateRange(at(2024, 10, 29, 18, 2), at(2024, 10, 29, 18, 44), utc) }
        waitForIdle()
        assertEquals("ter., 29 out · 18:02–18:44", text)
    }

    @Test
    fun summaryRangeEnglish() = runComposeUiTest {
        var text = ""
        withScreen(locale = "en") { text = summaryDateRange(at(2024, 10, 29, 18, 2), at(2024, 10, 29, 18, 44), utc) }
        waitForIdle()
        assertEquals("Tue, Oct 29 · 18:02–18:44", text)
    }

    @Test
    fun durations() = runComposeUiTest {
        val texts = mutableListOf<String>()
        withScreen(locale = "en") { texts += durationText(2520); texts += durationText(4320) }
        waitForIdle()
        assertEquals(listOf("42 min", "1 h 12 min"), texts)
    }
}
