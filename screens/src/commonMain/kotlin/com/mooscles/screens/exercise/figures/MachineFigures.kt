package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR

/** Machine exercises, plate-loaded and selectorized. Poses drawn by hand; see [FigureFrames]. */
internal object MachineFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("plate_leg_press_45") {
            // Reclined against the back pad, feet on the sled: legs almost straight, then the knees bent.
            start {
                hip(40f, 72f); trunk(-55f, -40f)
                arms(125f, 125f)
                legs(55f, 35f, -45f)
                seat(39.6f, 77.8f, 26f, -55f)
                platform(ANKLE_NEAR, 16f, -45f)
            }
            end {
                hip(40f, 72f); trunk(-55f, -40f)
                arms(125f, 125f)
                legs(0f, 90f, -45f)
                seat(39.6f, 77.8f, 26f, -55f)
                platform(ANKLE_NEAR, 16f, -45f)
            }
        }
        figure("selectorized_leg_extension") {
            // Seated, the pad on the lower shins: knees bent, then the legs straight.
            start {
                hip(40f, 60f); trunk(0f)
                arms(180f, 150f)
                legs(90f, 180f, 150f)
                seat(30f, 65f, 24f)
                bench(35f, 65f, 24f, 0f)
                pad(ANKLE_NEAR, 70f, 5.5f, 3f)
            }
            end {
                hip(40f, 60f); trunk(0f)
                arms(180f, 150f)
                legs(90f, 90f, 30f)
                seat(30f, 65f, 24f)
                bench(35f, 65f, 24f, 0f)
                pad(ANKLE_NEAR, -20f, 5.5f, 3f)
            }
        }
        figure("selectorized_leg_curl_lying") {
            // Face down on the bench, the pad above the heels: legs straight, then curled.
            start {
                hip(46f, 60f); trunk(90f)
                arms(150f, 180f)
                legs(-90f, -90f, 180f)
                bench(32f, 65f, 44f)
                pad(ANKLE_NEAR, 20f, 5.5f, 3f)
            }
            end {
                hip(46f, 60f); trunk(90f)
                arms(150f, 180f)
                legs(-90f, 20f, -60f)
                bench(32f, 65f, 44f)
                pad(ANKLE_NEAR, 130f, 5.5f, 3f)
            }
        }
    }
}
