package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HIP
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/** Barbell exercises (and, from the lots on, the trap bar). Poses drawn by hand; see [FigureFrames]. */
internal object BarbellFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("barbell_bench_flat") {
            // Lying on the bench, head to the left; the bar over the shoulders, then on the lower chest.
            start {
                hip(46f, 69f); trunk(-90f)
                arms(0f, 0f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                barbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                arms(155f, -5f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_back_squat_high_bar") {
            // Standing with the bar on the upper back, then at the bottom with the thighs about parallel.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(190f, -5f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(35f, 20f)
                arms(225f, 30f)
                legs(85f, 205f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_deadlift_conventional") {
            // The bar on the floor over the mid-foot, hips above the knees; then standing tall.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(56f, 65f)
                arms(180f, 180f)
                legs(114.6f, 192f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(0f)
                arms(168f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_overhead_press") {
            // The bar on the front of the shoulders, then locked out overhead.
            start {
                place(ANKLE_NEAR, 46f, 90f); trunk(0f)
                arms(150f, -10f)
                legs(180f, 180f, 90f)
                floor(90f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 90f); trunk(0f, 10f)
                arms(5f, 5f)
                legs(180f, 180f, 90f)
                floor(90f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_row_overhand") {
            // Hinged with a flat back, arms hanging; then the bar pulled to the upper stomach.
            start {
                place(ANKLE_NEAR, 52f, 85f); trunk(62f, 70f)
                arms(180f, 180f)
                legs(150f, 185f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 52f, 85f); trunk(62f, 70f)
                arms(270f, 180f)
                legs(150f, 185f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_deadlift_romanian") {
            // Standing with the bar at the thighs, then hips back and the bar below the knees.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(178f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(70f, 75f)
                arms(190f, 190f)
                legs(135f, 190f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_hip_thrust") {
            // Upper back on the bench, the bar across the hips: hips low, then level with the knees.
            start {
                place(ANKLE_NEAR, 66f, 85f); trunk(-62f, -60f)
                arms(75f, 140f)
                legs(57f, 162f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 18f)
                barbell(HIP)
            }
            end {
                place(ANKLE_NEAR, 66f, 85f); trunk(-90f, -60f)
                arms(70f, 110f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 18f)
                barbell(HIP)
            }
        }
    }
}
