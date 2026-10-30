package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mooscles.data.ExerciseCatalog
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Today's taps on the old library (plan 4A, Task 19, Step 1), measured before the swap, over the
 * real navigation graph with the whole catalog. Each `performClick` counts 1; typed text does not
 * count, nor does scrolling to a control. The custom exercise flow is measured on the device
 * (`LibraryTapBaselineDeviceTest`): the old dialog's text field never idles under Robolectric.
 * Temporary: deleted with the old screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class LibraryTapBaselineHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    private lateinit var state: MutableState<UiState>
    private var taps = 0

    private fun tap(node: SemanticsNodeInteraction) {
        node.performClick()
        taps++
        compose.waitForIdle()
    }

    /** From the Train tab: the Library tab, the Muscle menu, "Back", then the first back exercise. */
    @Test fun findByMuscleAndOpen() {
        show()
        tap(compose.onNodeWithTag("tab-library"))
        tap(compose.onNodeWithText("Muscle"))
        tap(compose.onNodeWithText("Back"))
        // The cards keep the state's order: the first back exercise is the first card.
        val first = state.value.exercises.first { it.muscle == "Back" }
        tap(compose.onNodeWithText(first.name))
        compose.onNodeWithText("Movement history", substring = true, ignoreCase = true).assertIsDisplayed()
        println("TAP baseline findByMuscleAndOpen: $taps")
        assertEquals(4, taps)
    }

    private fun show() {
        val exercises = ExerciseCatalog.exercises.map { ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label) }
        state = mutableStateOf(UiState(loading = false, exercises = exercises))
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.waitForIdle()
    }

    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Library tap baseline actions"
            else -> { calls += method.name + ":" + (args ?: emptyArray()).joinToString(","); null }
        }
    } as MoosclesActions
}
