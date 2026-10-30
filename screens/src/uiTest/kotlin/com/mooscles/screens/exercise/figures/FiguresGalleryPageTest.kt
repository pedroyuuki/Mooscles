package com.mooscles.screens.exercise.figures

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.components.ExerciseMediaFigureTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.withScreen
import kotlin.test.AfterTest
import kotlin.test.Test

/** The gallery's "Figures" page (plan decision 5): every pose, both frames, by its shown name. */
@OptIn(ExperimentalTestApi::class)
class FiguresGalleryPageTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun showsEveryPoseTwice() = runComposeUiTest {
        val page = figuresGalleryPage()
        withScreen(locale = "pt-BR") { page.content() }
        waitForIdle()
        for (id in ExerciseFigures.stableIds) {
            onNodeWithTag(figureFrameTag(id, start = true)).assertExists()
            onNodeWithTag(figureFrameTag(id, start = false)).assertExists()
        }
        val frames = SemanticsMatcher("a figure frame") { node ->
            SemanticsProperties.TestTag in node.config && node.config[SemanticsProperties.TestTag].startsWith("figure-")
        }
        onAllNodes(frames).assertCountEquals(2 * ExerciseFigures.stableIds.size)
        // And each exercise's list thumbnail, the end frame alone.
        onAllNodesWithTag(ExerciseMediaFigureTag).assertCountEquals(ExerciseFigures.stableIds.size)
        // The names are the shown ones, in the gallery's language.
        onNodeWithText("Supino reto com barra").assertExists()
        onNodeWithText("Barra fixa pronada").assertExists()
    }
}
