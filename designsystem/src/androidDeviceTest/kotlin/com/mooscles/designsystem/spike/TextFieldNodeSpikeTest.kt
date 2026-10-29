package com.mooscles.designsystem.spike

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.platform.app.InstrumentationRegistry
import com.mooscles.designsystem.components.MTextField
import com.mooscles.designsystem.components.SpikeLabelStrategy
import com.mooscles.designsystem.theme.MoosclesDesignTheme
import org.junit.Test

class TextFieldNodeSpikeTest {
    private fun walk(n: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (n == null) return
        out += n
        for (i in 0 until n.childCount) walk(n.getChild(i), out)
    }

    @Test
    fun recordsTheNodeOfEachStrategy() {
        // A fresh activity per strategy, so the accessibility cache never carries a node over.
        for (s in SpikeLabelStrategy.entries) {
            androidx.test.core.app.ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { act ->
                    act.setContentView(ComposeView(act).apply { setContent {
                        MoosclesDesignTheme(dark = false) {
                            MTextField(TextFieldValue("2:30"), {}, "Default rest duration", help = "Minutes, then seconds", labelStrategy = s, singleLine = true)
                        }
                    } })
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                Thread.sleep(1500)
                val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
                val nodes = mutableListOf<AccessibilityNodeInfo>()
                walk(ui.rootInActiveWindow, nodes)
                fun dump(n: AccessibilityNodeInfo?, d: Int) {
                    if (n == null) return
                    val line = "SPIKE $s tree ${"  ".repeat(d)}${n.className} text=${n.text} cd=${n.contentDescription} focusable=${n.isFocusable} clickable=${n.isClickable} editable=${n.isEditable}"
                    println(line); android.util.Log.i("SPIKE", line)
                    for (i in 0 until n.childCount) dump(n.getChild(i), d + 1)
                }
                dump(ui.rootInActiveWindow, 0)
                for (n in nodes.filter { it.isEditable }) {
                    val line = "SPIKE $s text=${n.text} hint=${n.hintText} contentDescription=${n.contentDescription} " +
                        "stateDescription=${n.stateDescription} editable=${n.isEditable} class=${n.className}"
                    println(line); android.util.Log.i("SPIKE", line)
                }
                for (n in nodes.filter { !it.isEditable && (it.text != null || it.contentDescription != null) }) {
                    val line = "SPIKE $s other text=${n.text} contentDescription=${n.contentDescription} class=${n.className}"
                    println(line); android.util.Log.i("SPIKE", line)
                }
            }
        }
    }
}
