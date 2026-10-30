package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.HAND_FAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/** Bodyweight exercises. Poses drawn by hand; see [FigureFrames]. */
internal object BodyweightFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("bodyweight_pull_up") {
            // Hands on the bar: a dead hang, then the chin over the bar.
            start {
                place(HAND_NEAR, 48f, 16f); trunk(-6f, -25f)
                arms(16f, 16f)
                legs(172f, 215f, 160f)
                bar(30f, 16f, 34f)
            }
            end {
                place(HAND_NEAR, 48f, 16f); trunk(-8f, 5f)
                arms(150f, 0f)
                legs(165f, 215f, 160f)
                bar(30f, 16f, 34f)
            }
        }
        figure("bodyweight_push_up") {
            // On the hands and toes, body in one line: arms straight, then the chest just above the floor.
            start {
                place(HAND_NEAR, 70f, 85f); trunk(66.9f)
                arms(180f, 180f)
                legs(246.9f, 246.9f, 155f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
            end {
                place(TOE_NEAR, 25.4f, 85f); trunk(84.1f)
                arms(-91.2f, 138.6f)
                legs(264.1f, 264.1f, 150f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
        }
    }
}
