package com.mooscles.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_add_set
import com.mooscles.designsystem.resources.ds_exercise_menu
import com.mooscles.designsystem.resources.ds_media_offline
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

const val ExerciseCardTag = "exercise-card"
const val ExerciseMenuTag = "exercise-menu"
const val ExerciseAddSetTag = "exercise-add-set"
const val ExerciseEquipmentIconTag = "exercise-equipment-icon"
const val ExerciseOfflineIconTag = "exercise-offline-icon"
const val ExerciseOfflineTextTag = "exercise-offline-text"
const val ExerciseSetsTag = "exercise-sets"

/** What the 64 dp thumbnail of an exercise shows. */
@Immutable
sealed interface ExerciseMedia {
    /** The exercise has no image: only the equipment icon is drawn. */
    data object None : ExerciseMedia

    /** The image exists but is not on the device yet: an "image off" icon and a short note. */
    data object Offline : ExerciseMedia

    data class Image(val painter: Painter) : ExerciseMedia
}

/**
 * An exercise in the workout: the thumbnail ([media], 64 dp), [name], [meta] (muscle, equipment),
 * the options menu and, below, the set rows given by [sets] and a 56 dp "Add set" button. With
 * [collapsed] only the header remains. Without an image the thumbnail is the equipment icon;
 * with one (or the offline note) the equipment icon sits beside [meta].
 */
@Composable
fun ExerciseCard(
    name: String,
    meta: String,
    media: ExerciseMedia,
    equipmentIcon: ImageVector,
    onMenu: () -> Unit,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    sets: @Composable ColumnScope.() -> Unit,
) {
    val colors = MTheme.colors
    val cardShape = RoundedCornerShape(MTheme.radius.lg)
    val thumbShape = RoundedCornerShape(MTheme.radius.md)
    Column(
        modifier
            .testTag(ExerciseCardTag)
            .fillMaxWidth()
            .background(colors.bgSurface, cardShape)
            .border(MTheme.sizes.border, colors.borderSubtle, cardShape)
            .padding(MTheme.space.s3),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(MTheme.sizes.media).clip(thumbShape).background(colors.bgInset),
                contentAlignment = Alignment.Center,
            ) {
                when (media) {
                    is ExerciseMedia.Image -> Image(
                        media.painter,
                        contentDescription = null,
                        modifier = Modifier.size(MTheme.sizes.media),
                        contentScale = ContentScale.Crop,
                    )
                    ExerciseMedia.Offline -> MIcon(
                        MIcons.ImageOff, null, Modifier.testTag(ExerciseOfflineIconTag),
                        size = MTheme.sizes.icon24, tint = colors.textTertiary,
                    )
                    ExerciseMedia.None -> MIcon(
                        equipmentIcon, null, Modifier.testTag(ExerciseEquipmentIconTag),
                        size = MTheme.sizes.icon28, tint = colors.textTertiary,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                MText(name, MTheme.type.titleMd, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1), verticalAlignment = Alignment.CenterVertically) {
                    if (media != ExerciseMedia.None) {
                        MIcon(equipmentIcon, null, Modifier.testTag(ExerciseEquipmentIconTag), size = MTheme.sizes.icon16, tint = colors.textTertiary)
                    }
                    MText(meta, MTheme.type.bodyMd, color = colors.textSecondary)
                }
                if (media == ExerciseMedia.Offline) {
                    MText(
                        stringResource(Res.string.ds_media_offline),
                        MTheme.type.caption,
                        modifier = Modifier.testTag(ExerciseOfflineTextTag),
                        color = colors.textSecondary,
                    )
                }
            }
            MIconButton(MIcons.EllipsisVertical, stringResource(Res.string.ds_exercise_menu), onMenu, Modifier.testTag(ExerciseMenuTag))
        }
        if (!collapsed) {
            Column(Modifier.testTag(ExerciseSetsTag), verticalArrangement = Arrangement.spacedBy(MTheme.targets.gap)) {
                sets()
            }
            MButton(
                stringResource(Res.string.ds_add_set), onAddSet,
                modifier = Modifier.fillMaxWidth().testTag(ExerciseAddSetTag),
                variant = MButtonVariant.Tonal,
                size = MButtonSize.L,
                icon = MIcons.Plus,
            )
        }
    }
}
