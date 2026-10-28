package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseCardTest {
    @androidx.compose.runtime.Composable
    private fun Card(
        media: ExerciseMedia,
        collapsed: Boolean = false,
        onMenu: () -> Unit = {},
        onAddSet: () -> Unit = {},
    ) = ProvideAppLocale("en") {
        ExerciseCard("Bench press", "Barbell, chest", media, MIcons.Barbell, onMenu, onAddSet, collapsed = collapsed) {
            Box(Modifier.testTag("a-set"))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun offlineMediaShowsTheIconAndText() = runComposeUiTest {
        withTheme { Card(ExerciseMedia.Offline) }
        waitForIdle()
        onNodeWithTag(ExerciseOfflineIconTag, useUnmergedTree = true).assertExists()
        onNodeWithTag(ExerciseOfflineTextTag, useUnmergedTree = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noMediaShowsOnlyTheEquipmentIcon() = runComposeUiTest {
        withTheme { Card(ExerciseMedia.None) }
        waitForIdle()
        onNodeWithTag(ExerciseEquipmentIconTag, useUnmergedTree = true).assertExists()
        onNodeWithTag(ExerciseOfflineIconTag, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(ExerciseOfflineTextTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun menuIsLabelled() = runComposeUiTest {
        var opened = 0
        withTheme { Card(ExerciseMedia.None, onMenu = { opened++ }) }
        waitForIdle()
        onNodeWithTag(ExerciseMenuTag).assertContentDescriptionEquals("Exercise options")
        onNodeWithTag(ExerciseMenuTag).performClick()
        assertEquals(1, opened)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun addSetMeetsTheWorkoutTarget() = runComposeUiTest {
        var added = 0
        withTheme { Card(ExerciseMedia.None, onAddSet = { added++ }) }
        waitForIdle()
        val touch = onNodeWithTag(ExerciseAddSetTag).fetchSemanticsNode().touchBoundsInRoot
        val target = with(density) { MoosclesTargets().workout.toPx() }
        assertTrue(touch.height >= target - 0.5f, "height ${touch.height} < $target")
        onNodeWithTag(ExerciseAddSetTag).performClick()
        assertEquals(1, added)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun collapsedHidesTheSets() = runComposeUiTest {
        withTheme { Card(ExerciseMedia.None, collapsed = true) }
        waitForIdle()
        onNodeWithTag("a-set").assertDoesNotExist()
        onNodeWithTag(ExerciseAddSetTag).assertDoesNotExist()
        onNodeWithTag(ExerciseMenuTag).assertExists()
    }
}
