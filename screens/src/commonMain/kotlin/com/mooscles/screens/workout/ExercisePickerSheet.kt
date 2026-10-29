package com.mooscles.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MTextField
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.equipmentLabel
import com.mooscles.screens.format.muscleLabel
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_picker_all
import com.mooscles.screens.resources.workout_picker_meta
import com.mooscles.screens.resources.workout_picker_none
import com.mooscles.screens.resources.workout_picker_recent
import com.mooscles.screens.resources.workout_picker_search
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutUi
import org.jetbrains.compose.resources.stringResource

/** The tag of the picker's list. */
internal const val ExercisePickerListTag = "exercise-picker-list"

/** The tag of the equipment icon of a picker row. */
internal const val ExercisePickerEquipmentIconTag = "exercise-picker-equipment"

/** The tag of the row of [exerciseId] in "Recent" ([recent]) or in "All". */
internal fun exercisePickerRowTag(exerciseId: String, recent: Boolean) =
    if (recent) "exercise-picker-recent-$exerciseId" else "exercise-picker-all-$exerciseId"

/**
 * The exercise picker (spec "Seletor de exercício"): an `MBottomSheet` at 90% of the screen with
 * a search field ("Search exercises"), then "Recent" (up to 8 exercises of the latest workouts in
 * [history], [ExercisePicker.recents], each shown as [exercises] has it now; an exercise no longer
 * there is left out) and "All" ([exercises] in alphabetical order). A search lists only the
 * matches ([ExercisePicker.filter]), without "Recent", or says that nothing matched. Each row, 56 dp
 * or taller, has the exercise's name, "Chest · Barbell" in the app's language and the equipment's
 * icon.
 *
 * A tap on a row reports the exercise to [onPick] and then closes the picker through [onDismiss],
 * once: the caller turns the pick into `addWorkoutExercise` or `replaceWorkoutExercise` and drops
 * the sheet in [onDismiss], which is also the close without a choice (the scrim, Back, a drag
 * down). The search survives recreation.
 */
@Composable
internal fun ExercisePickerSheet(
    exercises: List<ExerciseUi>,
    history: List<WorkoutUi>,
    onPick: (ExerciseUi) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var picked by remember { mutableStateOf(false) }
    val searching = query.text.isNotBlank()
    val recents = remember(exercises, history) {
        val current = exercises.associateBy { it.id }
        ExercisePicker.recents(history, limit = Int.MAX_VALUE).mapNotNull { current[it.id] }.take(ExercisePicker.RECENT_LIMIT)
    }
    val results = remember(exercises, query.text) { ExercisePicker.filter(exercises, query.text) }
    val pick: (ExerciseUi) -> Unit = { exercise ->
        if (!picked) {
            picked = true
            onPick(exercise)
            onDismiss()
        }
    }
    MBottomSheet(onDismiss = onDismiss) {
        MTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(Res.string.workout_picker_search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = MTheme.space.s4).padding(bottom = MTheme.space.s2),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            singleLine = true,
        )
        // Weighted, so the sheet keeps its full height while the matches change.
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag(ExercisePickerListTag),
            contentPadding = PaddingValues(bottom = MTheme.space.s4),
        ) {
            if (!searching && recents.isNotEmpty()) {
                item(key = "header-recent") { SectionHeader(stringResource(Res.string.workout_picker_recent)) }
                items(recents, key = { "recent-${it.id}" }) { exercise ->
                    PickerRow(exercise, exercisePickerRowTag(exercise.id, recent = true)) { pick(exercise) }
                }
            }
            if (results.isNotEmpty()) {
                item(key = "header-all") { SectionHeader(stringResource(Res.string.workout_picker_all)) }
                items(results, key = { "all-${it.id}" }) { exercise ->
                    PickerRow(exercise, exercisePickerRowTag(exercise.id, recent = false)) { pick(exercise) }
                }
            } else {
                item(key = "none") {
                    MText(
                        stringResource(Res.string.workout_picker_none),
                        MTheme.type.bodyMd,
                        color = MTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(MTheme.space.s4),
                    )
                }
            }
        }
    }
}

/** A picker row: the equipment's icon, the name and "Chest · Barbell"; the whole row is one button. */
@Composable
private fun PickerRow(exercise: ExerciseUi, tag: String, onClick: () -> Unit) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(MTheme.radius.md)
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .heightIn(min = MTheme.sizes.rowMin)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .mFocusRing(shape, source)
            .background(if (pressed) colors.bgInset else Color.Transparent, shape)
            .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s1),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MIcon(
            EquipmentIcons.forEquipment(exercise.equipment),
            contentDescription = null,
            size = MTheme.sizes.icon24,
            tint = colors.textSecondary,
            modifier = Modifier.testTag(ExercisePickerEquipmentIconTag),
        )
        Column(Modifier.weight(1f)) {
            MText(exercise.name, MTheme.type.bodyLg, color = colors.textPrimary)
            MText(
                stringResource(Res.string.workout_picker_meta, muscleLabel(exercise.muscle), equipmentLabel(exercise.equipment)),
                MTheme.type.bodyMd,
                color = colors.textSecondary,
            )
        }
    }
}
