package com.mooscles.screens.workout

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The exercise picker sheet (spec "Seletor de exercício"): search, "Recent" first, then all of them in alphabetical order. */
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
    ): Picks {
        val picks = Picks()
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
    fun rowsAreFiftySixWithMuscleAndEquipment() {
        for ((locale, meta) in listOf("pt-BR" to "Peito · Barra", "en" to "Chest · Barbell")) {
            runComposeUiTest {
                showPicker(locale = locale)
                val tag = exercisePickerRowTag(bench.id, recent = false)
                pickerRow(bench).assertHeightIsEqualTo(56.dp).assertHasClickAction().assertTextEquals("Supino reto", meta)
                onNode(hasText(meta) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertIsDisplayed()
                onNode(hasTestTag(ExercisePickerEquipmentIconTag) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertIsDisplayed()
                pickerRow(fly).assertHeightIsEqualTo(56.dp)
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
        // Then every exercise, the recent ones too, in alphabetical order: "Todos" scrolled to the top.
        onNodeWithTag(ExercisePickerListTag).performScrollToIndex(1 + 4)
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
}
