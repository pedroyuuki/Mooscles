package com.mooscles.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.ExerciseThumbnail
import com.mooscles.designsystem.components.MInfoChip
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.Skeleton
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.equipmentLabel
import com.mooscles.screens.format.muscleLabel
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.library_custom
import com.mooscles.screens.resources.library_meta
import com.mooscles.screens.resources.library_row
import com.mooscles.screens.resources.library_row_custom
import com.mooscles.screens.workout.EquipmentIcons
import com.mooscles.ui.ExerciseUi
import org.jetbrains.compose.resources.stringResource

/** From this font scale (200%) a library row's name may take three lines instead of two. */
internal const val THREE_LINE_NAME_FONT_SCALE = 2f

/**
 * A library row (spec "Biblioteca", Lista), one button and one node for a screen reader, "Supino
 * reto com barra, Peito, Barra" (", personalizado" for a custom exercise): the 56 dp media
 * ([ExerciseThumbnail] of [media]: the still figure, frame B, or the equipment's icon), [name] in
 * `title.sm` on up to two lines (three at 200%), "Chest · Barbell" and, for a custom exercise, the
 * "Custom" tag. The media and 8 dp above and below it make the row at least 72 dp tall.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryRow(
    exercise: ExerciseUi,
    name: String,
    media: ExerciseMedia,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MTheme.colors
    val muscle = muscleLabel(exercise.muscle)
    val equipment = equipmentLabel(exercise.equipment)
    val label = if (exercise.custom) {
        stringResource(Res.string.library_row_custom, name, muscle, equipment)
    } else {
        stringResource(Res.string.library_row, name, muscle, equipment)
    }
    val nameLines = if (LocalDensity.current.fontScale >= THREE_LINE_NAME_FONT_SCALE) 3 else 2
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(MTheme.radius.md)
    Row(
        modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .mFocusRing(shape, source)
            .background(if (pressed) colors.bgInset else Color.Transparent, shape)
            .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(media, EquipmentIcons.forEquipment(exercise.equipment), MTheme.sizes.mediaList)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
            // As wide as the column, so its layout (and a test's reading of it) is the one drawn.
            MText(
                name,
                MTheme.type.titleSm,
                color = colors.textPrimary,
                maxLines = nameLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag(LibraryTags.ROW_NAME),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                MText(stringResource(Res.string.library_meta, muscle, equipment), MTheme.type.bodyMd, color = colors.textSecondary)
                if (exercise.custom) {
                    MInfoChip(stringResource(Res.string.library_custom), Modifier.testTag(LibraryTags.ROW_CUSTOM))
                }
            }
        }
    }
}

/** A placeholder shaped like [LibraryRow], while the list is not built yet. */
@Composable
internal fun LibrarySkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .testTag(LibraryTags.SKELETON_ROW)
            .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Skeleton(Modifier.size(MTheme.sizes.mediaList), RoundedCornerShape(MTheme.radius.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
            Skeleton(Modifier.fillMaxWidth(NAME_BLOCK_FRACTION).height(MTheme.space.s4))
            Skeleton(Modifier.fillMaxWidth(META_BLOCK_FRACTION).height(MTheme.space.s3))
        }
    }
}

private const val NAME_BLOCK_FRACTION = 0.7f
private const val META_BLOCK_FRACTION = 0.4f
