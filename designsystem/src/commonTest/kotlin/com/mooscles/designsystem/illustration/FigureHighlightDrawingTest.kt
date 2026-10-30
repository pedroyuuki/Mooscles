package com.mooscles.designsystem.illustration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.mooscles.designsystem.tokens.MoosclesIllustration
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Where the renderer puts a muscle highlight on the figure (Task 8: spans tuned on the pilot poses). */
class FigureHighlightDrawingTest {
    private val palette = FigurePalette(body = Color(1), far = Color(2), equipment = Color(3), highlight = Color(4))
    private val illustration = MoosclesIllustration()

    // At 100 × 100 a pixel is a box unit.
    private val stroke = illustration.figureStroke * 100f

    private val standing = FigurePose(
        hipX = 50f, hipY = 50f, trunk = 0f, head = 0f,
        upperArmNear = 120f, forearmNear = 120f, upperArmFar = 240f, forearmFar = 240f,
        thighNear = 160f, shinNear = 180f, footNear = 90f, thighFar = 200f, shinFar = 180f, footFar = 90f,
        props = listOf(FigureProp.Dumbbell(FigureJoint.HAND_NEAR)),
    )

    private fun operations(pose: FigurePose, highlights: Set<FigureSegment>): List<FigureOperation> {
        val joints = FigureKinematics.solve(pose).joints
        return figureOperations(
            joints, FigureKinematics.equipment(pose, joints), highlights, palette, illustration, Size(100f, 100f), pose.frontView,
        )
    }

    private fun List<FigureOperation>.lines(color: Color) = filterIsInstance<FigureOperation.Line>().filter { it.color == color }

    private fun near(a: Offset, b: Offset) = abs(a.x - b.x) < 0.01f && abs(a.y - b.y) < 0.01f

    /** The glutes wrap the hip: the whole width of the thigh's first 40%; the hamstrings the back of the rest. */
    @Test
    fun glutesAndHamstringsShareTheThigh() {
        val joints = FigureKinematics.solve(standing).joints
        val hip = joints.getValue(FigureJoint.HIP)
        val knee = joints.getValue(FigureJoint.KNEE_NEAR)
        val split = hip + (knee - hip) * 0.4f

        val glutes = operations(standing, setOf(FigureSegment.HIP)).lines(palette.highlight).single()
        assertTrue(near(hip, glutes.start) && near(split, glutes.end), "glutes from the hip to 40%: ${glutes.start}–${glutes.end}")
        assertEquals(stroke, glutes.width, "the glutes take the whole width")

        val hamstrings = operations(standing, setOf(FigureSegment.THIGH_BACK)).lines(palette.highlight).single()
        assertEquals(stroke / 2f, hamstrings.width, "the hamstrings take the back half")
        // Shifted a quarter stroke toward the back, from 40% to the knee.
        assertEquals(stroke / 4f, (hamstrings.start - split).getDistance(), 0.01f)
        assertEquals(stroke / 4f, (hamstrings.end - knee).getDistance(), 0.01f)
    }

    /** Side view: the far limbs are their own tone, unlit, and drawn over the equipment. Front view: both sides are body and lit. */
    @Test
    fun farLimbsAreBodyAndLitOnlyFromTheFront() {
        val side = operations(standing, setOf(FigureSegment.SHOULDER))
        assertEquals(1, side.lines(palette.highlight).size, "one deltoid from the side")
        assertEquals(5, side.lines(palette.far).size, "the far leg (3) and arm (2) in the far tone")
        val equipmentAt = side.indexOfFirst { it !is FigureOperation.Line || (it as FigureOperation.Line).color == palette.equipment }
        val farAt = side.indexOfFirst { it is FigureOperation.Line && it.color == palette.far }
        assertTrue(equipmentAt < farAt, "the equipment is drawn before the far limbs")

        val front = operations(standing.copy(frontView = true), setOf(FigureSegment.SHOULDER))
        val delts = front.lines(palette.highlight)
        assertEquals(2, delts.size, "both deltoids from the front")
        val joints = FigureKinematics.solve(standing).joints
        val shoulder = joints.getValue(FigureJoint.SHOULDER)
        for ((delt, elbow) in delts.zip(listOf(FigureJoint.ELBOW_FAR, FigureJoint.ELBOW_NEAR))) {
            assertTrue(near(shoulder, delt.start), "a deltoid starts at the shoulder")
            assertTrue(near(shoulder + (joints.getValue(elbow) - shoulder) * 0.4f, delt.end), "and covers 40% of the $elbow arm")
        }
        assertEquals(0, front.lines(palette.far).size, "no far tone from the front")
    }
}
