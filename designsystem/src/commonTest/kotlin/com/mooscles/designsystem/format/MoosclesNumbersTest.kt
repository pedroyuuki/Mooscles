package com.mooscles.designsystem.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoosclesNumbersTest {
    @Test
    fun localizesEnglish() {
        val cases = mapOf(
            "82.5" to "82.5", "6840" to "6,840", "1234.75" to "1,234.75",
            "-2.5" to "-2.5", "0" to "0", "100000" to "100,000",
        )
        for ((plain, expected) in cases) assertEquals(expected, MoosclesNumbers.localize(plain, NumberLocale.EN), plain)
    }

    @Test
    fun localizesPortuguese() {
        val cases = mapOf("82.5" to "82,5", "6840" to "6.840", "1234.75" to "1.234,75", "999" to "999")
        for ((plain, expected) in cases) assertEquals(expected, MoosclesNumbers.localize(plain, NumberLocale.PT_BR), plain)
    }

    @Test
    fun weightUsesNoBreakSpace() {
        assertEquals("82,5 kg", MoosclesNumbers.weight("82.5", "kg", NumberLocale.PT_BR))
        assertEquals("82.5 kg", MoosclesNumbers.weight("82.5", "kg", NumberLocale.EN))
    }

    @Test
    fun setTextUsesTheMultiplicationSign() {
        assertEquals("87,5 kg × 6", MoosclesNumbers.setText("87.5", "kg", 6, NumberLocale.PT_BR))
    }

    @Test
    fun clockPadsSeconds() {
        assertEquals("0:00", MoosclesNumbers.clock(0))
        assertEquals("1:24", MoosclesNumbers.clock(84))
        assertEquals("9:59", MoosclesNumbers.clock(599))
    }

    @Test
    fun localeFromTag() {
        for (tag in listOf("pt-BR", "pt", "pt-PT")) assertEquals(NumberLocale.PT_BR, NumberLocale.from(tag), tag)
        for (tag in listOf("en-US", "de-DE", "")) assertEquals(NumberLocale.EN, NumberLocale.from(tag), tag)
    }

    @Test
    fun rejectsNonPlainInput() {
        for (input in listOf("1,5", "1e3", "")) {
            assertFailsWith<IllegalArgumentException>(input) { MoosclesNumbers.localize(input, NumberLocale.EN) }
        }
    }
}
