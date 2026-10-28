package com.mooscles.designsystem.gallery

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_unit_kg_spoken
import com.mooscles.designsystem.resources.gallery_controls_hide
import com.mooscles.designsystem.resources.gallery_controls_show
import com.mooscles.designsystem.resources.gallery_sample_previous_spoken
import com.mooscles.designsystem.resources.gallery_section_buttons
import com.mooscles.designsystem.resources.gallery_section_chip_kinds
import com.mooscles.designsystem.resources.gallery_section_colors
import com.mooscles.designsystem.resources.gallery_section_exercise_card
import com.mooscles.designsystem.resources.gallery_section_icons
import com.mooscles.designsystem.resources.gallery_section_records
import com.mooscles.designsystem.resources.gallery_section_overlays
import com.mooscles.designsystem.resources.gallery_section_rows
import com.mooscles.designsystem.resources.gallery_section_set_rows
import com.mooscles.designsystem.resources.gallery_section_states
import com.mooscles.designsystem.resources.gallery_section_text_fields
import com.mooscles.designsystem.theme.rememberSystemReducedMotion
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.resources.gallery_section_timer
import com.mooscles.designsystem.resources.gallery_section_workout_inputs
import com.mooscles.designsystem.withTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

class GallerySmokeTest {
    /** A string each page shows once rendered. Every new page must add its entry here. */
    private val knownText: Map<GalleryPage, StringResource> = mapOf(
        GalleryPage.Foundations to Res.string.gallery_section_colors,
        GalleryPage.Icons to Res.string.gallery_section_icons,
        GalleryPage.Buttons to Res.string.gallery_section_buttons,
        GalleryPage.Chips to Res.string.gallery_section_chip_kinds,
        GalleryPage.Rows to Res.string.gallery_section_rows,
        GalleryPage.Fields to Res.string.gallery_section_text_fields,
        GalleryPage.States to Res.string.gallery_section_states,
        GalleryPage.Overlays to Res.string.gallery_section_overlays,
        GalleryPage.WorkoutInputs to Res.string.gallery_section_workout_inputs,
        GalleryPage.SetRow to Res.string.gallery_section_set_rows,
        GalleryPage.Timer to Res.string.gallery_section_timer,
        GalleryPage.Records to Res.string.gallery_section_records,
        GalleryPage.ExerciseCard to Res.string.gallery_section_exercise_card,
    )

