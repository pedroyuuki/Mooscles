package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_FAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/** Dumbbell exercises. Poses drawn by hand; see [FigureFrames]. */
internal object DumbbellFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("dumbbell_bench_flat") {
            // Lying on the bench, the dumbbells pressed up, then lowered beside the chest.
            start {
                hip(46f, 69f); trunk(-90f)
                arms(0f, 0f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                arms(125f, -20f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_press_seated") {
            // Upright on the bench, the dumbbells at shoulder height, then pressed overhead.
            start {
                hip(42f, 69f); trunk(0f)
                arms(150f, -5f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(5f, 3f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_raise_lateral") {
            // Seen from the front, the only figure that is (a side view cannot show arms rising sideways):
            // both arms and both deltoids, feet as short stubs toward the viewer, toes on the floor.
            start {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(165f, 170f); farArm(195f, 190f)
                nearLeg(172f, 177f, 160f); farLeg(188f, 183f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR); dumbbell(HAND_FAR)
            }
            end {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(85f, 95f); farArm(-85f, -95f)
                nearLeg(172f, 177f, 160f); farLeg(188f, 183f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR); dumbbell(HAND_FAR)
            }
        }
        figure("dumbbell_curl_standing") {
            // Standing, the dumbbells at the sides, then curled with the elbows still.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(175f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(170f, 40f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_squat_bulgarian") {
            // The back foot on a bench; only the front foot is on the floor. Tall, then the front thigh parallel.
            start {
                place(ANKLE_NEAR, 60f, 85f); trunk(0f)
                arms(180f, 180f)
                nearLeg(180f, 180f, 90f); farLeg(215.4f, 259.3f, -90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                bench(18f, 74f, 20f)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 60f, 85f); trunk(12f)
                arms(185f, 185f)
                nearLeg(90f, 200f, 90f); farLeg(210.8f, -23.2f, -90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                bench(18f, 74f, 20f)
                dumbbell(HAND_NEAR)
            }
        }
    }
}
