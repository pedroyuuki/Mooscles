package com.mooscles.screens.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.DayOfWeek

class WeekStartTest {
    @Test
    fun brazilUsAndCanadaStartOnSunday() {
        for (tag in listOf("pt-BR", "en-US", "en-CA")) {
            assertEquals(DayOfWeek.SUNDAY, WeekStart.of(tag), tag)
        }
    }

    @Test
    fun mostOfEuropeStartsOnMonday() {
        // pt-PT is decided by CLDR (checked against the JVM), not by the language.
        for (tag in listOf("de-DE", "fr-FR", "en-GB")) {
            assertEquals(DayOfWeek.MONDAY, WeekStart.of(tag), tag)
        }
    }

    @Test
    fun aSaturdayRegion() {
        assertEquals(DayOfWeek.SATURDAY, WeekStart.of("ar-EG"))
    }

    @Test
    fun withoutRegionPortugueseIsSundayOthersMonday() {
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("pt"))
        assertEquals(DayOfWeek.MONDAY, WeekStart.of("en"))
    }

    @Test
    fun unknownRegionFallsBackByLanguage() {
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("pt-ZZ"))
        assertEquals(DayOfWeek.MONDAY, WeekStart.of("en-ZZ"))
    }

    @Test
    fun scriptAndExtensionsDoNotHideTheRegion() {
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("zh-Hant-TW"))
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("pt_BR"))
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("en-US-u-ca-gregory"))
    }
}
