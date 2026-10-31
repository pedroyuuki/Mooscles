package com.mooscles.screens.exercise.figures

import com.mooscles.data.Equipment
import com.mooscles.data.ExerciseCatalog
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.illustration.FigureJoint
import com.mooscles.designsystem.illustration.FigureKinematics
import com.mooscles.designsystem.illustration.FigurePose
import com.mooscles.designsystem.illustration.FigureProp
import com.mooscles.designsystem.illustration.FigureSegment
import com.mooscles.designsystem.tokens.MoosclesIllustration
import com.mooscles.screens.exercise.exerciseMedia
import com.mooscles.ui.ExerciseUi
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The hand-drawn poses (spec D3, D4; plan decisions 6 and 7). */
class ExerciseFiguresTest {
    /** Spec D4, written out again so the table cannot drift from it. */
    private val d4 = listOf(
        "barbell_bench_flat", "barbell_back_squat_high_bar", "barbell_deadlift_conventional", "barbell_overhead_press",
        "barbell_row_overhand", "barbell_deadlift_romanian", "barbell_hip_thrust", "dumbbell_bench_flat",
        "dumbbell_press_seated", "dumbbell_raise_lateral", "dumbbell_curl_standing", "dumbbell_squat_bulgarian",
        "cable_lat_pulldown_wide", "cable_row_close_neutral", "cable_triceps_pushdown_rope", "bodyweight_pull_up",
        "bodyweight_push_up", "plate_leg_press_45", "selectorized_leg_extension", "selectorized_leg_curl_lying",
    )

    private val tolerance = 0.5f

    private fun eachFrame(check: (id: String, frame: String, pose: FigurePose, onFloor: Set<FigureJoint>) -> Unit) {
        for (id in ExerciseFigures.stableIds) {
            val frames = assertNotNull(ExerciseFigures.forExercise("catalog_$id"), id)
            check(id, "start", frames.start, frames.startOnFloor)
            check(id, "end", frames.end, frames.endOnFloor)
        }
    }

    @Test
    fun pilotIsComplete() {
        assertEquals(d4, ExerciseFigures.PILOT)
        // Both frames come with every figure (FigureFrames has no optional frame).
        for (id in d4) assertNotNull(ExerciseFigures.forExercise("catalog_$id"), "$id has a figure")
        assertTrue(ExerciseFigures.stableIds.containsAll(d4))
    }

    /**
     * Plan Task L: every exercise of a lot has a figure, in its family's table. [members] are the
     * lot's `stableId`s, read from the catalog by equipment.
     */
    private fun familyIsComplete(family: String, members: List<String>) {
        val table = assertNotNull(ExerciseFigures.families[family], "no $family table")
        assertTrue(members.isNotEmpty(), "the $family lot is empty")
        val missing = members.filter { it !in table }
        assertTrue(missing.isEmpty(), "$family lacks ${missing.size} figures: $missing")
    }

    /** The barbell lot (spec D4): every barbell entry of the catalog, EZ bar included, and the trap-bar deadlift. */
    @Test
    fun barbellFamilyIsComplete() = familyIsComplete(
        "barbell",
        ExerciseCatalog.exercises.filter { it.equipment == Equipment.BARBELL }.map { it.id.removePrefix(ExerciseCatalog.ID_PREFIX) } +
            "trap_bar_deadlift",
    )

    /** F34: the margin token is a fraction of the box, so it is compared in box units. */
    @Test
    fun everyPoseStaysInsideTheBoxWithMargin() {
        val margin = MoosclesIllustration().figureMargin * BOX
        eachFrame { id, frame, pose, _ ->
            val bounds = FigureKinematics.solve(pose).bounds
            assertTrue(
                bounds.left >= margin && bounds.top >= margin && bounds.right <= BOX - margin && bounds.bottom <= BOX - margin,
                "$id $frame: $bounds leaves [$margin, ${BOX - margin}]",
            )
        }
    }

