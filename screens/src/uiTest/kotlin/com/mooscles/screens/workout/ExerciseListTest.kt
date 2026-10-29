package com.mooscles.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.ExerciseCardCaptionTag
import com.mooscles.designsystem.components.ExerciseCardDoneTag
import com.mooscles.designsystem.components.ExerciseCardProgressTag
import com.mooscles.designsystem.components.ExerciseHeaderToggleTag
import com.mooscles.designsystem.components.ExerciseMenuTag
import com.mooscles.designsystem.components.PrBadgeTag
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PreviousSetUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.SetRecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The exercise cards of the active workout as lazy list items (spec "Lista", "Card do
 * exercício", D9): the header, the column header, the rows and the footer, the advance to the
 * next exercise and the exercise menu. The list is laid out as the screen lays it out, with the
 * advance and the outside-tap scope beside it.
 */
@OptIn(ExperimentalTestApi::class)
class ExerciseListTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Supino reto", "Chest", "Barbell", convention = "Total load")
    private val curl = ExerciseUi("curl", "Rosca alternada", "Biceps", "Dumbbell", convention = "Per hand")
    private val pullUp = ExerciseUi("apu", "Barra assistida", "Back", "Selectorized machine", convention = "Assistance")

    /**
     * Records every call and, like the database emission, writes `updateSet` into [exercises] and
     * drops the entry of `removeWorkoutExercise`.
     */
    private class Actions(
        val exercises: MutableState<List<WorkoutExerciseUi>>,
        val recording: RecordingActions = RecordingActions(),
    ) : MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

        override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) {
            recording.updateSet(setId, reps, weightKg, completed, warmup, assistance)
            exercises.value = exercises.value.map { entry ->
                entry.copy(
                    sets = entry.sets.map {
                        if (it.id == setId) it.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance) else it
                    },
                )
            }
        }

        override fun removeWorkoutExercise(entryId: String) {
            recording.removeWorkoutExercise(entryId)
            exercises.value = exercises.value.filter { it.id != entryId }
        }
    }

    private class Shown {
        lateinit var screen: ActiveWorkoutScreenState
        lateinit var list: LazyListState

        /** What the cards asked of the screen through their callbacks. */
        val callbacks = mutableListOf<String>()
    }

    /**
     * The cards of [exercises] in a lazy list with no spacing of its own, the advance beside it,
     * the outside-tap scope around it and the "How to log" sheet the screen opens.
     */
    private fun ComposeUiTest.showList(
        exercises: MutableState<List<WorkoutExerciseUi>>,
        actions: MoosclesActions,
        locale: String? = "pt-BR",
        reducedMotion: Boolean = false,
        pounds: Boolean = false,
        size: DpSize = DpSize(412.dp, 800.dp),
        probe: ((String) -> Unit)? = null,
    ): Shown {
        val shown = Shown()
        withScreen(locale = locale, reducedMotion = reducedMotion, size = size, probe = probe) {
            val screen = rememberActiveWorkoutScreenState()
            val list = rememberLazyListState()
            shown.screen = screen
            shown.list = list
            var howToLog by remember { mutableStateOf<WorkoutExerciseUi?>(null) }
            val callbacks = remember(screen) {
                ExerciseCallbacks(
                    onRestChip = { shown.callbacks += "rest:${it.id}" },
                    onSwap = { shown.callbacks += "swap:${it.id}" },
                    onHistory = { shown.callbacks += "history:$it" },
                    onHowToLog = { howToLog = it },
                    onChangeType = { set, _ -> screen.openTypeSheet(set.id) },
                    onRemoveSet = { screen.removeSet(it, actions) },
                )
            }
            val current = exercises.value
            Box(Modifier.fillMaxSize().endsSetEditingOnOutsideTap(screen)) {
                LazyColumn(
                    Modifier.fillMaxSize().testTag(LIST),
                    state = list,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    exerciseCards(current, pounds, screen, actions, callbacks)
                }
            }
            CardAdvance(current, screen, list)
            howToLog?.let { HowToLogSheet(it) { howToLog = null } }
        }
        waitForIdle()
        return shown
    }

    private fun sets(prefix: String, count: Int, done: Int = 0) =
        (1..count).map { SetUi("$prefix$it", reps = 6, weightKg = 80.0, completed = it <= done) }

    private fun row(id: String) = hasTestTag(workoutSetRowTag(id))

    private fun ComposeUiTest.header(entryId: String): SemanticsNodeInteraction = onNodeWithTag(exerciseHeaderTag(entryId))

    private fun ComposeUiTest.inHeader(entryId: String, tag: String): SemanticsNodeInteraction =
        onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag(entryId))), useUnmergedTree = true)

    private fun ComposeUiTest.toggle(entryId: String) {
        inHeader(entryId, ExerciseHeaderToggleTag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openMenu(entryId: String) {
        inHeader(entryId, ExerciseMenuTag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.check(id: String): SemanticsNodeInteraction = onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(row(id)))

    private fun ComposeUiTest.field(id: String, cell: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(cell)) and hasAnyAncestor(row(id)), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.editableText(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun ComposeUiTest.rowDescription(id: String): String =
        onNodeWithTag(workoutSetRowTag(id), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString(" ")

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag(LIST).performScrollToNode(hasTestTag(tag))
        waitForIdle()
    }

    /** Whether the rows (and footer) of [entryId] are in the list: the card is open. */
    private fun ComposeUiTest.isOpen(entryId: String): Boolean =
        onAllNodesWithTag(exerciseColumnsTag(entryId)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun largeExerciseComposesOnlyVisibleRows() = runComposeUiTest {
        val rows = HashSet<String>()
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 100))))
        val shown = showList(exercises, Actions(exercises), probe = { if (it.startsWith("row-")) rows += it })
        onNode(row("s1")).assertExists()
        onNode(row("s100")).assertDoesNotExist()
        assertTrue(rows.size in 1..20, "100 rows must not compose 100 rows: ${rows.size} composed")
        val seen = rows.size

        scrollTo(workoutSetRowTag("s100"))
        onNode(row("s100")).assertIsDisplayed()
        onNode(row("s1")).assertDoesNotExist()
        assertTrue(shown.list.firstVisibleItemIndex > 80, "the list really scrolled: ${shown.list.firstVisibleItemIndex}")
        assertTrue(rows.size > seen, "the rows scrolled to are composed and reported")
    }

    /** Review Focus 3: the same exercise twice keeps its own values, record and previous pairs. */
    @Test
    fun repeatedInstancesKeepTheirOwnValues() = runComposeUiTest {
        val previous = listOf(PreviousSetUi(80.0, 5, false, false), PreviousSetUi(82.5, 5, false, false))
        val record = SetRecordUi(RecordKind.WEIGHT, previousKg = 85.0)
        val exercises = mutableStateOf(
            listOf(
                WorkoutExerciseUi(
                    "e1", bench,
                    sets = listOf(SetUi("a1", reps = 5, weightKg = 90.0), SetUi("a2", reps = 5, weightKg = 90.0)),
                    previousSets = previous,
                ),
                WorkoutExerciseUi(
                    "e2", bench,
                    sets = listOf(SetUi("b1", reps = 5, weightKg = 90.0, completed = true, record = record), SetUi("b2", reps = 5, weightKg = 90.0)),
                    previousSets = previous,
                ),
            ),
        )
        val actions = Actions(exercises)
        showList(exercises, actions)
        // Both open by hand, so the advance leaves them open when the order changes.
        toggle("e2")
        toggle("e1")
        toggle("e1")

        // Each instance pairs the previous session with its own sets.
        assertTrue("80 quilos por 5" in rowDescription("a1"), rowDescription("a1"))
        assertTrue("82,5 quilos por 5" in rowDescription("a2"), rowDescription("a2"))
        scrollTo(workoutSetRowTag("b2"))
        assertTrue("80 quilos por 5" in rowDescription("b1"), rowDescription("b1"))
        assertTrue("82,5 quilos por 5" in rowDescription("b2"), rowDescription("b2"))
        // The record is the one the domain chose, not the equal set of the other instance.
        onNode(hasTestTag(PrBadgeTag) and hasAnyAncestor(row("b1")), useUnmergedTree = true).assertExists()
        scrollTo(workoutSetRowTag("a1"))
        assertEquals(0, onAllNodes(hasTestTag(PrBadgeTag) and hasAnyAncestor(row("a1")), useUnmergedTree = true).fetchSemanticsNodes().size)

        field("a1", SetRowRepsTag).performTextReplacement("12")
        waitForIdle()
        check("a1").performClick()
        waitForIdle()
        assertEquals(SetUi("a1", reps = 12, weightKg = 90.0, completed = true), exercises.value[0].sets[0])
        assertEquals(listOf(SetUi("b1", reps = 5, weightKg = 90.0, completed = true, record = record), SetUi("b2", reps = 5, weightKg = 90.0)), exercises.value[1].sets)

        // Reordered (as moving it does), each instance keeps its own rows.
        exercises.value = exercises.value.reversed()
        waitForIdle()
        scrollTo(workoutSetRowTag("b2"))
        assertEquals("5", field("b2", SetRowRepsTag).editableText())
        onNode(hasTestTag(PrBadgeTag) and hasAnyAncestor(row("b1")), useUnmergedTree = true).assertExists()
        scrollTo(workoutSetRowTag("a1"))
        assertEquals("12", field("a1", SetRowRepsTag).editableText())
        assertEquals("90", field("a1", SetRowLoadTag).editableText())
        assertEquals(0, onAllNodes(hasTestTag(PrBadgeTag) and hasAnyAncestor(row("a1")), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun metaLineIsLocalized() {
        for ((locale, texts) in listOf(
            "pt-BR" to listOf("Barra · Peito · série 4 de 5", "Halter · Bíceps", "0 de 3 séries"),
            "en" to listOf("Barbell · Chest · set 4 of 5", "Dumbbell · Biceps", "0 of 3 sets"),
        )) runComposeUiTest {
            val exercises = mutableStateOf(
                listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 5, done = 3)), WorkoutExerciseUi("e2", curl, sets = sets("c", 3))),
            )
            showList(exercises, Actions(exercises), locale = locale)
            onNode(hasText(texts[0]) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).assertIsDisplayed()
            // Collapsed: the meta without the progress, and the count of sets done under it.
            onNode(hasText(texts[1]) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e2"))), useUnmergedTree = true).assertIsDisplayed()
            assertEquals(texts[2], inHeader("e2", ExerciseCardProgressTag).text())
            assertEquals(0, onAllNodes(hasTestTag(ExerciseCardProgressTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun supportLineShowsRestChipAndPreviousDate() = runComposeUiTest {
        val zone = TimeZone.currentSystemDefault()
        val october21 = LocalDateTime(2026, 10, 21, 12, 0).toInstant(zone).toEpochMilliseconds()
        val exercises = mutableStateOf(
            listOf(
                WorkoutExerciseUi("e1", bench, sets = sets("s", 2), restSeconds = 120, previousDate = october21),
                WorkoutExerciseUi("e2", curl, sets = sets("c", 2), restSeconds = 90),
            ),
        )
        val shown = showList(exercises, Actions(exercises))
        onNode(hasText("Descanso 2:00") and hasAnyAncestor(hasTestTag(exerciseRestTag("e1"))), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Anterior: 21 out") and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(exerciseRestTag("e1")).performClick()
        waitForIdle()
        assertEquals(listOf("rest:e1"), shown.callbacks)

        toggle("e2")
        onNode(hasText("Descanso 1:30") and hasAnyAncestor(hasTestTag(exerciseRestTag("e2"))), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Primeira vez") and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e2"))), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun conventionCaptionOnlyForNonTotalLoad() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 1)), WorkoutExerciseUi("e2", curl, sets = sets("c", 1))),
        )
        showList(exercises, Actions(exercises))
        toggle("e2")
        assertEquals(0, onAllNodes(hasTestTag(ExerciseCardCaptionTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("Por mão: registre a carga de um halter.", inHeader("e2", ExerciseCardCaptionTag).text())
    }

    @Test
    fun columnHeaderIsHiddenFromScreenReaders() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 2))))
        showList(exercises, Actions(exercises))
        val columns = onNodeWithTag(exerciseColumnsTag("e1"), useUnmergedTree = true)
        columns.assertIsDisplayed()
        assertTrue(columns.fetchSemanticsNode().size.height > 0, "the column header is drawn")
        // The merged tree is what a screen reader walks: a cleared node's children are not in it.
        val inside = SemanticsMatcher("text") { it.config.getOrNull(SemanticsProperties.Text) != null } and
            hasAnyAncestor(hasTestTag(exerciseColumnsTag("e1")))
        assertEquals(0, onAllNodes(inside).fetchSemanticsNodes().size, "no column title reaches a screen reader")
        for (title in listOf("Série", "Anterior", "Reps", "kg")) {
            assertEquals(0, onAllNodes(hasText(title)).fetchSemanticsNodes().size, title)
        }
        // Drawn, though: the titles are there below the cleared node.
        assertEquals(4, onAllNodes(inside, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun columnUnitFollowsUnitAndConvention() {
        for (locale in listOf("pt-BR", "en")) runComposeUiTest {
            val units = mutableListOf<String>()
            withScreen(locale = locale) {
                for (convention in listOf(bench.convention, curl.convention, pullUp.convention)) {
                    for (pounds in listOf(false, true)) units += columnUnit(convention, pounds)
                }
            }
            waitForIdle()
            assertEquals(listOf("kg", "lb", "kg", "lb", "kg assist.", "lb assist."), units.take(6), locale)
        }
    }

    @Test
    fun completingTheLastPendingCollapsesAndOpensTheNext() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(
                WorkoutExerciseUi("e1", bench, sets = sets("s", 8, done = 7)),
                WorkoutExerciseUi("e2", curl, sets = sets("c", 6)),
                WorkoutExerciseUi("e3", pullUp, sets = sets("p", 2)),
            ),
        )
        val shown = showList(exercises, Actions(exercises), size = DpSize(412.dp, 640.dp))
        assertTrue(isOpen("e1"))
        assertFalse(isOpen("e2"))
        scrollTo(workoutSetRowTag("s8"))

        mainClock.autoAdvance = false
        check("s8").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(MoosclesTiming().cardAdvance - 100)
        // The check is seen first: the card waits before it collapses.
        assertTrue(isOpen("e1"), "e1 stays open during timing.cardAdvance")
        assertFalse(isOpen("e2"))

        mainClock.advanceTimeBy(200)
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
        assertFalse(isOpen("e1"))
        assertTrue(isOpen("e2"))
        assertFalse(isOpen("e3"))
        // The list scrolled to the header of the next exercise, right after the collapsed e1.
        assertEquals(1, shown.list.firstVisibleItemIndex)
        assertEquals(0, shown.list.firstVisibleItemScrollOffset)
        scrollTo(exerciseHeaderTag("e1"))
        assertEquals("8 de 8 séries", inHeader("e1", ExerciseCardProgressTag).text())
        inHeader("e1", ExerciseCardDoneTag).assertExists()
    }

    @Test
    fun reducedMotionSwitchesInstantly() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(
                WorkoutExerciseUi("e1", bench, sets = sets("s", 8, done = 7)),
                WorkoutExerciseUi("e2", curl, sets = sets("c", 6)),
            ),
        )
        val shown = showList(exercises, Actions(exercises), reducedMotion = true, size = DpSize(412.dp, 640.dp))
        scrollTo(workoutSetRowTag("s8"))

        mainClock.autoAdvance = false
        check("s8").performClick()
        repeat(4) { mainClock.advanceTimeByFrame() }
        // Well under timing.cardAdvance: no wait and no scroll animation.
        assertFalse(isOpen("e1"))
        assertTrue(isOpen("e2"))
        assertEquals(1, shown.list.firstVisibleItemIndex)
        assertEquals(0, shown.list.firstVisibleItemScrollOffset)
        mainClock.autoAdvance = true
    }

    @Test
    fun manualToggleStaysPut() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(
                WorkoutExerciseUi("e1", bench, sets = sets("s", 1)),
                WorkoutExerciseUi("e2", curl, sets = sets("c", 2)),
                WorkoutExerciseUi("e3", pullUp, sets = sets("p", 1)),
            ),
        )
        val shown = showList(exercises, Actions(exercises))
        // The person opens e3, and opens and closes e2 again.
        toggle("e3")
        toggle("e2")
        toggle("e2")
        assertTrue(isOpen("e1"))
        assertFalse(isOpen("e2"))
        assertTrue(isOpen("e3"))

        check("s1").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(MoosclesTiming().cardAdvance + 100)
        waitForIdle()
        // e1 collapses on its own; e2, closed by hand, stays closed; e3, opened by hand, stays open.
        assertFalse(isOpen("e1"))
        assertFalse(isOpen("e2"))
        assertTrue(isOpen("e3"))
        assertEquals(0, shown.list.firstVisibleItemIndex, "nothing opened, so the list does not move")

        // A tap on a header the advance closed opens it again.
        toggle("e1")
        assertTrue(isOpen("e1"))
    }

    @Test
    fun menuHasTheSixItems() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 1)), WorkoutExerciseUi("e2", curl, sets = sets("c", 1))),
        )
        showList(exercises, Actions(exercises))
        openMenu("e1")
        val labels = listOf(
            "Histórico do exercício", "Trocar exercício", "Mover para cima", "Mover para baixo", "Como registrar", "Remover exercício",
        )
        val tops = ExerciseMenuTags.all.mapIndexed { i, tag ->
            val item = onNodeWithTag(tag)
            item.assertIsDisplayed()
            assertEquals(labels[i], onNode(hasText(labels[i]) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).text())
            item.fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops, "in this order")
        // The first exercise cannot move up; the others can move.
        onNodeWithTag(ExerciseMenuTags.MOVE_UP).assertIsNotEnabled()
        onNodeWithTag(ExerciseMenuTags.MOVE_DOWN).assertIsEnabled()

        // "How to log" opens the full explanation of the convention.
        onNodeWithTag(ExerciseMenuTags.HOW_TO_LOG).performClick()
        waitForIdle()
        onNodeWithTag(ExerciseMenuTags.HOW_TO_LOG).assertDoesNotExist()
        onNodeWithText("Como registrar").assertIsDisplayed()
        onNodeWithText("Carga total").assertIsDisplayed()
        onNodeWithText("Registre a carga externa total; volume = carga registrada × repetições.").assertIsDisplayed()
    }

    @Test
    fun removeExerciseConfirms() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 3)), WorkoutExerciseUi("e2", curl, sets = sets("c", 1))),
        )
        val actions = Actions(exercises)
        showList(exercises, actions)
        val text = "Remover Supino reto? As 3 séries dele saem deste treino."

        // "Keep training" leaves the exercise in the workout.
        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.REMOVE).performClick()
        waitForIdle()
        onNodeWithText(text).assertIsDisplayed()
        onNodeWithText("Continuar treinando").performClick()
        waitForIdle()
        onNodeWithText(text).assertDoesNotExist()
        assertEquals(emptyList(), actions.calls)

        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.REMOVE).performClick()
        waitForIdle()
        onNodeWithText("Remover").performClick()
        waitForIdle()
        assertEquals(listOf("removeWorkoutExercise:e1"), actions.calls)
        header("e1").assertDoesNotExist()
        onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    fun swapWithCompletedSetsConfirms() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 3, done = 1))))
        val actions = Actions(exercises)
        val shown = showList(exercises, actions)
        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.SWAP).performClick()
        waitForIdle()
        onNodeWithText("Trocar Supino reto? As séries registradas dele saem deste treino.").assertIsDisplayed()
        assertEquals(emptyList(), shown.callbacks, "the picker waits for the confirmation")
        onNodeWithText("Trocar").performClick()
        waitForIdle()
        assertEquals(listOf("swap:e1"), shown.callbacks)
        assertEquals(emptyList(), actions.calls, "the picker writes the swap, not the menu")
    }

    @Test
    fun swapWithoutCompletedSetsOpensThePickerDirectly() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 3))))
        val shown = showList(exercises, Actions(exercises))
        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.SWAP).performClick()
        waitForIdle()
        assertEquals(listOf("swap:e1"), shown.callbacks)
        onNodeWithText("Trocar Supino reto?", substring = true).assertDoesNotExist()
        onNodeWithTag(ExerciseMenuTags.SWAP).assertDoesNotExist()
    }

    @Test
    fun moveUpAndDownCallMove() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 1)), WorkoutExerciseUi("e2", curl, sets = sets("c", 1))),
        )
        val actions = Actions(exercises)
        showList(exercises, actions)
        openMenu("e2")
        onNodeWithTag(ExerciseMenuTags.MOVE_DOWN).assertIsNotEnabled()
        onNodeWithTag(ExerciseMenuTags.MOVE_UP).performClick()
        waitForIdle()
        assertEquals(listOf("moveWorkoutExercise:e2,-1"), actions.calls)
        onNodeWithTag(ExerciseMenuTags.MOVE_UP).assertDoesNotExist()

        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.MOVE_DOWN).performClick()
        waitForIdle()
        assertEquals(listOf("moveWorkoutExercise:e2,-1", "moveWorkoutExercise:e1,1"), actions.calls)
    }

    @Test
    fun historyCallsBackWithTheExerciseId() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 1))))
        val shown = showList(exercises, Actions(exercises))
        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.HISTORY).performClick()
        waitForIdle()
        assertEquals(listOf("history:bench"), shown.callbacks)
        onNodeWithTag(ExerciseMenuTags.HISTORY).assertDoesNotExist()
    }

    private companion object {
        const val LIST = "exercise-list"
    }
}
