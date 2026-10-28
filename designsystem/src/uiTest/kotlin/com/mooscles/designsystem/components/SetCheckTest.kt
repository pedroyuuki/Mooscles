package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetCheckTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun isAToggleWithTheSetNumber() = runComposeUiTest {
        var checked by mutableStateOf(false)
        withTheme { ProvideAppLocale("en") { SetCheck(checked, { checked = it }, setNumber = 4, modifier = Modifier.testTag("c")) } }
        waitForIdle()
        onNodeWithTag("c").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        onNodeWithTag("c").assertContentDescriptionEquals("Complete set 4")
        onNodeWithTag("c").assertIsOff()
        onNodeWithTag("c").performClick()
        waitForIdle()
        onNodeWithTag("c").assertIsOn()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun targetIsFiftySix() = runComposeUiTest {
        withTheme { SetCheck(false, {}, setNumber = 1, modifier = Modifier.testTag("c")) }
        waitForIdle()
        val touch = onNodeWithTag("c").fetchSemanticsNode().touchBoundsInRoot
        val target = with(density) { MoosclesTargets().workout.toPx() }
        assertTrue(touch.height >= target - 0.5f && touch.width >= target - 0.5f, "touch ${touch.width} x ${touch.height} < $target")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun checkAndUncheckFireTheirHaptics() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var checked by mutableStateOf(false)
        withTheme(haptics = haptics) { SetCheck(checked, { checked = it }, setNumber = 1, modifier = Modifier.testTag("c")) }
        waitForIdle()
        onNodeWithTag("c").performClick()
        waitForIdle()
        onNodeWithTag("c").performClick()
        waitForIdle()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.SetUnchecked), haptics.events.toList())
    }
}
