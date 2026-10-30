package com.mooscles.screens.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performTextInput
import com.mooscles.designsystem.components.tabTag
import com.mooscles.screens.exercise.CustomExerciseFormTags
import com.mooscles.screens.exercise.ExerciseInfoRoute
import com.mooscles.screens.exercise.ExerciseInfoScreenTag
import com.mooscles.screens.exercise.ExerciseInfoTags
import com.mooscles.screens.library.LibraryFilters
import com.mooscles.screens.library.LibraryModel
import com.mooscles.screens.library.LibraryRoute
import com.mooscles.screens.library.LibraryScreenTag
import com.mooscles.screens.library.LibraryTags
import com.mooscles.screens.shell.AppShellBottom
import com.mooscles.screens.shell.ShellTabs
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mooscles.data.WorkoutRepository
import com.mooscles.designsystem.components.ExerciseAddSetTag
import com.mooscles.designsystem.components.ExerciseHeaderToggleTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.components.StepperDecreaseTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.TimerDockTag
import com.mooscles.designsystem.components.TimerPlusTag
import com.mooscles.designsystem.components.TimerSkipTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.domain.currentTimeMillis
import com.mooscles.screens.flow.FlowApp
import com.mooscles.screens.flow.FlowHarness
import com.mooscles.screens.flow.FlowViewModel
import com.mooscles.screens.flow.active
import com.mooscles.screens.flow.await
import com.mooscles.screens.flow.check
import com.mooscles.screens.flow.exists
import com.mooscles.screens.flow.field
import com.mooscles.screens.flow.row
import com.mooscles.screens.flow.scrollToRow
import com.mooscles.screens.flow.set
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.ActiveWorkoutTags
import com.mooscles.screens.workout.WorkoutHeaderTags
import com.mooscles.screens.workout.exerciseColumnsTag
import com.mooscles.screens.workout.exerciseFooterTag
import com.mooscles.screens.workout.exerciseHeaderTag
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The spec's tap budget ("Orçamento de toques"): each flow of its table on the new workout screen,
 * over the real stack ([FlowHarness]: the common view model, Room and DataStore), counted with the
 * counter of Task 24 ([Taps]: a tap or a gesture counts 1, and so would each typed character;
 * scrolling to a control and opening the workout do not count). Each flow checks what it saved,
 * from the view model's state (which comes from Room), and that its count is within the spec's
 * "Novo" column ([New]) and within today's count on the old screen ([Today]); the one exception is
 * removing a set by its sheet, the accessible alternative to the swipe, held to "Novo" only
 * (spec: "O de remover pela sheet (2) é a alternativa acessível do deslize (1)").
 *
 * Every flow starts from a bench press with two sets and a finished workout a week ago with two
 * sets of 80 kg × 8 on it, the record the routine's sets take as their target ([seed]).
 *
 * Plus the phase 4A library's two flows of its spec table (docs/superpowers/specs/2026-10-29-phase4-library-routines-design.md,
 * "Orçamento de toques"), over the same stack with the whole catalog, the library and the exercise
 * detail hosted as the app does under the tab bar ([LibraryApp]): finding an exercise by its muscle
 * and opening it, from another tab, and creating a custom exercise with a name, a muscle and an
 * equipment, from the library (the typed name does not count). Each is held to the spec's "Novo"
 * ([LibraryNew]) and to today's count on the old library ([LibraryToday]).
 */