    /**
     * F14: each frame declares what rests on its floor (both feet flat when standing, toes and hands
     * in a push-up, the front foot only in a split squat). Those joints sit on `floorY`, and nothing
     * goes through it.
     */
    @Test
    fun groundedFeetTouchTheFloor() {
        eachFrame { id, frame, pose, onFloor ->
            val floor = pose.floorY
            if (floor == null) {
                assertTrue(onFloor.isEmpty(), "$id $frame declares $onFloor on no floor")
                return@eachFrame
            }
            assertTrue(onFloor.isNotEmpty(), "$id $frame has a floor that nothing rests on")
            val joints = FigureKinematics.solve(pose).joints
            for (joint in onFloor) {
                val y = joints.getValue(joint).y
                assertTrue(abs(y - floor) <= tolerance, "$id $frame: $joint at $y, the floor at $floor")
            }
            for ((joint, point) in joints) {
                assertTrue(point.y <= floor + tolerance, "$id $frame: $joint at ${point.y} is under the floor at $floor")
            }
            // The drawn floor line is the one the joints rest on, just under them.
            val line = pose.props.filterIsInstance<FigureProp.Floor>().single()
            assertEquals(floor + CONTACT_GAP, line.y, 0.001f, "$id $frame floor line")
        }
    }

    @Test
    fun bothFramesHaveTheSameEquipment() {
        for (id in ExerciseFigures.stableIds) {
            val frames = ExerciseFigures.forExercise("catalog_$id")!!
            val start = frames.start.props
            val end = frames.end.props
            assertEquals(start.map { it::class }, end.map { it::class }, "$id: the same equipment in the same order")
            for ((a, b) in start.zip(end)) {
                when (a) {
                    // Fixed equipment does not move between frames; held weights and pads follow the body.
                    is FigureProp.Bench, is FigureProp.Seat, is FigureProp.Bar, is FigureProp.Box, is FigureProp.Floor ->
                        assertEquals(a, b, "$id: $a stays put")
                    is FigureProp.Cable -> {
                        b as FigureProp.Cable
                        assertEquals(Pair(a.anchorX, a.anchorY), Pair(b.anchorX, b.anchorY), "$id: the pulley stays put")
                        assertEquals(a.to, b.to, "$id: the cable goes to the same hand")
                    }
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun framesDiffer() {
        for (id in ExerciseFigures.stableIds) {
            val frames = ExerciseFigures.forExercise("catalog_$id")!!
            assertNotEquals(
                frames.start.copy(props = emptyList(), floorY = null),
                frames.end.copy(props = emptyList(), floorY = null),
                "$id: the body moves between the frames",
            )
        }
    }

    @Test
    fun noOrphanKeys() {
        val catalog = ExerciseCatalog.exercises.mapTo(HashSet()) { it.id }
        for (id in ExerciseFigures.stableIds) assertTrue("catalog_$id" in catalog, "$id is not in the catalog")
    }

    /** Spec, size warning: the tables are split by family, each well under the JVM method limit. */
    @Test
    fun noFamilyTableIsTooLarge() {
        assertEquals(setOf("barbell", "dumbbell", "cable", "bodyweight", "machine"), ExerciseFigures.families.keys)
        for ((family, table) in ExerciseFigures.families) {
            assertTrue(table.size <= 60, "$family has ${table.size} figures")
        }
        assertEquals(ExerciseFigures.families.values.sumOf { it.size }, ExerciseFigures.stableIds.size, "no figure in two families")
    }

    /** Spec D5: a list shows the end frame with the primary muscles; without a figure, the equipment icon. */
    @Test
    fun exerciseMediaIsTheEndFrameOrNone() {
        val bench = ExerciseUi("catalog_barbell_bench_flat", "Barbell bench press", "Chest", "Barbell", "Total load")
        val frames = ExerciseFigures.forExercise(bench.id)!!
        assertEquals(ExerciseMedia.Figure(frames.end, setOf(FigureSegment.TORSO_FRONT)), exerciseMedia(bench))
        val deadlift = ExerciseUi("catalog_barbell_deadlift_conventional", "Conventional barbell deadlift", "Back", "Barbell")
        assertEquals(
            setOf(FigureSegment.TORSO_BACK, FigureSegment.HIP, FigureSegment.THIGH_BACK),
            (exerciseMedia(deadlift) as ExerciseMedia.Figure).highlights,
        )
        // A custom exercise, even one that reuses a catalog id's shape, and a catalog one without a figure yet.
        assertEquals(ExerciseMedia.None, exerciseMedia(bench.copy(id = "custom_1", custom = true)))
        assertEquals(ExerciseMedia.None, exerciseMedia(bench.copy(custom = true)))
        val noFigure = ExerciseCatalog.exercises.first { it.id.removePrefix("catalog_") !in ExerciseFigures.stableIds }
        assertEquals(
            ExerciseMedia.None,
            exerciseMedia(ExerciseUi(noFigure.id, noFigure.name, noFigure.muscle.label, noFigure.equipment.label)),
        )
    }
}
