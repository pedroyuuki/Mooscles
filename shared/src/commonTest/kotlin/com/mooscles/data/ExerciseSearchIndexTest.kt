package com.mooscles.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseSearchIndexTest {
    private val catalog = ExerciseCatalog.exercises
    private val entries = catalog.map { SearchEntry(it.id, it.name, it.muscle.label, it.equipment.label) }
    private val index = ExerciseSearchIndex.build(entries)

    private fun found(query: String) = index.matching(query).toSet()

    @Test
    fun portugueseQueriesFindTheirExercises() {
        // (query, must contain, must not contain), stable ids without the prefix.
        val table = listOf(
            Triple("supino inclinado halter", "dumbbell_bench_incline", null),
            Triple("puxada", "cable_lat_pulldown_wide", null),
            Triple("lat pulldown", "cable_lat_pulldown_wide", null),
            Triple("mesa flexora", "selectorized_leg_curl_lying", "selectorized_leg_curl_seated"),
            Triple("cadeira flexora", "selectorized_leg_curl_seated", "selectorized_leg_curl_lying"),
            Triple("eleva", "dumbbell_raise_lateral", null),
            Triple("terra", "barbell_deadlift_conventional", null),
            Triple("rdl", "barbell_deadlift_romanian", null),
            Triple("voador", "selectorized_pec_fly", null),
            Triple("peito halter", "dumbbell_bench_flat", null),
        )
        table.forEach { (query, expected, excluded) ->
            val result = found(query)
            assertTrue("catalog_$expected" in result, "\"$query\" should find $expected")
            if (excluded != null) assertFalse("catalog_$excluded" in result, "\"$query\" should not find $excluded")
        }
    }

    @Test
    fun wordsOfTwoDifferentAliasesDoNotCombine() {
        // "Voador" is the Portuguese name and "Peck deck" an alias: no single candidate has both.
        assertTrue(index.matches("catalog_selectorized_pec_fly", "voador"))
        assertTrue(index.matches("catalog_selectorized_pec_fly", "peck deck"))
        assertFalse(index.matches("catalog_selectorized_pec_fly", "voador peck"))
    }

    @Test
    fun metadataWorksInBothLanguages() {
        assertTrue("catalog_cable_row_close_neutral" in found("costas polia"))
        assertTrue("catalog_cable_row_close_neutral" in found("back cable"))
    }

    @Test
    fun everyLegacyMatchStillMatches() {
        val phrases = catalog.map(Exercise::name)
        val words = phrases.flatMap { it.split(' ', '-', '(', ')', '/') }.filter(String::isNotBlank)
        // Both searches read a query only through ExerciseSearch.normalize, so one query per
        // normalized form loses nothing and keeps the run short on the iOS simulator.
        val queries = (phrases + words).distinctBy(ExerciseSearch::normalize)
        var legacyMatches = 0
        val lost = mutableListOf<String>()
        for (exercise in catalog) for (query in queries) {
            if (!ExerciseSearch.matches(exercise, query)) continue
            legacyMatches++
            if (!index.matches(exercise.id, query)) lost += "${exercise.id} / \"$query\""
        }
        // The check means something only if the old search matches often.
        assertTrue(legacyMatches > catalog.size, "only $legacyMatches legacy matches")
        assertTrue(lost.isEmpty(), "Found before, missed by the index: ${lost.take(20)}")
    }

    @Test
    fun customNamedLikeACatalogEntryIsFoundAlongsideIt() {
        val custom = SearchEntry("custom_supino", "Supino reto com barra", MuscleGroup.CHEST.label, Equipment.BARBELL.label)
        val withCustom = ExerciseSearchIndex.build(entries + custom)
        val result = withCustom.matching("supino reto")
        assertTrue("custom_supino" in result)
        assertTrue("catalog_barbell_bench_flat" in result)
    }

    @Test
    fun blankQueryMatchesAll() {
        val ids = entries.map(SearchEntry::id)
        assertEquals(ids, index.matching(""))
        assertEquals(ids, index.matching("   "))
        assertTrue(index.matches("catalog_barbell_bench_flat", " "))
    }

    @Test
    fun orderFollowsTheEntries() {
        val reversed = entries.reversed()
        val result = ExerciseSearchIndex.build(reversed).matching("supino")
        assertTrue(result.size > 1)
        assertEquals(reversed.map(SearchEntry::id).filter { it in result }, result)
    }

    @Test
    fun aliasKeysAreCatalogIds() {
        val ids = catalog.map(Exercise::id).toSet()
        val orphans = ExerciseAliases.portuguese.keys - ids
        assertTrue(orphans.isEmpty(), "Portuguese aliases for ids outside the catalog: $orphans")
        ExerciseAliases.portuguese.forEach { (id, aliases) ->
            assertTrue(aliases.isNotEmpty() && aliases.all(String::isNotBlank), "$id has an empty alias")
        }
    }

    @Test
    fun specAliasesAreListed() {
        // The gym words of spec D13, each an alias of (or part of an alias of) some exercise.
        val spec = listOf(
            "supino", "voador", "peck deck", "crucifixo", "puxada", "pulley", "remada baixa", "terra", "stiff",
            "RDL", "cadeira extensora", "mesa flexora", "cadeira flexora", "agachamento", "afundo", "passada",
            "búlgaro", "panturrilha", "rosca direta", "rosca martelo", "rosca scott", "tríceps testa",
            "tríceps corda", "desenvolvimento", "elevação lateral", "encolhimento", "elevação pélvica",
            "barra fixa", "paralelas", "flexão", "graviton", "hack",
        )
        val aliases = ExerciseAliases.portuguese.values.flatten().map(SearchFolding::fold)
        val missing = spec.filter { term -> aliases.none { SearchFolding.fold(term) in it } }
        assertTrue(missing.isEmpty(), "Spec aliases not listed: $missing")
    }
}
