package com.mooscles.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mooscles.data.Equipment
import com.mooscles.data.ExerciseNames
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.NameRule
import com.mooscles.data.newId
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MTextField
import com.mooscles.designsystem.components.RadioGroup
import com.mooscles.designsystem.components.RadioRow
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.format.conventionExplanation
import com.mooscles.screens.format.conventionLabel
import com.mooscles.screens.format.equipmentLabel
import com.mooscles.screens.format.muscleLabel
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.exercise_form_cancel
import com.mooscles.screens.resources.exercise_form_convention
import com.mooscles.screens.resources.exercise_form_convention_change
import com.mooscles.screens.resources.exercise_form_duplicate
import com.mooscles.screens.resources.exercise_form_edit
import com.mooscles.screens.resources.exercise_form_equipment
import com.mooscles.screens.resources.exercise_form_equipment_required
import com.mooscles.screens.resources.exercise_form_muscle
import com.mooscles.screens.resources.exercise_form_muscle_required
import com.mooscles.screens.resources.exercise_form_name
import com.mooscles.screens.resources.exercise_form_name_long
import com.mooscles.screens.resources.exercise_form_name_required
import com.mooscles.screens.resources.exercise_form_new
import com.mooscles.screens.resources.exercise_form_save
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.UiState
import org.jetbrains.compose.resources.stringResource

/** The test tags of [CustomExerciseForm]. */
internal object CustomExerciseFormTags {
    const val ROOT = "exercise-form"
    const val TITLE = "exercise-form-title"
    const val NAME = "exercise-form-name"
    const val CANCEL = "exercise-form-cancel"
    const val SAVE = "exercise-form-save"
    const val CONVENTION_CHANGE = "exercise-form-convention-change"

    /** The chip of the muscle group whose English label is [label] ("Chest"). */
    fun muscle(label: String) = "exercise-form-muscle-$label"

    /** The chip of the equipment whose English label is [label] ("Barbell"). */
    fun equipment(label: String) = "exercise-form-equipment-$label"

    /** The option of the convention whose English label is [label] ("Total load"). */
    fun convention(label: String) = "exercise-form-convention-$label"
}

