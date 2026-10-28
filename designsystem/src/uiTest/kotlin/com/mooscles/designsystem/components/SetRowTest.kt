package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetRowTest {
    private fun state(
        status: SetStatus = SetStatus.Pending,
        type: SetType = SetType.Normal,
        previous: String? = "80 kg × 6",
        previousSpoken: String? = "80 kilograms by 6",
        load: String = "85",
        loadSpoken: String = "85 kilograms",
    ) = SetRowState(
        index = 4, total = 5, previous = previous, previousSpoken = previousSpoken,
        load = load, loadSpoken = loadSpoken, reps = 6, type = type, status = status,
    )

    @androidx.compose.runtime.Composable
    private fun Row(
        state: SetRowState,
        onComplete: (Boolean) -> Unit = {},
        onLoad: () -> Unit = {},
        onReps: () -> Unit = {},
        onType: () -> Unit = {},
        onRemove: () -> Unit = {},
    ) = SetRow(state, onComplete, onLoad, onReps, onType, onRemove, modifier = Modifier.testTag("row"))

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun readsAsOneNodeWithTheSpecLabel() {
        runComposeUiTest {
            withTheme { ProvideAppLocale("en") { Row(state()) } }
            waitForIdle()
            onNodeWithTag("row").assertContentDescriptionEquals(
                "Set 4 of 5, previous 80 kilograms by 6, load 85 kilograms, 6 reps, not completed",
            )
        }
        runComposeUiTest {
            withTheme {
                ProvideAppLocale("pt-BR") {
                    Row(state(previousSpoken = "80 quilos por 6", loadSpoken = "85 quilos"))
                }
            }
            waitForIdle()
            onNodeWithTag("row").assertContentDescriptionEquals(
                "Série 4 de 5, anterior 80 quilos por 6, carga 85 quilos, 6 repetições, não concluída",
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun readsWithoutPrevious() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { Row(state(previous = null, previousSpoken = null, status = SetStatus.Completed)) } }
        waitForIdle()
        onNodeWithTag("row").assertContentDescriptionEquals("Set 4 of 5, load 85 kilograms, 6 reps, completed")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun exposesFiveCustomActionsThatCallBack() = runComposeUiTest {
        val calls = mutableListOf<String>()
        withTheme {
            ProvideAppLocale("en") {
                Row(
                    state(),
                    onComplete = { calls += "complete:$it" },
                    onLoad = { calls += "load" },
                    onReps = { calls += "reps" },
                    onType = { calls += "type" },
                    onRemove = { calls += "remove" },
                )
            }
        }
        waitForIdle()
        val actions = onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(
            listOf("Complete set", "Edit load", "Edit reps", "Change type", "Remove set"),
            actions.map { it.label },
        )
        runOnUiThread { actions.forEach { it.action() } }
        assertEquals(listOf("complete:true", "load", "reps", "type", "remove"), calls)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun splitsIntoTwoLinesAtOneAndAHalf() {
        for ((scale, split) in listOf(1f to false, 1.5f to true)) {
            runComposeUiTest {
                withTheme(fontScale = scale) { Row(state()) }
                waitForIdle()
                val previous = onNodeWithTag(SetRowPreviousTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val load = onNodeWithTag(SetRowLoadTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                if (split) {
                    assertTrue(previous.bottom <= load.top + 0.5f, "previous ${previous.bottom} not above load ${load.top}")
                } else {
                    assertEquals(previous.top, load.top, 0.5f)
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recordShowsTheBadgeAndFiresTheRecordHaptic() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var status by mutableStateOf(SetStatus.Editing)
        withTheme(haptics = haptics) { ProvideAppLocale("en") { Row(state(status = status)) } }
        waitForIdle()
        onNodeWithTag(PrBadgeTag, useUnmergedTree = true).assertDoesNotExist()
        status = SetStatus.CompletedRecord
        waitForIdle()
        onNodeWithTag(PrBadgeTag, useUnmergedTree = true).assertExists()
        assertEquals(listOf(HapticEvent.PersonalRecord), haptics.events.toList())
        onNodeWithTag("row").assertContentDescriptionEquals(
            "Set 4 of 5, previous 80 kilograms by 6, load 85 kilograms, 6 reps, completed, personal record",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recordReplacesTheCompleteHaptic() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var status by mutableStateOf(SetStatus.Pending)
        withTheme(haptics = haptics) { Row(state(status = status)) }
        waitForIdle()
        assertEquals(emptyList(), haptics.events.toList())
        status = SetStatus.CompletedRecord
        waitForIdle()
        assertEquals(listOf(HapticEvent.PersonalRecord), haptics.events.toList())
        status = SetStatus.Pending
        waitForIdle()
        assertEquals(listOf(HapticEvent.PersonalRecord, HapticEvent.SetUnchecked), haptics.events.toList())
        status = SetStatus.Completed
        waitForIdle()
        assertEquals(listOf(HapticEvent.PersonalRecord, HapticEvent.SetUnchecked, HapticEvent.SetCompleted), haptics.events.toList())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun typeIsALetterNotOnlyColor() {
        for ((tag, type, letter) in listOf(
            Triple("en", SetType.Warmup, "W"), Triple("pt-BR", SetType.Warmup, "A"),
            Triple("en", SetType.Drop, "D"), Triple("en", SetType.Failure, "F"),
        )) {
            runComposeUiTest {
                withTheme { ProvideAppLocale(tag) { Row(state(type = type)) } }
                waitForIdle()
                onNodeWithTag(SetRowTypeTag, useUnmergedTree = true).assertTextEquals(letter)
            }
        }
        runComposeUiTest {
            withTheme { Row(state()) }
            waitForIdle()
            onNodeWithTag(SetRowTypeTag, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun numbersNeverEllipsizeAtTwoHundredPercentOn320dp() = runComposeUiTest {
        withTheme(fontScale = 2f) {
            Box(Modifier.width(320.dp)) {
                ProvideAppLocale("en") {
                    Row(state(status = SetStatus.CompletedRecord, load = "187.5", previous = "187.5 kg × 12"))
                }
            }
        }
        waitForIdle()
        val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(texts.isNotEmpty())
        for (node in texts) {
            val results = mutableListOf<TextLayoutResult>()
            runOnUiThread { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
            for (layout in results) {
                assertFalse(layout.hasVisualOverflow, "\"${layout.layoutInput.text.text}\" overflows")
                assertFalse(layout.didOverflowWidth, "\"${layout.layoutInput.text.text}\" overflows its width")
            }
        }
        val check = onNodeWithContentDescription("Complete set 4", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val widthPx = with(density) { 320.dp.toPx() }
        assertTrue(check.right <= widthPx + 0.5f, "check ends at ${check.right} beyond $widthPx")
    }
}
