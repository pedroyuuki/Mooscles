package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import com.mooscles.designsystem.components.ExerciseCard
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.SetRow
import com.mooscles.designsystem.components.SetRowState
import com.mooscles.designsystem.components.SetStatus
import com.mooscles.designsystem.components.SetType
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_sample_exercise
import com.mooscles.designsystem.resources.gallery_sample_exercise_meta
import com.mooscles.designsystem.resources.gallery_section_exercise_card
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

private const val TOTAL = 2

private fun row(index: Int, status: SetStatus) = SetRowState(
    index = index, total = TOTAL, previous = null, previousSpoken = null,
    load = "80", loadSpoken = "80", reps = 8, type = SetType.Normal, status = status,
)

/** A painter that has not loaded yet: it draws nothing, so the placeholder under it shows. */
private object LoadingPainter : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() = Unit
}

/** The card while its image loads, with an image, with the offline note, with nothing, and collapsed. */
@Composable
fun ExerciseCardPage() {
    val name = stringResource(Res.string.gallery_sample_exercise)
    val meta = stringResource(Res.string.gallery_sample_exercise_meta)
    val media = listOf(
        "media = Image, loading" to ExerciseMedia.Image(LoadingPainter),
        "media = Image" to ExerciseMedia.Image(ColorPainter(MTheme.colors.accentSubtle)),
        "media = Offline" to ExerciseMedia.Offline,
        "media = None" to ExerciseMedia.None,
    )
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s4),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_exercise_card))
        for ((label, item) in media) {
            Sample(label) {
                ExerciseCard(name, meta, item, MIcons.Barbell, onMenu = {}, onAddSet = {}) {
                    SetRow(row(1, SetStatus.Completed), {}, {}, {}, {}, {})
                    SetRow(row(2, SetStatus.Pending), {}, {}, {}, {}, {})
                }
            }
        }
        Sample("collapsed = true") {
            ExerciseCard(name, meta, ExerciseMedia.None, MIcons.Barbell, onMenu = {}, onAddSet = {}, collapsed = true) {}
        }
    }
}