/**
 * The custom exercise form (spec "Formulário de exercício personalizado", without the photo of
 * D2): a full screen page over the screen that opens it, in a window of its own, so Back cancels it
 * like "Cancel" does (pre-flight F8: a page, not a 90% sheet). From the top: "Cancel" and "Save";
 * the title ("New exercise", or "Edit exercise" for [initial]); the name; the muscle group and the
 * equipment, one chip of each to choose; and the logging convention, five options with what each
 * one means.
 *
 * A new exercise starts with [prefillName] (the library's search, or nothing) and with no muscle
 * group and no equipment, both required; its id is born here ([newId], plan decision 13), so the
 * host knows it before the state brings the exercise. Choosing an equipment gives the convention
 * its default ([CustomExerciseRules.defaultConvention]) until the person picks one; editing keeps the
 * convention it had. A name another exercise already has, in either language, shows a warning that
 * does not block; one that [NameRule] refuses, or a missing choice, shows its error and keeps the
 * form open. When an edit changes the convention of an exercise with logged sets, a line says those
 * sets keep theirs.
 *
 * "Save" clears any earlier error ([MoosclesActions.clearError], so the host only sees this save's),
 * calls [MoosclesActions.createCustomExercise] with the form's id, or
 * [MoosclesActions.saveCustomExercise] for [initial], once, and reports the id to [onSaved]; a later
 * tap (a double tap, or after a recreation) only reports the id again. "Cancel" and Back call
 * [onCancel] and nothing else. The draft (name, choices, id and whether it was saved) is kept with
 * `rememberSaveable`, in the host's composition, so it survives recreation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomExerciseForm(
    initial: ExerciseUi?,
    prefillName: String,
    state: UiState,
    actions: MoosclesActions,
    onSaved: (exerciseId: String) -> Unit,
    onCancel: () -> Unit,
) {
    val id = rememberSaveable { initial?.id ?: newId() }
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val start = initial?.name ?: prefillName
        mutableStateOf(TextFieldValue(start, TextRange(start.length)))
    }
    var muscle by rememberSaveable { mutableStateOf(initial?.muscle) }
    var equipment by rememberSaveable { mutableStateOf(initial?.equipment) }
    var convention by rememberSaveable { mutableStateOf(initial?.convention ?: LoadConvention.TOTAL_LOAD.label) }
    var conventionChosen by rememberSaveable { mutableStateOf(initial != null) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val portuguese = catalogInPortuguese()
    val duplicate = CustomExerciseRules.duplicateOf(name.text, state.exercises, exceptId = initial?.id)
    val duplicateName = duplicate?.let { ExerciseNames.display(it.id, it.name, it.custom, portuguese) }
    val conventionChanges = initial != null && convention != initial.convention &&
        CustomExerciseRules.hasLoggedSets(initial.id, state.history, state.active)

    fun save() {
        if (submitted) {
            onSaved(id)
            return
        }
        attempted = true
        val chosenMuscle = muscle
        val chosenEquipment = equipment
        if (NameRule.problem(name.text) != null || chosenMuscle == null || chosenEquipment == null) return
        submitted = true
        val trimmed = name.text.trim()
        actions.clearError()
        if (initial == null) {
            actions.createCustomExercise(id, trimmed, chosenMuscle, chosenEquipment, convention)
        } else {
            actions.saveCustomExercise(id, trimmed, chosenMuscle, chosenEquipment, convention)
        }
        onSaved(id)
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        Column(
            Modifier
                .fillMaxSize()
                .testTag(CustomExerciseFormTags.ROOT)
                .background(MTheme.colors.bgBase)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = MTheme.sizes.rowMin)
                    .padding(horizontal = MTheme.space.s2),
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MButton(
                    stringResource(Res.string.exercise_form_cancel),
                    onClick = onCancel,
                    modifier = Modifier.testTag(CustomExerciseFormTags.CANCEL),
                    variant = MButtonVariant.Text,
                )
                // Pushes "Save" to the end; with large text the two buttons share the line instead.
                Spacer(Modifier.weight(1f))
                MButton(
                    stringResource(Res.string.exercise_form_save),
                    onClick = ::save,
                    modifier = Modifier.testTag(CustomExerciseFormTags.SAVE),
                    variant = MButtonVariant.Primary,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MTheme.space.s4)
                    .padding(top = MTheme.space.s2, bottom = bottom + MTheme.space.s6),
                verticalArrangement = Arrangement.spacedBy(MTheme.space.s6),
            ) {
                MText(
                    stringResource(if (initial == null) Res.string.exercise_form_new else Res.string.exercise_form_edit),
                    MTheme.type.titleLg,
                    color = MTheme.colors.textPrimary,
                    modifier = Modifier.fillMaxWidth().testTag(CustomExerciseFormTags.TITLE).semantics { heading() },
                )
                MTextField(
                    value = name,
                    // A name is one line: a line break typed or pasted becomes a space.
                    onValueChange = { name = it.copy(text = it.text.replace('\n', ' ').replace('\r', ' ')) },
                    label = stringResource(Res.string.exercise_form_name),
                    modifier = Modifier.fillMaxWidth().testTag(CustomExerciseFormTags.NAME),
                    help = duplicateName?.let { stringResource(Res.string.exercise_form_duplicate, it) },
                    error = if (!attempted) null else when (NameRule.problem(name.text)) {
                        NameRule.Problem.BLANK -> stringResource(Res.string.exercise_form_name_required)
                        NameRule.Problem.TOO_LONG -> stringResource(Res.string.exercise_form_name_long)
                        null -> null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                FormSection(
                    title = stringResource(Res.string.exercise_form_muscle),
                    error = if (attempted && muscle == null) stringResource(Res.string.exercise_form_muscle_required) else null,
                ) {
                    ChoiceChips(
                        options = MuscleGroup.entries.map { it.label },
                        selected = muscle,
                        label = { muscleLabel(it) },
                        tag = CustomExerciseFormTags::muscle,
                        onSelect = { muscle = it },
                    )
                }
                FormSection(
                    title = stringResource(Res.string.exercise_form_equipment),
                    error = if (attempted && equipment == null) stringResource(Res.string.exercise_form_equipment_required) else null,
                ) {
                    ChoiceChips(
                        options = Equipment.entries.map { it.label },
                        selected = equipment,
                        label = { equipmentLabel(it) },
                        tag = CustomExerciseFormTags::equipment,
                        onSelect = { label ->
                            equipment = label
                            if (!conventionChosen) {
                                Equipment.entries.firstOrNull { it.label == label }?.let {
                                    convention = CustomExerciseRules.defaultConvention(it).label
                                }
                            }
                        },
                    )
                }
                FormSection(title = stringResource(Res.string.exercise_form_convention), error = null) {
                    RadioGroup {
                        for (option in LoadConvention.entries) {
                            RadioRow(
                                title = conventionLabel(option.label),
                                subtitle = conventionExplanation(option.label),
                                selected = convention == option.label,
                                onSelect = {
                                    convention = option.label
                                    conventionChosen = true
                                },
                                modifier = Modifier.testTag(CustomExerciseFormTags.convention(option.label)),
                            )
                        }
                    }
                    if (conventionChanges) {
                        FormNote(
                            MIcons.Info,
                            stringResource(Res.string.exercise_form_convention_change),
                            MTheme.colors.textSecondary,
                            Modifier.testTag(CustomExerciseFormTags.CONVENTION_CHANGE),
                        )
                    }
                }
            }
        }
    }
}

/** A titled part of the form ([title] is a heading), with its [error] under the content. */
@Composable
private fun FormSection(title: String, error: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        MText(title, MTheme.type.titleSm, color = MTheme.colors.textSecondary, modifier = Modifier.fillMaxWidth().semantics { heading() })
        content()
        if (error != null) FormNote(MIcons.CircleAlert, error, MTheme.colors.dangerText)
    }
}

/** A line of text after [icon], so its meaning is never carried by [color] alone. */
@Composable
private fun FormNote(icon: ImageVector, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2), verticalAlignment = Alignment.Top) {
        MIcon(icon, contentDescription = null, size = MTheme.sizes.icon16, tint = color, modifier = Modifier.padding(top = MTheme.space.s1))
        MText(text, MTheme.type.caption, color = color, modifier = Modifier.weight(1f))
    }
}

/** One choice of [options] (English labels), as chips that wrap; [label] gives each one's text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(
    options: List<String>,
    selected: String?,
    label: @Composable (String) -> String,
    tag: (String) -> String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
    ) {
        for (option in options) {
            MChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.testTag(tag(option)),
                kind = MChipKind.SetType,
            )
        }
    }
}
