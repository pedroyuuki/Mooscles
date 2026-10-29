package com.mooscles.screens.workout

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.PrBadgeTag
import com.mooscles.designsystem.components.PrToastTag
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.assertShownWhole
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.RecordKind
import com.mooscles.ui.SetRecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reaction to a record on the active workout (spec D17, D18, decision 15, Review Focus 2):
 * the badge on the set the presentation chose, the `PrToast` at the top of the list and the
 * `PersonalRecord` haptic, played once per record and only for a set completed on this screen.
 */
@OptIn(ExperimentalTestApi::class)
class RecordReactionTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Supino reto", "Chest", "Barbell", convention = "Total load")
    private val weightRecord = SetRecordUi(RecordKind.WEIGHT, previousKg = 85.0)

    private fun workout(sets: List<SetUi>) = WorkoutUi(
        id = "w1",
        name = "Treino A",
        startedAt = START,
        exercises = listOf(WorkoutExerciseUi("e1", bench, sets)),
    )

    private fun stateOf(workout: WorkoutUi) = UiState(loading = false, active = workout, exercises = listOf(bench))

    /**
     * Records every call and echoes `updateSet` into [state], as the view model would, without a
     * record: the presentation's confirmation comes after, by [confirm].
     */
    private class Fake(initial: UiState, val recording: RecordingActions = RecordingActions()) : MoosclesActions by recording {
        val state: MutableState<UiState> = mutableStateOf(initial)

        private fun changeSets(change: (SetUi) -> SetUi) {
            val active = state.value.active ?: return
            state.value = state.value.copy(active = active.copy(exercises = active.exercises.map { e -> e.copy(sets = e.sets.map(change)) }))
        }

        override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) {
            recording.updateSet(setId, reps, weightKg, completed, warmup, assistance)
            changeSets { if (it.id == setId) it.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance) else it }
        }

        /** The presentation puts [record] on [setId] alone (null takes every record away), as `WorkoutCalculations` chose. */
        fun confirm(setId: String?, record: SetRecordUi?) {
            changeSets { it.copy(record = if (it.id == setId) record else null) }
        }

        /** The set [setId] changed elsewhere (another device of the same state, an edit). */
        fun change(setId: String, change: (SetUi) -> SetUi) {
            changeSets { if (it.id == setId) change(it) else it }
        }
    }

    private fun ComposeUiTest.show(
        fake: Fake,
        haptics: RecordingHaptics,
        locale: String? = "pt-BR",
        size: DpSize = DpSize(412.dp, 800.dp),
        restoration: ScreenRestoration? = null,
    ) {
        withScreen(locale = locale, size = size, haptics = haptics, restoration = restoration) {
            ActiveWorkoutRoute(
                state = fake.state.value,
                timer = remember { mutableStateOf(TimerUi()) },
                actions = fake,
                onMinimize = {},
                onOpenSummary = {},
                onExerciseHistory = {},
                onChooseRoutine = {},
                nowMillis = { START },
            )
        }
        waitForIdle()
    }

    private fun ComposeUiTest.row(id: String): SemanticsNodeInteraction = onNodeWithTag(workoutSetRowTag(id))

    private fun ComposeUiTest.check(id: String) {
        onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(hasTestTag(workoutSetRowTag(id)))).performClick()
        waitForIdle()
    }

    /** The badge in the row of [id]. */
    private fun ComposeUiTest.badge(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag(PrBadgeTag) and hasAnyAncestor(hasTestTag(workoutSetRowTag(id))), useUnmergedTree = true)

    private fun ComposeUiTest.toast(): SemanticsNodeInteraction = onNodeWithTag(PrToastTag)

    /** A text of the record toast. */
    private fun ComposeUiTest.toastText(text: String): SemanticsNodeInteraction =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(PrToastTag)), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "no text layout")
        return results.first()
    }

    @Test
    fun weightRecordShowsBadgeToastAndHaptic() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val fake = Fake(stateOf(workout(listOf(SetUi("s1", reps = 6, weightKg = 87.5), SetUi("s2", reps = 6, weightKg = 87.5)))))
        show(fake, haptics)
        check("s1")
        // The check vibrates at once; the record waits for the presentation.
        assertEquals(listOf(HapticEvent.SetCompleted), haptics.events.toList())
        toast().assertDoesNotExist()
        badge("s1").assertDoesNotExist()

        fake.confirm("s1", weightRecord)
        waitForIdle()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.PersonalRecord), haptics.events.toList())
        badge("s1").assertExists()
        badge("s2").assertDoesNotExist()
        toastText("Novo recorde de carga").assertExists()
        toastText("87,5${NBSP}kg × 6 · antes 85${NBSP}kg").assertExists()
        assertShownWhole(toastText("Novo recorde de carga").layout(), "title")
        assertShownWhole(toastText("87,5${NBSP}kg × 6 · antes 85${NBSP}kg").layout(), "detail")
        // At the top of the list, under the header.
        val header = onNodeWithTag(WorkoutHeaderTags.HEADER).getBoundsInRoot()
        val list = onNodeWithTag(ActiveWorkoutTags.LIST).getBoundsInRoot()
        val toast = toast().assertIsDisplayed().getBoundsInRoot()
        assertTrue(toast.top >= header.bottom - 0.5.dp, "the toast covers the header: $toast, $header")
        assertTrue(abs((toast.top - list.top).value) < 1f, "the toast is not at the top of the list: $toast, $list")
    }

    @Test
    fun repsRecordToastText() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val fake = Fake(stateOf(workout(listOf(SetUi("s1", reps = 8, weightKg = 80.0), SetUi("s2", reps = 8, weightKg = 80.0)))))
        show(fake, haptics)
        check("s1")
        fake.confirm("s1", SetRecordUi(RecordKind.REPS, previousReps = 6))
        waitForIdle()
        toastText("Novo recorde de repetições").assertExists()
        toastText("80${NBSP}kg × 8 · antes 80${NBSP}kg × 6").assertExists()
        assertShownWhole(toastText("80${NBSP}kg × 8 · antes 80${NBSP}kg × 6").layout(), "detail")
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.PersonalRecord), haptics.events.toList())
        badge("s1").assertExists()
    }

    @Test
    fun firstRecordDoesNothing() = runComposeUiTest {
        // An exercise without history: the presentation sends no record (D18), only firstRecord.
        val haptics = RecordingHaptics()
        val fake = Fake(stateOf(workout(listOf(SetUi("s1", reps = 6, weightKg = 87.5), SetUi("s2", reps = 6, weightKg = 87.5)))))
        show(fake, haptics)
        check("s1")
        fake.state.value = fake.state.value.copy(
            active = fake.state.value.active!!.let { w -> w.copy(exercises = w.exercises.map { it.copy(firstRecord = true) }) },
        )
        waitForIdle()
        assertEquals(listOf(HapticEvent.SetCompleted), haptics.events.toList())
        toast().assertDoesNotExist()
        badge("s1").assertDoesNotExist()
    }

    @Test
    fun resumedRecordShowsOnlyTheBadge() = runComposeUiTest {
        // Reopening a workout (or the app) whose set already holds a record.
        val haptics = RecordingHaptics()
        val fake = Fake(
            stateOf(workout(listOf(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weightRecord), SetUi("s2", reps = 6, weightKg = 80.0)))),
        )
        show(fake, haptics)
        mainClock.advanceTimeBy(1_000)
        badge("s1").assertExists()
        toast().assertDoesNotExist()
        assertEquals(emptyList(), haptics.events.toList())
        // Completing another set here does not announce the old record either.
        check("s2")
        assertEquals(listOf(HapticEvent.SetCompleted), haptics.events.toList())
        toast().assertDoesNotExist()
    }

    @Test
    fun toastLeavesAfterFourSecondsAndIsPolite() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val fake = Fake(stateOf(workout(listOf(SetUi("s1", reps = 6, weightKg = 87.5), SetUi("s2", reps = 6, weightKg = 87.5)))))
        show(fake, haptics)
        check("s1")
        mainClock.autoAdvance = false
        fake.confirm("s1", weightRecord)
        Snapshot.sendApplyNotifications()
        // The state, the frame the reaction waits for and the toast's first frame.
        repeat(4) { mainClock.advanceTimeByFrame() }
        toast().assertExists()
        toast().assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        val hold = MoosclesTiming().prToast
        mainClock.advanceTimeBy(hold - 300)
        toast().assertExists()
        mainClock.advanceTimeBy(600)
        mainClock.advanceTimeByFrame()
        toast().assertDoesNotExist()
        mainClock.autoAdvance = true
        waitForIdle()
        // Gone for good: the badge stays, nothing plays again.
        toast().assertDoesNotExist()
        badge("s1").assertExists()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.PersonalRecord), haptics.events.toList())
    }

    @Test
    fun toastWaitsWhileARowIsEditing() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val fake = Fake(
            stateOf(workout(listOf(SetUi("s1", reps = 6, weightKg = 87.5), SetUi("s2", reps = 6, weightKg = 80.0), SetUi("s3", reps = 6, weightKg = 80.0)))),
        )
        show(fake, haptics)
        check("s1")
        // The next set goes into edit (its load, first tap) before the record arrives.
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SetRowLoadTag)) and hasAnyAncestor(hasTestTag(workoutSetRowTag("s2"))), useUnmergedTree = true)
            .performClick()
        waitForIdle()
        fake.confirm("s1", weightRecord)
        waitForIdle()
        // Confirmed: the badge and the haptic now; the toast waits, without running out.
        badge("s1").assertExists()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.PersonalRecord), haptics.events.toList())
        toast().assertDoesNotExist()
        mainClock.advanceTimeBy(MoosclesTiming().prToast + 1_000)
        toast().assertDoesNotExist()
        // The check ends the edit: the toast shows, for its whole time.
        check("s2")
        toastText("Novo recorde de carga").assertExists()
        toast().assertIsDisplayed()
        mainClock.advanceTimeBy(MoosclesTiming().prToast + 500)
        toast().assertDoesNotExist()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.PersonalRecord, HapticEvent.SetCompleted), haptics.events.toList())
    }

    @Test
    fun scrollingARecordRowAwayAndBackDoesNotReplay() = runComposeUiTest {
        // Review Focus 2: a long exercise, so the record row leaves the composition.
        val haptics = RecordingHaptics()
        val sets = (1..30).map { SetUi("s$it", reps = 6, weightKg = 87.5) }
        val fake = Fake(stateOf(workout(sets)))
        show(fake, haptics, size = DpSize(412.dp, 640.dp))
        check("s1")
        fake.confirm("s1", weightRecord)
        waitForIdle()
        badge("s1").assertExists()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.PersonalRecord), haptics.events.toList())
        mainClock.advanceTimeBy(MoosclesTiming().prToast + 500)
        toast().assertDoesNotExist()

        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(ActiveWorkoutTags.END))
        waitForIdle()
        // The control: the row really left the composition.
        row("s1").assertDoesNotExist()
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(workoutSetRowTag("s1")))
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        // The badge is back; no second haptic, no second notice.
        badge("s1").assertExists()
        toast().assertDoesNotExist()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.PersonalRecord), haptics.events.toList())
    }

    @Test
    fun recreationDoesNotReplayTheRecord() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val fake = Fake(stateOf(workout(listOf(SetUi("s1", reps = 6, weightKg = 87.5), SetUi("s2", reps = 6, weightKg = 87.5)))))
        val restoration = screenRestoration()
        // No language override: leaving ProvideAppLocale rebuilds the theme's tree, which would
        // drop the saved state the tester restores.
        show(fake, haptics, locale = null, restoration = restoration)
        check("s1")
        fake.confirm("s1", weightRecord)
        waitForIdle()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.PersonalRecord), haptics.events.toList())
        mainClock.advanceTimeBy(MoosclesTiming().prToast + 500)
        toast().assertDoesNotExist()

        restoration.emulateSaveAndRestore()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        badge("s1").assertExists()
        toast().assertDoesNotExist()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.PersonalRecord), haptics.events.toList())

        // The control: the set is still known as completed here, so beating the record again
        // (the load edited up) is announced, with the new value.
        fake.change("s1") { it.copy(weightKg = 90.0) }
        waitForIdle()
        // The system's language: only the new load is checked.
        onNode(hasText("90${NBSP}kg × 6", substring = true) and hasAnyAncestor(hasTestTag(PrToastTag)), useUnmergedTree = true).assertExists()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.PersonalRecord, HapticEvent.PersonalRecord), haptics.events.toList())
    }

    @Test
    fun badgeFollowsTheChosenSet() = runComposeUiTest {
        // Two sets of the same value: the badge is only on the one the domain chose, and moves with it.
        val haptics = RecordingHaptics()
        val fake = Fake(
            stateOf(
                workout(
                    listOf(
                        SetUi("s1", reps = 5, weightKg = 90.0, completed = true),
                        SetUi("s2", reps = 5, weightKg = 90.0, completed = true, record = weightRecord),
                        SetUi("s3", reps = 5, weightKg = 90.0),
                    ),
                ),
            ),
        )
        show(fake, haptics)
        badge("s2").assertExists()
        badge("s1").assertDoesNotExist()
        badge("s3").assertDoesNotExist()
        fake.confirm("s1", weightRecord)
        waitForIdle()
        badge("s1").assertExists()
        badge("s2").assertDoesNotExist()
        // Losing it is silent; none of these was completed here, so nothing was announced.
        fake.confirm(null, null)
        waitForIdle()
        badge("s1").assertDoesNotExist()
        badge("s2").assertDoesNotExist()
        toast().assertDoesNotExist()
        assertEquals(emptyList(), haptics.events.toList())
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val NBSP = ' '
    }
}
