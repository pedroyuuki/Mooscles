package com.mooscles.screens.workout

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mooscles.data.ExerciseCatalog
import com.mooscles.designsystem.components.ExerciseEquipmentIconTag
import com.mooscles.designsystem.components.ExerciseMediaFigureTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.library.LibraryModel
import com.mooscles.screens.library.LibraryModelCache
import com.mooscles.screens.library.LibraryTags
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The exercise picker sheet (spec "Seletor de exercício"): search, the library's filters, "Recent"
 * first, then all of them in alphabetical order of the shown name, each row with its 56 dp media.
 */
@OptIn(ExperimentalTestApi::class)
class ExercisePickerSheetTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Supino reto", "Chest", "Barbell")
    private val squat = ExerciseUi("squat", "Agachamento livre", "Quadriceps", "Barbell")
    private val row = ExerciseUi("row", "Remada curvada", "Back", "Barbell")
    private val curl = ExerciseUi("curl", "Rosca direta", "Biceps", "Dumbbell")
    private val fly = ExerciseUi("fly", "Crucifixo na polia", "Chest", "Cable")
    private val exercises = listOf(bench, squat, row, curl, fly)

    private fun workout(id: String, startedAt: Long, vararg exercises: ExerciseUi) =
        WorkoutUi(id, "Treino $id", startedAt, endedAt = startedAt + 1, exercises = exercises.mapIndexed { i, e -> WorkoutExerciseUi("$id-$i", e) })

    /** Newest first: Remada, Supino; then Agachamento and the curl under the name it had then. */
    private val history = listOf(
        workout("w2", startedAt = 200, row, bench),
        workout("w1", startedAt = 100, squat, curl.copy(name = "Rosca (nome antigo)"), bench),
    )

    private class Picks {
        val picked = mutableListOf<ExerciseUi>()
        var dismissed = 0
    }

    /** The picker while [open]; a dismissal closes it, as the screen does. */
    private fun ComposeUiTest.showPicker(
        history: MutableState<List<WorkoutUi>> = mutableStateOf(this@ExercisePickerSheetTest.history),
        open: MutableState<Boolean> = mutableStateOf(true),
        locale: String? = "pt-BR",
        restoration: ScreenRestoration? = null,
        exercises: List<ExerciseUi> = this@ExercisePickerSheetTest.exercises,
    ): Picks {
        val picks = Picks()
        // A fresh cache, and the model built on the test's own dispatcher: ready when the picker is idle.
        val cache = LibraryModelCache()
        withScreen(locale = locale, restoration = restoration) {
            if (open.value) {
                ExercisePickerSheet(
                    exercises = exercises,
                    history = history.value,
                    onPick = { picks.picked += it },
                    onDismiss = {
                        picks.dismissed++
                        open.value = false
                    },
                    cache = cache,
                    build = { list, portuguese -> LibraryModel.build(list, portuguese) },
                )
            }
        }
        waitForIdle()
        return picks
    }

    /** The row of [exercise] in "Recent" or in "All", scrolled into view. */
    private fun ComposeUiTest.pickerRow(exercise: ExerciseUi, recent: Boolean = false): SemanticsNodeInteraction {
        onNodeWithTag(ExercisePickerListTag).performScrollToNode(hasTestTag(exercisePickerRowTag(exercise.id, recent)))
        waitForIdle()
        return onNodeWithTag(exercisePickerRowTag(exercise.id, recent))
    }

    private fun ComposeUiTest.search(query: String) {
        onNode(hasSetTextAction()).performTextReplacement(query)
        waitForIdle()
    }

    private fun ComposeUiTest.top(node: SemanticsNodeInteraction): Dp = node.getUnclippedBoundsInRoot().top

    private fun ComposeUiTest.click(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    /** The ids of [among] that the results list (a filtered list fits the sheet; "All" with "Recentes" does not). */
    private fun ComposeUiTest.allRows(among: List<ExerciseUi> = exercises): Set<String> =
        among.map { it.id }.filter { onAllNodesWithTag(exercisePickerRowTag(it, recent = false)).fetchSemanticsNodes().isNotEmpty() }.toSet()

    private fun catalog(stableId: String): ExerciseUi {
        val entry = ExerciseCatalog.exercises.single { it.id == "catalog_$stableId" }
        return ExerciseUi(entry.id, entry.name, entry.muscle.label, entry.equipment.label, entry.convention.label)
    }

    @Test
    fun pickReportsTheExerciseAndCloses() = runComposeUiTest {
        val picks = showPicker()
        onNodeWithText("Buscar exercício").assertIsDisplayed()
        pickerRow(fly).performClick()
        waitForIdle()
        // The caller turns the pick into addWorkoutExercise or replaceWorkoutExercise (Task 19).
        assertEquals(listOf(fly), picks.picked)
        assertEquals(1, picks.dismissed)
        onNodeWithText("Buscar exercício").assertDoesNotExist()
        onNodeWithTag(ExercisePickerListTag).assertDoesNotExist()
    }

    @Test
    fun recentPickReportsTheCurrentExercise() = runComposeUiTest {
        val picks = showPicker()
        // "Recent" shows the exercise as the catalog has it now, not the history's old name.
        pickerRow(curl, recent = true).assertTextEquals("Rosca direta", "Bíceps · Halter")
        onNodeWithText("Rosca (nome antigo)", useUnmergedTree = true).assertDoesNotExist()
        pickerRow(curl, recent = true).performClick()
        waitForIdle()
        assertEquals(listOf(curl), picks.picked)
        assertEquals(1, picks.dismissed)
        onNodeWithTag(ExercisePickerListTag).assertDoesNotExist()
    }

    @Test
    fun rowsHoldTheMediaWithMuscleAndEquipment() {
        for ((locale, meta) in listOf("pt-BR" to "Peito · Barra", "en" to "Chest · Barbell")) {
            runComposeUiTest {
                showPicker(locale = locale)
                val tag = exercisePickerRowTag(bench.id, recent = false)
                // The library's row: the 56 dp media and 8 dp above and below it, so at least 72 dp.
                pickerRow(bench).assertHeightIsAtLeast(72.dp).assertHasClickAction()
                    .assertTextEquals("Supino reto", meta)
                onNode(hasText(meta) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertIsDisplayed()
                // Without a figure, the media tile shows the equipment's icon.
                onNode(hasTestTag(ExerciseEquipmentIconTag) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertIsDisplayed()
                pickerRow(fly).assertHeightIsAtLeast(56.dp)
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun recentsComeFirst() = runComposeUiTest {
        val history = mutableStateOf(this@ExercisePickerSheetTest.history)
        showPicker(history = history)
        // Measured without scrolling in between: the heading, the four recent rows and "Todos" fit at the top.
        val recentHeader = top(onNodeWithText("Recentes"))
        // Newest workout first, each exercise once.
        val recentRows = listOf(row, bench, squat, curl).map { top(onNodeWithTag(exercisePickerRowTag(it.id, recent = true))) }
        val allHeader = top(onNodeWithText("Todos"))
        assertTrue(recentHeader < recentRows.first(), "\"Recentes\" heads the list")
        assertEquals(recentRows.sorted(), recentRows, "recent rows in recency order")
        assertTrue(recentRows.last() < allHeader, "\"Todos\" comes after the recent ones")
        // Then every exercise, the recent ones too, in alphabetical order: "Todos" scrolled to the top
        // (after the filters, "Recentes" and its four rows).
        onNodeWithTag(ExercisePickerListTag).performScrollToIndex(1 + 1 + 4)
        waitForIdle()
        val allRows = listOf(squat, fly, row, curl, bench).map { top(onNodeWithTag(exercisePickerRowTag(it.id, recent = false))) }
        assertEquals(allRows.sorted(), allRows, "all rows in alphabetical order")
        assertEquals(5, allRows.distinct().size)
        // Without history there is no "Recentes".
        runOnIdle { history.value = emptyList() }
        waitForIdle()
        onNodeWithText("Recentes").assertDoesNotExist()
        onNodeWithTag(exercisePickerRowTag(row.id, recent = true)).assertDoesNotExist()
        onNodeWithText("Todos").assertIsDisplayed()
    }

    @Test
    fun searchFilters() = runComposeUiTest {
        showPicker()
        search("supino")
        // A search lists the matches only, without "Recentes".
        onNodeWithText("Recentes").assertDoesNotExist()
        pickerRow(bench).assertIsDisplayed()
        for (other in listOf(squat, row, curl, fly)) onNodeWithTag(exercisePickerRowTag(other.id, recent = false)).assertDoesNotExist()
        // The same search as the library: accents and case fold, muscle and equipment count.
        search("AGACHAMENTO LÍVRE")
        pickerRow(squat).assertIsDisplayed()
        onNodeWithTag(exercisePickerRowTag(bench.id, recent = false)).assertDoesNotExist()
        search("barbell")
        for (match in listOf(bench, squat, row)) pickerRow(match).assertIsDisplayed()
        onNodeWithTag(exercisePickerRowTag(curl.id, recent = false)).assertDoesNotExist()
        search("zzz")
        onNodeWithText("Nenhum exercício encontrado. Tente uma busca mais ampla.").assertIsDisplayed()
        onNodeWithText("Todos").assertDoesNotExist()
        search("")
        onNodeWithText("Recentes").assertIsDisplayed()
        pickerRow(curl, recent = true).assertIsDisplayed()
    }

    @Test
    fun searchSurvivesRecreation() = runComposeUiTest {
        // No forced language: the restoration tester loses the saved state when the language override rebuilds the tree.
        val restoration = screenRestoration()
        val picks = showPicker(locale = null, restoration = restoration)
        search("supino")
        pickerRow(bench).assertIsDisplayed()

        restoration.emulateSaveAndRestore()
        waitForIdle()
        assertEquals("supino", onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        pickerRow(bench).assertIsDisplayed()
        onNodeWithTag(exercisePickerRowTag(squat.id, recent = false)).assertDoesNotExist()
        onNodeWithTag(exercisePickerRowTag(row.id, recent = true)).assertDoesNotExist()
        assertEquals(emptyList(), picks.picked)
        assertEquals(0, picks.dismissed)
    }

    @Test
    fun muscleChipsFilterTheList() = runComposeUiTest {
        val picks = showPicker()
        // The library's chips, in the app's language, all off, above "Recentes".
        onNodeWithTag(LibraryTags.muscleChip("Chest")).assertTextEquals("Peito").assertIsOff()
        onNodeWithText("Recentes").assertIsDisplayed()
        assertTrue(top(onNodeWithTag(LibraryTags.muscleChip("Chest"))) < top(onNodeWithText("Recentes")), "the filters scroll with the list, on top")
        // A chip lists only its muscle, without "Recentes"; the picker has no "My exercises".
        click(LibraryTags.muscleChip("Chest"))
        onNodeWithTag(LibraryTags.muscleChip("Chest")).assertIsOn()
        onNodeWithText("Recentes").assertDoesNotExist()
        assertEquals(setOf(bench.id, fly.id), allRows())
        onNodeWithTag(LibraryTags.MINE_CHIP).assertDoesNotExist()
        // Two muscles: either one ("or"); with the search, both must hold ("and").
        click(LibraryTags.muscleChip("Back"))
        assertEquals(setOf(bench.id, fly.id, row.id), allRows())
        search("barra")
        assertEquals(setOf(bench.id, row.id), allRows())
        search("")
        // "Limpar filtros" turns them off and brings "Recentes" back.
        click(LibraryTags.CLEAR_FILTERS)
        onNodeWithTag(LibraryTags.muscleChip("Chest")).assertIsOff()
        onNodeWithTag(LibraryTags.CLEAR_FILTERS).assertDoesNotExist()
        onNodeWithText("Recentes").assertIsDisplayed()
        // A filter that keeps nothing says so.
        onNodeWithTag(LibraryTags.muscleChip("Shoulders")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Nenhum exercício encontrado. Tente uma busca mais ampla.").assertIsDisplayed()
        assertEquals(emptySet(), allRows())
        assertEquals(emptyList(), picks.picked)
        assertEquals(0, picks.dismissed)
    }

    @Test
    fun equipmentSheetFiltersFromThePicker() = runComposeUiTest {
        val picks = showPicker()
        onNodeWithTag(LibraryTags.EQUIPMENT_CHIP).assertTextEquals("Equipamento")
        click(LibraryTags.EQUIPMENT_CHIP)
        // The sheet opens over the picker, which stays.
        onNodeWithTag(LibraryTags.EQUIPMENT_SHEET).assertIsDisplayed()
        click(LibraryTags.equipmentOption("Dumbbell"))
        onNodeWithTag(LibraryTags.equipmentOption("Dumbbell")).assertIsOn()
        // Closed by its own scrim (the picker's is under it), the picker lists only the dumbbell exercise.
        onNode(hasTestTag(SHEET_SCRIM) and hasAnySibling(hasTestTag(LibraryTags.EQUIPMENT_SHEET))).performClick()
        waitForIdle()
        onNodeWithTag(LibraryTags.EQUIPMENT_SHEET).assertDoesNotExist()
        onNodeWithTag(ExercisePickerListTag).assertIsDisplayed()
        assertEquals(0, picks.dismissed)
        assertEquals(setOf(curl.id), allRows())
        // The chip says what it holds in words, not by its color alone.
        onNodeWithTag(LibraryTags.EQUIPMENT_CHIP)
            .assertTextEquals("Equipamento · 1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Halter"))
        // A pick from the filtered list is the usual one.
        pickerRow(curl).performClick()
        waitForIdle()
        assertEquals(listOf(curl), picks.picked)
        assertEquals(1, picks.dismissed)
    }

    @Test
    fun rowsShowTheFigureOrTheIcon() = runComposeUiTest {
        // A catalog exercise with a figure shows its still frame; a custom one, the equipment's icon.
        val pilot = catalog("barbell_bench_flat")
        val mine = ExerciseUi("custom-fly", "Crucifixo do Zé", "Chest", "Cable", custom = true)
        showPicker(exercises = listOf(pilot, mine), history = mutableStateOf(emptyList()))
        val pilotRow = hasAnyAncestor(hasTestTag(exercisePickerRowTag(pilot.id, recent = false)))
        pickerRow(pilot)
        onNode(hasTestTag(ExerciseMediaFigureTag) and pilotRow, useUnmergedTree = true).assertIsDisplayed().assertWidthIsEqualTo(56.dp)
        onNode(hasTestTag(ExerciseEquipmentIconTag) and pilotRow, useUnmergedTree = true).assertDoesNotExist()
        val mineRow = hasAnyAncestor(hasTestTag(exercisePickerRowTag(mine.id, recent = false)))
        pickerRow(mine)
        onNode(hasTestTag(ExerciseEquipmentIconTag) and mineRow, useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag(ExerciseMediaFigureTag) and mineRow, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun portugueseSearchFindsTheCatalog() = runComposeUiTest {
        // Stored in English ("Barbell bench press", "High-bar back squat", "Standing dumbbell curl").
        val benchPress = catalog("barbell_bench_flat")
        val highBar = catalog("barbell_back_squat_high_bar")
        val standingCurl = catalog("dumbbell_curl_standing")
        val catalog = listOf(benchPress, highBar, standingCurl)
        showPicker(exercises = catalog, history = mutableStateOf(emptyList()))
        // Shown, and in alphabetical order, by the Portuguese names: the reverse of the English order.
        pickerRow(benchPress).assertTextEquals("Supino reto com barra", "Peito · Barra")
        val tops = listOf(highBar, standingCurl, benchPress).map { top(pickerRow(it)) }
        assertEquals(tops.sorted(), tops, "Agachamento, Rosca, Supino")
        // The Portuguese name, an alias and the English name all find it.
        for (query in listOf("supino reto com barra", "SUPINO", "barbell bench")) {
            search(query)
            assertEquals(setOf(benchPress.id), allRows(catalog), query)
        }
        search("rosca direta")
        assertEquals(setOf(standingCurl.id), allRows(catalog))
        pickerRow(standingCurl).assertTextEquals("Rosca direta com halteres", "Bíceps · Halter")
        onNodeWithText("Standing dumbbell curl", useUnmergedTree = true).assertDoesNotExist()
    }

    private companion object {
        // The design system's internal tag of a sheet's scrim.
        const val SHEET_SCRIM = "sheet-scrim"
    }
}
