package com.mooscles.screens.exercise.figures

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.ExerciseNames
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.ExerciseThumbnail
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.gallery.GalleryExtraPage
import com.mooscles.designsystem.illustration.ExerciseFigure
import com.mooscles.designsystem.illustration.FigurePose
import com.mooscles.designsystem.illustration.FigureSegment
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_page_figures
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.exerciseMedia
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.workout.EquipmentIcons
import com.mooscles.ui.ExerciseUi
import org.jetbrains.compose.resources.stringResource

/** The gallery's key of the figures page. */
const val FIGURES_GALLERY_KEY = "figures"

/** Test tag of one frame on the figures page: `figure-<stableId>-start` or `-end`. */
fun figureFrameTag(stableId: String, start: Boolean): String = "figure-$stableId-${if (start) "start" else "end"}"

/**
 * The design gallery's "Figures" page (plan decision 5), for reviewing the poses: every exercise
 * with a figure, by its shown name, with the list thumbnail (the end frame at `sizes.media`) and the
 * start and end frames side by side at half the width. It follows the gallery's theme, text size
 * and language controls.
 */
fun figuresGalleryPage(): GalleryExtraPage = GalleryExtraPage(
    key = FIGURES_GALLERY_KEY,
    title = { stringResource(Res.string.gallery_page_figures) },
    content = { FiguresPage() },
)

@Composable
private fun FiguresPage() {
    val portuguese = catalogInPortuguese()
    val exercises = ExerciseCatalog.exercises.associateBy { it.id }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s6),
    ) {
        for (stableId in ExerciseFigures.stableIds) {
            val frames = ExerciseFigures.forExercise("catalog_$stableId") ?: continue
            val exercise = exercises.getValue("catalog_$stableId").let {
                ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label)
            }
            val media = exerciseMedia(exercise)
            val highlights = (media as? ExerciseMedia.Figure)?.highlights.orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExerciseThumbnail(media, EquipmentIcons.forEquipment(exercise.equipment), MTheme.sizes.media)
                    MText(ExerciseNames.display(exercise.id, exercise.name, custom = false, portuguese), MTheme.type.titleSm)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
                    Frame(frames.start, highlights, figureFrameTag(stableId, start = true), Modifier.weight(1f))
                    Frame(frames.end, highlights, figureFrameTag(stableId, start = false), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Frame(pose: FigurePose, highlights: Set<FigureSegment>, tag: String, modifier: Modifier) {
    ExerciseFigure(
        pose, highlights, contentDescription = null,
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(MTheme.radius.md))
            .background(MTheme.colors.bgInset)
            .testTag(tag),
    )
}
