package com.mooscles

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Today's tap counts on the old workout screen, for the spec's "Orçamento de toques" table,
 * recorded before the screen is replaced (Task 24 Step 1) and copied to `tap-baseline.md`.
 *
 * The counter: a tap ([TapCounter.tap]) or a gesture counts 1, and so does each typed or erased
 * character ([TapCounter.type], [TapCounter.erase]). Changing a value erases the whole old value and
 * types the new one. Scrolling to a control is not counted (the spec's "mais a rolagem"), nor
 * opening the workout. The screen is a 411 × 891 dp phone. Each flow also checks that it did what it says (the call it made).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class TapBudgetBaselineHostTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()
    private val bench = ExerciseUi("bench", "Bench press", "Chest", "Barbell")
    private lateinit var state: androidx.compose.runtime.MutableState<UiState>

    /** Counts the user's touches on the screen; see the class comment. */
    private inner class TapCounter {
        var count = 0
            private set
        fun tap(node: SemanticsNodeInteraction) { node.performClick(); count++ }
        fun type(node: SemanticsNodeInteraction, text: String) {
            text.forEach { char -> node.performTextReplacement(editable(node) + char); count++ }
        }
        fun erase(node: SemanticsNodeInteraction, chars: Int) {
            repeat(chars) { node.performTextReplacement(editable(node).dropLast(1)); count++ }
        }
    }

    @Test fun prefilledSet() {
        val counter = open(set(reps = 8, kg = 80.0))
        counter.tap(check())
        assertCompleted(reps = 8, kg = 80.0)
        assertEquals(1, counter.count)
    }

    @Test fun sameAsLastWeekWithoutTarget() {
        val counter = open(set(reps = 0, kg = 0.0), previous = "80 kg × 8")
        counter.tap(load())
        counter.type(load(), "80")
        counter.tap(reps())
        counter.type(reps(), "8")
        counter.tap(check())
        assertCompleted(reps = 8, kg = 80.0)
        assertEquals(6, counter.count)
    }

    @Test fun oneLoadStepUpAndComplete() {
        val counter = open(set(reps = 8, kg = 80.0))
        counter.tap(load())
        counter.erase(load(), 2)
        counter.type(load(), "82.5")
        counter.tap(check())
        assertCompleted(reps = 8, kg = 82.5)
        assertEquals(8, counter.count)
    }

    @Test fun oneRepLessAndComplete() {
        val counter = open(set(reps = 8, kg = 80.0))
        counter.tap(reps())
        counter.erase(reps(), 1)
        counter.type(reps(), "7")
        counter.tap(check())
        assertCompleted(reps = 7, kg = 80.0)
        assertEquals(4, counter.count)
    }

    @Test fun addSetLikeAboveAndComplete() {
        val counter = open(set(reps = 8, kg = 80.0, completed = true))
        counter.tap(compose.onNodeWithText("Add set"))
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasText("Add set"))
        compose.onNodeWithTag("workout-set-set-1").assertExists()
        counter.tap(reps(1))
        counter.type(reps(1), "8")
        counter.tap(check(1))
        assertCompleted(reps = 8, kg = 80.0, setId = "set-1")
        assertEquals(4, counter.count)
    }

    @Test fun markWarmup() {
        val counter = open(set(reps = 8, kg = 40.0))
        counter.tap(compose.onNodeWithText("Working set"))
        counter.tap(compose.onNodeWithText("Warm-up · excluded from PRs"))
        compose.runOnIdle { assertEquals("updateSet:set-0,8,40.0,false,true,false", calls.last()) }
        assertEquals(2, counter.count)
    }

    @Test fun removeSet() {
        val counter = open(set(reps = 8, kg = 80.0))
        counter.tap(compose.onNodeWithContentDescription("Remove set 1"))
        compose.runOnIdle { assertEquals("removeSet:set-0", calls.last()) }
        assertEquals(1, counter.count)
    }

    @Test fun skipRestAndPlusFifteen() {
        val skip = open(set(reps = 8, kg = 80.0, completed = true), timer = TimerUi("Bench press", 75, 90, running = true))
        skip.tap(compose.onNodeWithContentDescription("Skip rest timer"))
        compose.runOnIdle { assertEquals("skipTimer:", calls.last()) }
        assertEquals(1, skip.count)
        val plus = TapCounter()
        plus.tap(compose.onNodeWithText("+0:15"))
        compose.runOnIdle { assertEquals("adjustTimer:15", calls.last()) }
        assertEquals(1, plus.count)
    }

    @Test fun finish() {
        val counter = open(set(reps = 8, kg = 80.0, completed = true))
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("finish-workout"))
        counter.tap(compose.onNodeWithTag("finish-workout"))
        counter.tap(compose.onNodeWithText("Finish & save"))
        compose.runOnIdle { assertEquals("finishWorkout:", calls.last()) }
        assertEquals(2, counter.count)
    }

    private fun set(reps: Int, kg: Double, completed: Boolean = false) = SetUi("set-0", reps, kg, completed)

    /** Opens the old workout tab with one exercise holding [first]; returns a counter at zero. */
    private fun open(first: SetUi, previous: String? = null, timer: TimerUi = TimerUi()): TapCounter {
        val entry = WorkoutExerciseUi("entry", bench, listOf(first), restSeconds = 90, previous = previous)
        state = mutableStateOf(UiState(loading = false, exercises = listOf(bench), timer = timer,
            active = WorkoutUi("workout", "Push day", System.currentTimeMillis(), exercises = listOf(entry))))
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.onNodeWithTag("tab-workout").performClick()
        // Scrolling is not counted: bring the exercise's last row and its footer into view.
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasText("Add set"))
        compose.runOnIdle { calls.clear() }
        return TapCounter()
    }

    private fun row(index: Int) = hasAnyAncestor(hasTestTag("workout-set-set-$index"))
    private fun load(index: Int = 0) = compose.onNode(hasTestTag("set-load-$index") and row(index))
    private fun reps(index: Int = 0) = compose.onNode(hasTestTag("set-reps-$index") and row(index))
    private fun check(index: Int = 0) = compose.onNode(hasTestTag("set-complete-$index") and row(index))
    private fun editable(node: SemanticsNodeInteraction) =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun assertCompleted(reps: Int, kg: Double, setId: String = "set-0") = compose.runOnIdle {
        assertEquals("updateSet:$setId,$reps,$kg,true,false,false", calls.last { it.startsWith("updateSet:") })
    }

    /** Records each call and keeps the sets as the repository would: the screen sees its echoes. */
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Tap budget baseline actions"
            else -> {
                calls += method.name + ":" + (args ?: emptyArray()).joinToString(",")
                when (method.name) {
                    "updateSet" -> edit { sets -> sets.map { set ->
                        if (set.id != args!![0]) set
                        else set.copy(reps = args[1] as Int, weightKg = args[2] as Double, completed = args[3] as Boolean,
                            warmup = args[4] as Boolean, assistance = args[5] as Boolean)
                    } }
                    // The repository's addSet: the last set's load, no reps.
                    "addSet" -> edit { sets -> sets + SetUi("set-${sets.size}", weightKg = sets.lastOrNull()?.weightKg ?: 0.0) }
                }
                null
            }
        }
    } as MoosclesActions

    private fun edit(change: (List<SetUi>) -> List<SetUi>) {
        val workout = state.value.active!!
        state.value = state.value.copy(active = workout.copy(exercises = workout.exercises.map { it.copy(sets = change(it.sets)) }))
    }
}