@OptIn(ExperimentalTestApi::class)
class TapBudgetTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    /**
     * Today's counts, measured on the old workout screen before the swap (Task 24, at `bb3d633`):
     * docs/superpowers/reports/2026-10-29-phase3-verificacao/tap-baseline.md
     */
    private object Today {
        const val PREFILLED = 1
        const val SAME_AS_LAST_WEEK = 6
        const val LOAD_STEP_UP = 8
        const val REP_LESS = 4
        const val ADD_SET = 4
        const val WARMUP = 2
        const val SKIP = 1
        const val PLUS_FIFTEEN = 1
        const val FINISH = 2
    }

    /** The spec's "Novo" column (docs/superpowers/specs/2026-10-29-phase3-active-workout-design.md, "Orçamento de toques"). */
    private object New {
        const val PREFILLED = 1
        const val SAME_AS_LAST_WEEK = 1
        const val LOAD_STEP_UP = 3
        const val REP_LESS = 3
        const val ADD_SET = 2
        const val WARMUP = 2
        const val REMOVE_BY_SWIPE = 1
        const val REMOVE_BY_SHEET = 2
        const val SKIP = 1
        const val PLUS_FIFTEEN = 1
        const val FINISH = 2
    }

    /**
     * Today's counts on the old library (Task 19, at `9112a7d`, before the swap):
     * docs/superpowers/reports/2026-10-29-phase4a-verificacao/tap-baseline-4a.md
     */
    private object LibraryToday {
        const val FIND_BY_MUSCLE_AND_OPEN = 4
        const val CREATE_CUSTOM_EXERCISE = 6
    }

    /** The spec's "Novo" column (docs/superpowers/specs/2026-10-29-phase4-library-routines-design.md, "Orçamento de toques"). */
    private object LibraryNew {
        const val FIND_BY_MUSCLE_AND_OPEN = 3
        const val CREATE_CUSTOM_EXERCISE = 4
    }

    /** The user's touches: a tap or a gesture counts 1. Each waits for the screen to settle. */
    private class Taps(private val test: ComposeUiTest) {
        var count = 0
            private set

        fun tap(node: SemanticsNodeInteraction) {
            node.performClick()
            count++
            test.waitForIdle()
        }

        fun gesture(node: SemanticsNodeInteraction, block: TouchInjectionScope.() -> Unit) {
            node.performTouchInput(block)
            count++
            test.waitForIdle()
        }
    }

    private fun assertWithin(flow: String, taps: Taps, new: Int, today: Int? = null) {
        println("TAP budget $flow: ${taps.count} (new $new, today ${today ?: "-"})")
        assertTrue(taps.count <= new, "$flow took ${taps.count} taps, the spec's budget is $new")
        if (today != null) assertTrue(taps.count <= today, "$flow took ${taps.count} taps, today it takes $today")
    }

    // --- The flows ------------------------------------------------------------------------------

    @Test
    fun prefilledSet() = budget { vm, ids ->
        val taps = Taps(this)
        taps.tap(check(ids[0]))
        await("set 1 is saved done") { set(vm, ids[0])?.completed == true }
        assertEquals(8 to 80.0, set(vm, ids[0])!!.let { it.reps to it.weightKg })
        assertWithin("prefilledSet", taps, New.PREFILLED, Today.PREFILLED)
    }

    @Test
    fun sameAsLastWeekWithoutTarget() = budget(targets = false) { vm, ids ->
        // No target: the set opens empty, with last week's 80 kg × 8 as its suggestion.
        assertEquals(0 to 0.0, set(vm, ids[0])!!.let { it.reps to it.weightKg })
        val taps = Taps(this)
        taps.tap(check(ids[0]))
        await("set 1 is saved done") { set(vm, ids[0])?.completed == true }
        assertEquals(8 to 80.0, set(vm, ids[0])!!.let { it.reps to it.weightKg })
        assertWithin("sameAsLastWeekWithoutTarget", taps, New.SAME_AS_LAST_WEEK, Today.SAME_AS_LAST_WEEK)
    }

    @Test
    fun oneLoadStepUpAndComplete() = budget { vm, ids ->
        val taps = Taps(this)
        taps.tap(field(ids[0], SetRowLoadTag))
        taps.tap(onNodeWithTag(StepperIncreaseTag))
        await("the load is saved at 82.5") { set(vm, ids[0])?.weightKg == 82.5 }
        taps.tap(check(ids[0]))
        await("set 1 is saved done") { set(vm, ids[0])?.completed == true }
        assertEquals(8 to 82.5, set(vm, ids[0])!!.let { it.reps to it.weightKg })
        assertWithin("oneLoadStepUpAndComplete", taps, New.LOAD_STEP_UP, Today.LOAD_STEP_UP)
    }

    @Test
    fun oneRepLessAndComplete() = budget { vm, ids ->
        val taps = Taps(this)
        taps.tap(field(ids[0], SetRowRepsTag))
        taps.tap(onNodeWithTag(StepperDecreaseTag))
        await("the reps are saved at 7") { set(vm, ids[0])?.reps == 7 }
        taps.tap(check(ids[0]))
        await("set 1 is saved done") { set(vm, ids[0])?.completed == true }
        assertEquals(7 to 80.0, set(vm, ids[0])!!.let { it.reps to it.weightKg })
        assertWithin("oneRepLessAndComplete", taps, New.REP_LESS, Today.REP_LESS)
    }

    /**
     * Set 1 is done and set 2 still to do, so the bench is the current exercise and its card is
     * open: "Add set" gives a set 3 with the last set's load and no reps, whose suggestion is the
     * done set above (80 × 8), and its check saves that.
     */
    @Test
    fun addSetLikeAboveAndComplete() = budget(done = 1) { vm, ids ->
        val entryId = active(vm).exercises.single().id
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(exerciseFooterTag(entryId)))
        waitForIdle()
        val taps = Taps(this)
        taps.tap(onNode(hasTestTag(ExerciseAddSetTag) and hasAnyAncestor(hasTestTag(exerciseFooterTag(entryId)))))
        await("set 3 is added") { active(vm).exercises.single().sets.size == 3 }
        val added = active(vm).exercises.single().sets.last().id
        scrollToRow(added)
        taps.tap(check(added))
        await("set 3 is saved done") { set(vm, added)?.completed == true }
        assertEquals(8 to 80.0, set(vm, added)!!.let { it.reps to it.weightKg })
        assertWithin("addSetLikeAboveAndComplete", taps, New.ADD_SET, Today.ADD_SET)
    }

    /**
     * The same, once the bench's last set is done: its card has collapsed (D9), so it opens first.
     * One tap over the spec's "Novo" (2), still within today's (4): measured and reported, not a
     * budget of its own in the spec's table.
     */
    @Test
    fun addSetAfterTheLastSetOpensTheCardFirst() = budget(done = 2) { vm, _ ->
        val entryId = active(vm).exercises.single().id
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(exerciseHeaderTag(entryId)))
        waitForIdle()
        assertTrue(!exists(hasTestTag(exerciseColumnsTag(entryId)), unmerged = true), "the done card is open")
        val taps = Taps(this)
        taps.tap(onNode(hasTestTag(ExerciseHeaderToggleTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag(entryId)))))
        await("the card opens") { exists(hasTestTag(exerciseColumnsTag(entryId)), unmerged = true) }
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(exerciseFooterTag(entryId)))
        waitForIdle()
        taps.tap(onNode(hasTestTag(ExerciseAddSetTag) and hasAnyAncestor(hasTestTag(exerciseFooterTag(entryId)))))
        await("set 3 is added") { active(vm).exercises.single().sets.size == 3 }
        val added = active(vm).exercises.single().sets.last().id
        scrollToRow(added)
        taps.tap(check(added))
        await("set 3 is saved done") { set(vm, added)?.completed == true }
        assertEquals(8 to 80.0, set(vm, added)!!.let { it.reps to it.weightKg })
        println("TAP budget addSetAfterTheLastSet: ${taps.count} (new ${New.ADD_SET} with the card open, today ${Today.ADD_SET})")
        assertEquals(New.ADD_SET + 1, taps.count)
        assertTrue(taps.count <= Today.ADD_SET, "adding a set after the last one took ${taps.count} taps, today it takes ${Today.ADD_SET}")
    }

    @Test
    fun markWarmup() = budget { vm, ids ->
        val taps = Taps(this)
        taps.gesture(onNode(row(ids[0]))) { click(Offset(width * NUMBER_X, NUMBER_Y.dp.toPx())) }
        taps.tap(onNode(hasText("Warm-up") and hasAnyAncestor(hasTestTag(SHEET))))
        await("set 1 is saved as a warm-up") { set(vm, ids[0])?.warmup == true }
        assertEquals(false, set(vm, ids[0])!!.completed)
        assertWithin("markWarmup", taps, New.WARMUP, Today.WARMUP)
    }

    @Test
    fun removeSetBySwipe() = budget { vm, ids ->
        val taps = Taps(this)
        // Leftwards from its check, as a thumb does.
        taps.gesture(onNode(row(ids[0]))) {
            swipe(start = Offset(width * 0.97f, centerY), end = Offset(width * 0.5f, centerY), durationMillis = 400)
        }
        await("set 1 is removed") { set(vm, ids[0]) == null }
        assertEquals(listOf(ids[1]), active(vm).exercises.single().sets.map { it.id })
        assertWithin("removeSetBySwipe", taps, New.REMOVE_BY_SWIPE)
    }

    @Test
    fun removeSetBySheet() = budget { vm, ids ->
        val taps = Taps(this)
        taps.gesture(onNode(row(ids[0]))) { click(Offset(width * NUMBER_X, NUMBER_Y.dp.toPx())) }
        taps.tap(onNode(hasText("Remove set") and hasClickAction() and hasAnyAncestor(hasTestTag(SHEET))))
        await("set 1 is removed") { set(vm, ids[0]) == null }
        assertEquals(listOf(ids[1]), active(vm).exercises.single().sets.map { it.id })
        // The accessible alternative to the swipe: held to the spec's 2, not to today's 1 (the old X).
        assertWithin("removeSetBySheet", taps, New.REMOVE_BY_SHEET)
    }

    @Test
    fun skipRestAndPlusFifteen() = budget(done = 1) { vm, _ ->
        // A rest running, as after a check (not counted).
        vm.startTimer("Bench press", REST_SECONDS)
        await("the rest runs") { vm.timerUi.value.running }
        await("the dock shows") { exists(hasTestTag(TimerDockTag)) }
        val skip = Taps(this)
        skip.tap(onNode(hasTestTag(TimerSkipTag) and hasAnyAncestor(hasTestTag(TimerDockTag))))
        await("the rest is skipped") { !vm.timerUi.value.running && !vm.timerUi.value.paused }
        assertWithin("skipRest", skip, New.SKIP, Today.SKIP)

        vm.startTimer("Bench press", REST_SECONDS)
        await("the rest runs again") { vm.timerUi.value.running }
        await("the dock shows again") { exists(hasTestTag(TimerPlusTag) and hasAnyAncestor(hasTestTag(TimerDockTag))) }
        val before = vm.timerUi.value.remainingSeconds
        val plus = Taps(this)
        plus.tap(onNode(hasTestTag(TimerPlusTag) and hasAnyAncestor(hasTestTag(TimerDockTag))))
        await("+15 s is saved") { vm.timerUi.value.remainingSeconds >= before + 10 }
        assertWithin("plusFifteen", plus, New.PLUS_FIFTEEN, Today.PLUS_FIFTEEN)
    }

    /** From the header, where it always is: no scrolling, unlike today's button at the end of the list. */
    @Test
    fun finish() = budget(done = 1) { vm, _ ->
        val workoutId = active(vm).id
        val taps = Taps(this)
        taps.tap(onNodeWithTag(WorkoutHeaderTags.FINISH))
        await("the finish dialog opens") { exists(hasClickAction() and hasText("Finish") and hasAnyAncestor(hasTestTag(DIALOG))) }
        taps.tap(onNode(hasClickAction() and hasText("Finish") and hasAnyAncestor(hasTestTag(DIALOG))))
        await("the workout is saved finished") { vm.contentUi.value.active == null && vm.contentUi.value.history.any { it.id == workoutId } }
        assertWithin("finish", taps, New.FINISH, Today.FINISH)
    }

    /**
     * From the Train tab: the Library tab, the "Back" chip, the first back exercise of the results;
     * its detail opens. Scrolling to the row does not count (it is the first result).
     */
    @Test
    fun findByMuscleAndOpen() = libraryBudget(ShellTabs.TRAIN) { vm ->
        val taps = Taps(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.LIBRARY)))
        await("the library's list is built") { exists(hasTestTag(LibraryTags.COUNT)) }
        taps.tap(onNodeWithTag(LibraryTags.muscleChip("Back")))
        val model = LibraryModel.build(vm.contentUi.value.exercises, portuguese = false)
        val first = model.results("", LibraryFilters(muscles = setOf("Back"))).first()
        await("the back exercises are listed") { exists(hasTestTag(LibraryTags.row(first.id))) }
        onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasTestTag(LibraryTags.row(first.id)))
        waitForIdle()
        taps.tap(onNodeWithTag(LibraryTags.row(first.id)))
        await("the detail opens") { exists(hasTestTag(ExerciseInfoScreenTag)) }
        assertEquals("Back", first.muscle)
        assertEquals(model.shownName(first), onNodeWithTag(ExerciseInfoTags.TITLE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text })
        assertWithin("findByMuscleAndOpen", taps, LibraryNew.FIND_BY_MUSCLE_AND_OPEN, LibraryToday.FIND_BY_MUSCLE_AND_OPEN)
    }

    /**
     * From the library: "Create exercise", the name typed (not counted, nor the focus it takes),
     * "Chest", "Dumbbell" (the convention takes its default, "Per hand") and "Save"; the new
     * exercise is saved and its detail opens.
     */
    @Test
    fun createCustomExerciseWithNameMuscleAndEquipment() = libraryBudget(ShellTabs.LIBRARY) { vm ->
        await("the library's list is built") { exists(hasTestTag(LibraryTags.COUNT)) }
        val taps = Taps(this)
        taps.tap(onNodeWithTag(LibraryTags.CREATE))
        await("the form opens") { exists(hasTestTag(CustomExerciseFormTags.ROOT)) }
        onNodeWithTag(CustomExerciseFormTags.NAME).performTextInput("Machine row")
        waitForIdle()
        taps.tap(onNodeWithTag(CustomExerciseFormTags.muscle("Chest")))
        taps.tap(onNodeWithTag(CustomExerciseFormTags.equipment("Dumbbell")))
        taps.tap(onNodeWithTag(CustomExerciseFormTags.SAVE))
        await("the exercise is saved") { vm.contentUi.value.exercises.any { it.custom && it.name == "Machine row" } }
        val saved = vm.contentUi.value.exercises.single { it.custom && it.name == "Machine row" }
        assertEquals(listOf("Chest", "Dumbbell", "Per hand"), listOf(saved.muscle, saved.equipment, saved.convention))
        await("its detail opens") { exists(hasTestTag(ExerciseInfoScreenTag)) && !exists(hasTestTag(CustomExerciseFormTags.ROOT)) }
        assertEquals("Machine row", onNodeWithTag(ExerciseInfoTags.TITLE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text })
        assertWithin("createCustomExerciseWithNameMuscleAndEquipment", taps, LibraryNew.CREATE_CUSTOM_EXERCISE, LibraryToday.CREATE_CUSTOM_EXERCISE)
    }

    // --- The stack ------------------------------------------------------------------------------

    /**
     * Seeds the stack ([seed]), opens the workout screen as the app hosts it ([FlowApp]) in English
     * at 1.0, waits for the workout, and runs [flow] with the view model and the bench's set ids.
     */
    private fun budget(
        targets: Boolean = true,
        done: Int = 0,
        flow: suspend ComposeUiTest.(vm: FlowViewModel, setIds: List<String>) -> Unit,
    ) = runComposeUiTest(testTimeout = 2.minutes) {
        val harness = FlowHarness()
        try {
            seed(harness.repository, targets, done)
            val vm = harness.createViewModel()
            withScreen(locale = "en") { FlowApp(vm) }
            waitForIdle()
            await("the workout opens") { !vm.contentUi.value.loading && vm.contentUi.value.active != null }
            waitForIdle()
            val ids = active(vm).exercises.single().sets.map { it.id }
            assertEquals(2, ids.size)
            if (done < ids.size) scrollToRow(ids[done])
            flow(vm, ids)
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    /**
     * Seeds the catalog, opens the library as the app hosts it ([LibraryApp]) in English at 1.0 on
     * [tab], waits for the state, and runs [flow] with the view model.
     */
    private fun libraryBudget(tab: String, flow: suspend ComposeUiTest.(vm: FlowViewModel) -> Unit) = runComposeUiTest(testTimeout = 2.minutes) {
        val harness = FlowHarness()
        try {
            harness.repository.initialize()
            val vm = harness.createViewModel()
            withScreen(locale = "en") { LibraryApp(vm, tab) }
            waitForIdle()
            await("the state loads") { !vm.contentUi.value.loading && vm.contentUi.value.exercises.isNotEmpty() }
            waitForIdle()
            flow(vm)
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    /**
     * The library and the exercise detail as `:app` hosts them (`MoosclesApp`), without its NavHost:
     * the tab's content over the shell's bottom (the tab bar), starting on [startTab]; the Library
     * tab shows [LibraryRoute], a row opens [ExerciseInfoRoute] in its place, and choosing a tab
     * leaves the detail. Clear of the system bars and the keyboard, as the app's window is.
     */
    @Composable
    private fun LibraryApp(viewModel: FlowViewModel, startTab: String) {
        val state by viewModel.contentUi.collectAsState()
        val timer = viewModel.timerUi.collectAsState()
        var tab by rememberSaveable { mutableStateOf(startTab) }
        var detail by rememberSaveable { mutableStateOf<String?>(null) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val open = detail
                when {
                    open != null -> ExerciseInfoRoute(
                        exerciseId = open,
                        state = state,
                        actions = viewModel,
                        onBack = { detail = null },
                        onOpenHistory = {},
                        onOpenRoutine = {},
                        onOpenWorkout = {},
                    )
                    tab == ShellTabs.LIBRARY -> LibraryRoute(state, viewModel, onOpenExercise = { detail = it }, onOpenSettings = {})
                    else -> Box(Modifier.fillMaxSize())
                }
            }
            AppShellBottom(
                state = state,
                timer = timer,
                selectedTab = tab,
                onSelectTab = {
                    tab = it
                    detail = null
                },
                onReselectTab = { detail = null },
                onOpenWorkout = {},
                actions = viewModel,
                imeInsets = WindowInsets(0, 0, 0, 0),
            )
        }
    }

    /**
     * A finished workout a week ago with two sets of 80 kg × 8 on the bench, then the workout in
     * progress: from the routine "Push day" (the bench, whose two sets take that record as their
     * target, so they open at 80 × 8) with [targets], else an empty workout with the bench added
     * (two empty sets). The first [done] sets are already done, at 80 × 8.
     */
    private suspend fun seed(repository: WorkoutRepository, targets: Boolean, done: Int) {
        repository.initialize()
        val past = currentTimeMillis() - WEEK_MS
        val old = repository.startEmpty("Push", now = past)
        repository.addWorkoutExercise(old, BENCH)
        repository.workoutSets(old).forEach { repository.updateSet(it.copy(reps = 8, weightKg = 80.0, completed = true)) }
        check(repository.finishWorkout(old, now = past + HOUR_MS))
        val workout = if (targets) {
            val routine = repository.createRoutine("Push day")
            repository.addRoutineExercise(routine, BENCH)
            repository.startRoutine(routine)
        } else {
            repository.startEmpty("Push day").also { repository.addWorkoutExercise(it, BENCH) }
        }
        repository.workoutSets(workout).sortedBy { it.position }.take(done).forEach {
            repository.updateSet(it.copy(reps = 8, weightKg = 80.0, completed = true))
        }
    }

    private companion object {
        const val BENCH = "catalog_barbell_bench_flat"
        const val HOUR_MS = 3_600_000L
        const val WEEK_MS = 7 * 24 * HOUR_MS
        const val REST_SECONDS = 90

        /**
         * Where the set's number is: near the row's start, in its first 56 dp (the row may have
         * two lines, the number on the first). A tap there opens the set type sheet.
         */
        const val NUMBER_X = 0.05f
        const val NUMBER_Y = 28

        // The design system's internal tags (DialogTag, SheetTag).
        const val DIALOG = "dialog"
        const val SHEET = "sheet"
    }
}
