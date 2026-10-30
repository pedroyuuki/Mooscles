package com.mooscles.screens.exercise

import com.mooscles.data.ExerciseCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reads the cue files of the repository (spec D8), as [com.mooscles.screens.StringsParityTest]
 * reads the string XML: the same exercises and counts in both languages, short cues, no orphan,
 * no exclamation, emoji or health promise, and every exercise of the covered families.
 */
class ExerciseCuesFilesTest {
    private val dir = File("src/commonMain/composeResources/files/exercise_cues")

    private fun text(name: String) = File(dir, name).readText()

    private val en by lazy { ExerciseCues.parse(text("en.json")) }
    private val pt by lazy { ExerciseCues.parse(text("pt-BR.json")) }
    private val both get() = listOf("en" to en, "pt-BR" to pt)

    private val stableIds = ExerciseCatalog.exercises.map { it.id.removePrefix("catalog_") }

    // Grows with each content batch (Tasks 12 and 13) until every catalog exercise is covered.
    private val coveredPrefixes = listOf("barbell_", "ez_bar_", "dumbbell_")

    @Test
    fun sameKeysAndCountsInBothLanguages() {
        assertEquals(en.keys, pt.keys)
        for ((id, cues) in en) assertEquals(cues.size, pt.getValue(id).size, "cue count of $id")
    }

    @Test
    fun atMostFourCuesOfAtMost110Characters() {
        for ((language, map) in both) for ((id, cues) in map) {
            assertTrue(cues.size <= 4, "$language $id has ${cues.size} cues")
            for (cue in cues) assertTrue(cue.length <= 110, "$language $id: \"$cue\" has ${cue.length} characters")
        }
    }

    @Test
    fun noEmptyCueAndNoOrphanKey() {
        val known = stableIds.toSet()
        for ((language, map) in both) {
            for ((id, cues) in map) {
                assertTrue(id in known, "$language $id is not a catalog exercise")
                assertTrue(cues.isNotEmpty(), "$language $id has an empty list")
                for (cue in cues) assertTrue(cue.isNotBlank() && cue == cue.trim(), "$language $id has a blank or untrimmed cue")
            }
        }
        // One key per line in the files, so a key written twice (which the parser would merge) shows here.
        for (name in listOf("en.json", "pt-BR.json")) {
            val keys = Regex("^\\s*\"([^\"]+)\"\\s*:", RegexOption.MULTILINE).findAll(text(name)).map { it.groupValues[1] }.toList()
            assertEquals(keys.size, keys.toSet().size, "$name repeats a key: ${keys.groupBy { it }.filter { it.value.size > 1 }.keys}")
        }
    }

    @Test
    fun noExclamationEmojiOrHealthClaims() {
        for ((language, map) in both) for ((id, cues) in map) for (cue in cues) {
            assertFalse('!' in cue, "$language $id has an exclamation mark: \"$cue\"")
            assertFalse(hasEmoji(cue), "$language $id has an emoji: \"$cue\"")
            assertFalse(HealthClaims.matches(cue), "$language $id makes a health claim: \"$cue\"")
        }
    }

    /** The filter itself (pre-flight F17): whole words only, so common gym words pass. */
    @Test
    fun healthClaimFilterMatchesWholeWordsOnly() {
        for (claim in listOf(
            "Cura a dor nas costas.", "Ajuda a curar o joelho.", "Bom tratamento para o ombro.", "Evita lesão.",
            "Previne lesões.", "Indicado pelo médico.", "Recomendação médica.", "Cures back pain.", "Treats knee pain.",
            "Prevents injury.", "Avoids injuries.", "A medical classic.",
        )) assertTrue(HealthClaims.matches(claim), "should flag \"$claim\"")
        for (fine in listOf(
            "Curl the bar up.", "Barbell curl.", "Secure the bar.", "Occurs at the top.", "Percurso curto e curva suave.",
            "Rosca curta.", "Lower the bar to the curve of your chest.", "Retreat the shoulders.",
        )) assertFalse(HealthClaims.matches(fine), "should pass \"$fine\"")
    }

    @Test
    fun familiesAreCovered() {
        val missing = stableIds.filter { id -> coveredPrefixes.any(id::startsWith) && id !in en }
        assertTrue(missing.isEmpty(), "No cues for $missing")
    }

    private fun hasEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp in 0x1F000..0x1FAFF || cp in 0x2190..0x21FF || cp in 0x2300..0x23FF || cp in 0x2600..0x27BF ||
                cp in 0x2900..0x297F || cp in 0x2B00..0x2BFF || cp == 0xFE0F || cp == 0x200D
            ) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** Medical or healing words, by whole word (spec D8: no health promise, no medical language). */
    private object HealthClaims {
        private val portuguese = Regex(
            "(?U)\\b(cura[rs]?|curam|curativ\\w*|trat(a|ar|am|amento|amentos)|les(ão|ões)|médic\\w*)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val english = Regex("(?U)\\b(cure[sd]?|curing|treat(s|ed|ing|ment|ments)?|injur(e|ed|es|y|ies)|medical\\w*)\\b", RegexOption.IGNORE_CASE)

        fun matches(text: String) = portuguese.containsMatchIn(text) || english.containsMatchIn(text)
    }
}
