package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mooscles.data.ExerciseCatalog
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Today's taps on the old library (plan 4A, Task 19, Step 1), on the device: the old custom
 * exercise dialog (an `OutlinedTextField` in an `AlertDialog`) never idles under Robolectric.
 * Each `performClick` counts 1; typed text does not count. Temporary: deleted with the old screen.
 */
@RunWith(AndroidJUnit4::class)
class LibraryTapBaselineDeviceTest {
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
        tap(compose.onNodeWithText(state.value.exercises.first { it.muscle == "Back" }.name))
        compose.onNodeWithText("Movement history", substring = true, ignoreCase = true).assertIsDisplayed()
        println("TAP baseline device findByMuscleAndOpen: $taps")
        assertEquals(4, taps)
    }

    /**
     * From the library: "Create custom exercise", the name (typed, not counted; the text input
     * focuses the field itself), the Muscle menu and "Chest", the Equipment menu and "Dumbbell"
     * (the menus start on the first sorted value, "Back" and "Barbell"), the convention left as it
     * starts, "Save exercise".
     */
    @Test fun createCustomExerciseWithNameMuscleAndEquipment() {
        show()
        compose.onNodeWithTag("tab-library").performClick()
        compose.waitForIdle()
        tap(compose.onNodeWithContentDescription("Create custom exercise"))
        compose.onNode(hasSetTextAction() and hasText("Exercise name")).performTextInput("Machine row")
        compose.waitForIdle()
        tap(compose.onNode(hasText("Back") and hasClickAction() and hasAnyAncestor(isDialog())))
        tap(compose.onAllNodesWithText("Chest").filter(hasAnyAncestor(isPopup())).onFirst())
        tap(compose.onNode(hasText("Barbell") and hasClickAction() and hasAnyAncestor(isDialog())))
        tap(compose.onAllNodesWithText("Dumbbell").filter(hasAnyAncestor(isPopup())).onFirst())
        tap(compose.onNodeWithText("Save exercise"))
        val convention = state.value.exercises.map { it.convention }.distinct().sorted().first()
        compose.runOnIdle {
            assertEquals(listOf("saveCustomExercise:null,Machine row,Chest,Dumbbell,$convention"),
                calls.filter { it.startsWith("saveCustomExercise") })
        }
        println("TAP baseline device createCustomExerciseWithNameMuscleAndEquipment: $taps")
        assertEquals(6, taps)
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
