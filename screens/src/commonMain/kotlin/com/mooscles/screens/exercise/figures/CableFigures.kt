package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/** Cable exercises. Poses drawn by hand; see [FigureFrames]. */
internal object CableFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("cable_lat_pulldown_wide") {
            // Seated, thighs under the pad: arms up on the bar, then the bar to the upper chest.
            start {
                hip(40f, 69f); trunk(-10f, 0f)
                arms(10f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                cable(42f, 8f, HAND_NEAR)
            }
            end {
                hip(40f, 69f); trunk(-12f, 0f)
                arms(190f, 20f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                cable(42f, 8f, HAND_NEAR)
            }
        }
        figure("cable_row_close_neutral") {
            // Seated, feet on the platform: arms reaching forward, then the handle to the stomach.
            start {
                hip(34f, 69f); trunk(12f)
                arms(85f, 92f)
                legs(80f, 105f, 0f)
                groundLine(90f)
                bench(24f, 74f, 20f)
                platform(ANKLE_NEAR, 14f, 0f)
                cable(84f, 58f, HAND_NEAR)
            }
            end {
                hip(34f, 69f); trunk(-5f)
                arms(200f, 85f)
                legs(80f, 105f, 0f)
                groundLine(90f)
                bench(24f, 74f, 20f)
                platform(ANKLE_NEAR, 14f, 0f)
                cable(84f, 58f, HAND_NEAR)
            }
        }
        figure("cable_triceps_pushdown_rope") {
            // Leaning forward a little, elbows at the sides: forearms level, then the arms straight down.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(12f)
                arms(175f, 90f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(12f)
                arms(175f, 175f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
        }
    }
}
