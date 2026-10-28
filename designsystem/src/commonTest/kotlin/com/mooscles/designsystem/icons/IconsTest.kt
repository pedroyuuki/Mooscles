package com.mooscles.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IconsTest {
    private fun paths(node: VectorNode): List<VectorPath> = when (node) {
        is VectorPath -> listOf(node)
        is VectorGroup -> node.flatMap { paths(it) }
    }

    private fun pathsOf(icon: ImageVector): List<VectorPath> = paths(icon.root)

    @Test
    fun everyIconIsOnTheTwentyFourGrid() {
        for ((name, icon) in MIcons.all) {
            assertEquals(24.dp, icon.defaultWidth, name)
            assertEquals(24.dp, icon.defaultHeight, name)
            assertEquals(24f, icon.viewportWidth, name)
            assertEquals(24f, icon.viewportHeight, name)
        }
    }

    @Test
    fun everyPathStrokesTwoWithRoundCapsAndJoinsAndNeverFills() {
        for ((name, icon) in MIcons.all) {
            val all = pathsOf(icon)
            assertTrue(all.isNotEmpty(), "$name has no path")
            for (path in all) {
                assertEquals(2f, path.strokeLineWidth, name)
                assertEquals(StrokeCap.Round, path.strokeLineCap, name)
                assertEquals(StrokeJoin.Round, path.strokeLineJoin, name)
                assertNull(path.fill, "$name fills a path")
            }
        }
    }

    @Test
    fun everyIconIsTintable() {
        for ((name, icon) in MIcons.all) {
            for (path in pathsOf(icon)) {
                val stroke = assertNotNull(path.stroke, name)
                assertEquals(SolidColor(Color.Black), stroke, name)
            }
        }
    }

    @Test
    fun iconListIsExactly() {
        val expected = listOf(
            "ArrowLeft", "Check", "X", "Plus", "Minus", "ChevronRight", "ChevronDown", "ChevronUp",
            "EllipsisVertical", "CircleAlert", "CircleCheck", "Info", "Lock", "Sun", "Moon",
            "MonitorSmartphone", "Download", "Upload", "RefreshCw", "Timer", "SkipForward", "Pause",
            "Play", "Trash2", "Undo2", "FileText", "Wifi", "ImageOff",
            "Plate", "PlateRecord", "Dumbbell", "Barbell", "Kettlebell", "Bench", "Cable", "Machine",
            "Rope", "Body",
        )
        assertEquals(expected, MIcons.all.map { it.first })
        for ((name, icon) in MIcons.all) assertEquals(name, icon.name)
        assertEquals(38, MIcons.all.map { it.second }.toSet().size)
    }
}
