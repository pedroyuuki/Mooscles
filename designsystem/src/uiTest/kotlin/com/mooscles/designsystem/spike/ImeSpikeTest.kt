package com.mooscles.designsystem.spike

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val ScreenTag = "ime-spike-screen"
private const val FieldTag = "ime-spike-field"
private const val DockTag = "ime-spike-dock"

/**
 * Spike S3: does the iOS simulator (and the emulator) open a real keyboard in a UI test, so
 * WindowInsets.ime reports it? If not, the screen tests inject the insets instead.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ImeSpikeTest {

    private class Probe {
        var imeBottom = 0
        var maxImeBottom = 0
        val focusRequester = FocusRequester()
        val bringIntoView = BringIntoViewRequester()
        var scope: CoroutineScope? = null
    }

    /** A scrollable column with a field at its end and a 56 dp box pinned above [imeInsets]. */
    @Composable
    private fun SpikeScreen(probe: Probe, imeInsets: WindowInsets) {
        val density = LocalDensity.current
        val bottom = imeInsets.getBottom(density)
        SideEffect {
            probe.imeBottom = bottom
            if (bottom > probe.maxImeBottom) probe.maxImeBottom = bottom
        }
        probe.scope = rememberCoroutineScope()
        var text by remember { mutableStateOf("") }
        val insetsBottom = imeInsets.only(WindowInsetsSides.Bottom)
        Box(Modifier.fillMaxSize().testTag(ScreenTag)) {
            Column(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(insetsBottom)
                    .padding(bottom = 56.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.fillMaxWidth().height(2000.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth()
                        .height(48.dp)
                        .focusRequester(probe.focusRequester)
                        .bringIntoViewRequester(probe.bringIntoView)
                        .testTag(FieldTag),
                )
            }
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(insetsBottom)
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(DockTag),
            )
        }
    }

    private fun ComposeUiTest.bounds(tag: String): Rect =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /**
     * The field is wholly on screen and above the dock. boundsInRoot is clipped by the scroll
     * viewport (an off-screen field is an empty rect at 0,0), so its full 48 dp height counts.
     */
    private fun ComposeUiTest.fieldVisible(): Boolean {
        val field = bounds(FieldTag)
        val dock = bounds(DockTag)
        val fullHeight = with(density) { 48.dp.toPx() }
        return field.height >= fullHeight - 0.5f && field.top >= -0.5f && field.bottom <= dock.top + 0.5f
    }

    @Test
    fun focusingAFieldRecordsTheImeInset() = runComposeUiTest {
        val probe = Probe()
        setContent { SpikeScreen(probe, WindowInsets.ime) }
        waitForIdle()
        runOnIdle { probe.focusRequester.requestFocus() }
        // Real time, not the test clock: a system keyboard animates on its own clock.
        val deadline = TimeSource.Monotonic.markNow() + 2.seconds
        while (deadline.hasNotPassedNow()) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }
        val visible = fieldVisible()
        println(
            "SPIKE ime bottom=${probe.imeBottom} fieldVisible=$visible " +
                "maxBottom=${probe.maxImeBottom} screen=${bounds(ScreenTag)} dock=${bounds(DockTag)} field=${bounds(FieldTag)}",
        )
        onNodeWithTag(FieldTag, useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun injectedInsetsPlaceTheCompactDockAboveThem() = runComposeUiTest {
        val probe = Probe()
        setContent { SpikeScreen(probe, WindowInsets(bottom = 300.dp)) }
        waitForIdle()
        val screen = bounds(ScreenTag)
        val dock = bounds(DockTag)
        val expectedBottom = screen.bottom - with(density) { 300.dp.toPx() }
        println("SPIKE injected screen=$screen dock=$dock expectedBottom=$expectedBottom imeBottom=${probe.imeBottom}")
        assertTrue(abs(dock.bottom - expectedBottom) <= 1f, "dock bottom ${dock.bottom}, expected $expectedBottom")
        // The control: before bringIntoView the field (after 2000 dp of content) is off screen.
        assertFalse(fieldVisible(), "field already visible before bringIntoView: ${bounds(FieldTag)}")
        runOnIdle { probe.scope!!.launch { probe.bringIntoView.bringIntoView() } }
        waitForIdle()
        val field = bounds(FieldTag)
        println("SPIKE injected afterBringIntoView field=$field dock=${bounds(DockTag)}")
        assertTrue(fieldVisible(), "field $field not wholly above the dock ${bounds(DockTag)}")
    }
}