    /** A failed language test must not leak Portuguese into the next test. */
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyPageRendersInBothThemes() {
        assertEquals(GalleryPage.entries.toSet(), knownText.keys, "Add a known text for the new page")
        for (dark in listOf(false, true)) {
            for (page in GalleryPage.entries) {
                runComposeUiTest {
                    withTheme(dark = dark) { page.Content() }
                    waitForIdle()
                    onNodeWithText(getString(knownText.getValue(page))).assertExists("${page.name} dark=$dark")
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyPageRendersInBothThemesAtTwoHundredPercent() {
        assertEquals(GalleryPage.entries.toSet(), knownText.keys, "Add a known text for the new page")
        for (dark in listOf(false, true)) {
            for (page in GalleryPage.entries) {
                runComposeUiTest {
                    withTheme(dark = dark, fontScale = 2f) { page.Content() }
                    waitForIdle()
                    onNodeWithText(getString(knownText.getValue(page))).assertExists("${page.name} dark=$dark at 200%")
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theListShowsEveryTitleAndOpensAPage() {
        runComposeUiTest {
            withTheme { GalleryScreen(onBack = {}) }
            waitForIdle()
            for (page in GalleryPage.entries) onNodeWithText(getString(page.title)).assertExists(page.name)
            onNodeWithTag(galleryEntryTag(GalleryPage.Foundations)).performClick()
            waitForIdle()
            onNodeWithText(getString(knownText.getValue(GalleryPage.Foundations))).assertExists()
        }
    }

    /** Every page, with the gallery controls around it: each clickable node says what it is. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyClickableNodeHasALabel() {
        for (page in GalleryPage.entries) {
            runComposeUiTest {
                withTheme { GalleryScreen(onBack = {}, initialPage = page) }
                waitForIdle()
                val nodes = onAllNodes(hasClickAction()).fetchSemanticsNodes()
                assertTrue(nodes.isNotEmpty(), "${page.name}: no clickable node")
                for (node in nodes) {
                    val labels = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                    assertTrue(labels.any { it.isNotBlank() }, "${page.name}: clickable node ${node.id} has no label: ${node.config}")
                }
            }
        }
    }

    /** Every clickable target, drawn or extended, is at least 48 dp in both dimensions, measured in view. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyClickableTargetIsAtLeastTheMinimum() {
        for (page in GalleryPage.entries) {
            runComposeUiTest {
                withTheme { GalleryScreen(onBack = {}, initialPage = page) }
                waitForIdle()
                val minPx = with(density) { MoosclesTargets().min.toPx() } - 0.5f
                val inScroll = hasAnyAncestor(hasScrollAction())
                val count = onAllNodes(hasClickAction()).fetchSemanticsNodes().size
                for (index in 0 until count) {
                    // Bounds are clipped by the page's scroll: bring each target into view first.
                    val target = onAllNodes(hasClickAction())[index]
                    if (inScroll.matches(target.fetchSemanticsNode())) target.performScrollTo()
                    waitForIdle()
                    val node = target.fetchSemanticsNode()
                    val touch = node.touchBoundsInRoot
                    val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                        ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                    assertTrue(
                        touch.width >= minPx && touch.height >= minPx,
                        "${page.name}: \"$label\" has a ${touch.width} x ${touch.height} px target, minimum $minPx",
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fontScaleControlChangesTheDensityOfThePage() = runComposeUiTest {
        withTheme { GalleryScreen(onBack = {}, initialPage = GalleryPage.Foundations) }
        waitForIdle()
        val text = getString(knownText.getValue(GalleryPage.Foundations))
        val normal = onNodeWithText(text).getUnclippedBoundsInRoot().height
        onNodeWithTag(GalleryTags.scale(GalleryFontScale.Largest)).performClick()
        waitForIdle()
        onNodeWithTag(GalleryTags.scale(GalleryFontScale.Largest)).assertIsSelected()
        val largest = onNodeWithText(text).getUnclippedBoundsInRoot().height
        assertTrue(largest.value >= normal.value * 1.8f, "200% must grow the page text: $normal -> $largest")
        // The controls themselves keep the screen's density.
        val chip = onNodeWithTag(GalleryTags.scale(GalleryFontScale.Normal)).getUnclippedBoundsInRoot().height
        onNodeWithTag(GalleryTags.scale(GalleryFontScale.Normal)).performClick()
        waitForIdle()
        assertEquals(normal, onNodeWithText(text).getUnclippedBoundsInRoot().height)
        assertEquals(chip, onNodeWithTag(GalleryTags.scale(GalleryFontScale.Normal)).getUnclippedBoundsInRoot().height)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun languageControlTranslatesTheGalleryAndLeavingRestoresTheSystemLanguage() {
        var left = 0
        runComposeUiTest {
            withTheme { GalleryScreen(onBack = { left++ }, initialPage = GalleryPage.Chips) }
            waitForIdle()
            onNodeWithTag(GalleryTags.language(GalleryLanguage.Portuguese)).performClick()
            waitForIdle()
            onNodeWithTag(GalleryTags.language(GalleryLanguage.Portuguese)).assertIsSelected()
            // The emulator and the simulator run in English, so these are the translations.
            onNodeWithText("Tipos de chip").assertExists()
            onNodeWithText("Idioma").assertExists()
            val back = hasContentDescription("Voltar") and hasClickAction()
            // Back first closes the page, still in Portuguese.
            onNode(back).performClick()
            waitForIdle()
            onNodeWithText("Galeria de design").assertExists()
            assertEquals(0, left)
            // Back on the list leaves, and the system language is already back when it does.
            onNode(back).performClick()
            waitForIdle()
            assertEquals(1, left)
            onNodeWithText("Design gallery").assertExists()
            onNodeWithTag(GalleryTags.language(GalleryLanguage.System)).assertIsSelected()
        }
        assertFalse(LocalAppLocale.restore(), "Leaving must have restored the language already")
    }

    /** Critique #14: motion starts by following the system, and the choice can force it either way. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun motionFollowsTheSystemUntilChanged() = runComposeUiTest {
        var system: Boolean? = null
        withTheme {
            system = rememberSystemReducedMotion()
            GalleryScreen(onBack = {}, initialPage = GalleryPage.Foundations)
        }
        waitForIdle()
        onNodeWithTag(GalleryTags.motion(GalleryMotion.System)).assertIsSelected()
        onNodeWithText("reducedMotion: $system").assertExists()
        onNodeWithTag(GalleryTags.motion(GalleryMotion.Reduced)).performClick()
        waitForIdle()
        onNodeWithText("reducedMotion: true").assertExists()
        onNodeWithTag(GalleryTags.motion(GalleryMotion.Full)).performClick()
        waitForIdle()
        onNodeWithText("reducedMotion: false").assertExists()
        onNodeWithTag(GalleryTags.motion(GalleryMotion.System)).performClick()
        waitForIdle()
        onNodeWithText("reducedMotion: $system").assertExists()
    }

    /** Critique #17: the controls fold into one row, which gives the page the rest of the screen. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun controlsFoldIntoOneRowAndComeBack() = runComposeUiTest {
        withTheme { GalleryScreen(onBack = {}, initialPage = GalleryPage.Buttons) }
        waitForIdle()
        val pageTop = onNodeWithTag(galleryPageTag(GalleryPage.Buttons)).getUnclippedBoundsInRoot().top
        onNodeWithTag(GalleryTags.CONTROLS_TOGGLE).assert(hasText(getString(Res.string.gallery_controls_hide))).performClick()
        waitForIdle()
        onNodeWithTag(GalleryTags.theme(dark = false)).assertDoesNotExist()
        onNodeWithTag(GalleryTags.motion(GalleryMotion.System)).assertDoesNotExist()
        onNodeWithTag(GalleryTags.CONTROLS_TOGGLE).assert(hasText(getString(Res.string.gallery_controls_show)))
        val foldedTop = onNodeWithTag(galleryPageTag(GalleryPage.Buttons)).getUnclippedBoundsInRoot().top
        assertTrue(foldedTop < pageTop, "the page must move up: $pageTop -> $foldedTop")
        onNodeWithTag(GalleryTags.CONTROLS_TOGGLE).performClick()
        waitForIdle()
        onNodeWithTag(GalleryTags.theme(dark = false)).assertExists()
    }

    /** Critique #19: each entry of the page list is a full row, at least `rowMin` tall, with a chevron. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pageListEntriesAreFullRows() = runComposeUiTest {
        withTheme { GalleryScreen(onBack = {}) }
        waitForIdle()
        val rowMin = MoosclesSizes().rowMin
        val screenWidth = onNodeWithTag(GalleryTags.SCREEN).getUnclippedBoundsInRoot().width
        for (page in GalleryPage.entries) {
            val bounds = onNodeWithTag(galleryEntryTag(page)).performScrollTo().getUnclippedBoundsInRoot()
            assertTrue(bounds.height >= rowMin - 0.5.dp, "${page.name}: ${bounds.height} tall")
            assertEquals(screenWidth, bounds.width, "${page.name} spans the screen")
        }
    }

    /** Critique #22: the previous set is read with its reps ("anterior 80 quilos por 6"). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun setRowSampleReadsThePreviousSetWithItsReps() = runComposeUiTest {
        withTheme { GalleryPage.SetRow.Content() }
        waitForIdle()
        val previous = getString(Res.string.gallery_sample_previous_spoken, getString(Res.string.ds_unit_kg_spoken, "55"), 10)
        onNode(hasContentDescription(previous, substring = true)).assertExists()
    }
}
