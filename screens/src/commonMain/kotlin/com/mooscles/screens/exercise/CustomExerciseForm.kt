package com.mooscles.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
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
import androidx.compose.ui.unit.DpSize
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
import com.mooscles.designsystem.components.MOverlayWindow
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
import com.mooscles.screens.resources.exercise_form_required_hint
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
    const val REQUIRED_HINT = "exercise-form-required"
    const val MUSCLE_ERROR = "exercise-form-muscle-error"
    const val EQUIPMENT_ERROR = "exercise-form-equipment-error"

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
 * equipment, one chip of each to choose (a line under the title says both are required); and the logging convention, five options with what each
 * one means.
 *
 * A new exercise starts with [prefillName] (the library's search, or nothing) and with no muscle
 * group and no equipment, both required; its id is born here ([newId], plan decision 13), so the
 * host knows it before the state brings the exercise. Choosing an equipment gives the convention
 * its default ([CustomExerciseRules.defaultConvention]) until the person picks one; editing keeps the
 * convention it had. A name another exercise already has, in either language, shows a warning that
 * does not block; one that [NameRule] refuses, or a missing choice, shows its error and keeps the
 * form open: the first problem comes into view, the name field takes the focus, and a section's
 * error is announced. The window has the title as its pane title, and Escape cancels too. When an edit changes the convention of an exercise with logged sets, a line says those
 * sets keep theirs.
 *
 * "Save" clears any earlier error ([MoosclesActions.clearError], so the host only sees this save's),
 * calls [MoosclesActions.createCustomExercise] with the form's id, or
 * [MoosclesActions.saveCustomExercise] for [initial], once, and reports the id to [onSaved]; a later
 * tap (a double tap, or after a recreation) only reports the id again. "Cancel" and Back call
 * [onCancel] and nothing else. The draft (name, choices, id and whether it was saved) is kept with
 * `rememberSaveable`, in the host's composition, so it survives recreation.
 *
 * [pageSize] is for tests: the page that size, at the window's top start, as on a phone that size
 * (the window itself always takes the whole screen, whatever size the host is given).
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
    pageSize: DpSize? = null,
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
    // Folded once per list, not on every keystroke or unrelated state change.
    val names = remember(state.exercises) { CustomExerciseRules.NameIndex(state.exercises) }
    val duplicate = remember(names, name.text) { names.duplicateOf(name.text, exceptId = initial?.id) }
    val duplicateName = duplicate?.let { ExerciseNames.display(it.id, it.name, it.custom, portuguese) }
    val logged = remember(initial?.id, state.history, state.active) {
        initial != null && CustomExerciseRules.hasLoggedSets(initial.id, state.history, state.active)
    }
    val conventionChanges = logged && convention != initial?.convention
    // A refused save brings its first problem (the field, or a section's error) into view and gives it the focus (the name field) or
    // announces it (the section errors are polite live regions).
    val nameFocus = remember { FocusRequester() }
    val nameView = remember { BringIntoViewRequester() }
    val muscleView = remember { BringIntoViewRequester() }
    val equipmentView = remember { BringIntoViewRequester() }
    var refusals by remember { mutableIntStateOf(0) }
    LaunchedEffect(refusals) {
        if (refusals == 0) return@LaunchedEffect
        // Once the errors the refusal shows are laid out.
        withFrameNanos { }
        when {
            NameRule.problem(name.text) != null -> {
                // Focusing the field that already has the focus does not scroll to it.
                runCatching { nameFocus.requestFocus() }
                nameView.bringIntoView()
            }
            muscle == null -> muscleView.bringIntoView()
            equipment == null -> equipmentView.bringIntoView()
        }
    }

    fun save() {
        if (submitted) {
            onSaved(id)
            return
        }
        attempted = true
        val chosenMuscle = muscle
        val chosenEquipment = equipment
        if (NameRule.problem(name.text) != null || chosenMuscle == null || chosenEquipment == null) {
            refusals++
            return
        }
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

    // The design system's overlay window: the host's density and font scale reach the page.
    MOverlayWindow(onDismissRequest = onCancel) {
        val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        val title = stringResource(if (initial == null) Res.string.exercise_form_new else Res.string.exercise_form_edit)
        val windowFocus = remember { FocusRequester() }
        // Inside the window's own composition, so the requester is attached before it is used; with
        // the focus inside, Escape (and a hardware Back key) reach the window, as in the other overlays.
        LaunchedEffect(Unit) { runCatching { windowFocus.requestFocus() } }
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .then(if (pageSize == null) Modifier.fillMaxSize() else Modifier.size(pageSize))
                    .testTag(CustomExerciseFormTags.ROOT)
                    .semantics { paneTitle = title }
                    .onPreviewKeyEvent { event ->
                        val close = event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Back)
                        if (close) onCancel()
                        close
                    }
                    .focusRequester(windowFocus)
                    .focusable()
                    .background(MTheme.colors.bgBase)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = MTheme.sizes.rowMin)
                        // "Cancel" (text) keeps its own inset; "Save" (filled) ends with the content, 16 dp in.
                        .padding(start = MTheme.space.s2, end = MTheme.space.s4),
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
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
                        MText(
                            title,
                            MTheme.type.titleLg,
                            color = MTheme.colors.textPrimary,
                            modifier = Modifier.fillMaxWidth().testTag(CustomExerciseFormTags.TITLE).semantics { heading() },
                        )
                        // What is required, said before the first save (WCAG 3.3.2).
                        MText(
                            stringResource(Res.string.exercise_form_required_hint),
                            MTheme.type.bodyMd,
                            color = MTheme.colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().testTag(CustomExerciseFormTags.REQUIRED_HINT),
                        )
                    }
                    MTextField(
                        value = name,
                        // A name is one line: a line break typed or pasted becomes a space.
                        onValueChange = { name = it.copy(text = it.text.replace('\n', ' ').replace('\r', ' ')) },
                        label = stringResource(Res.string.exercise_form_name),
                        modifier = Modifier.fillMaxWidth().testTag(CustomExerciseFormTags.NAME).focusRequester(nameFocus).bringIntoViewRequester(nameView),
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
                        errorView = muscleView,
                        errorTag = CustomExerciseFormTags.MUSCLE_ERROR,
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
                        errorView = equipmentView,
                        errorTag = CustomExerciseFormTags.EQUIPMENT_ERROR,
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
}

/**
 * A titled part of the form ([title] is a heading), with its [error] under the content: a polite
 * live region, so it is announced when a refused save shows it, and [errorView] brings it into view
 * (the error itself: with large text a section of chips can be taller than the screen).
 */
@Composable
private fun FormSection(
    title: String,
    error: String?,
    modifier: Modifier = Modifier,
    errorView: BringIntoViewRequester? = null,
    errorTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        MText(title, MTheme.type.titleSm, color = MTheme.colors.textSecondary, modifier = Modifier.fillMaxWidth().semantics { heading() })
        content()
        if (error != null) {
            FormNote(
                MIcons.CircleAlert,
                error,
                MTheme.colors.dangerText,
                Modifier
                    .then(if (errorTag != null) Modifier.testTag(errorTag) else Modifier)
                    .then(if (errorView != null) Modifier.bringIntoViewRequester(errorView) else Modifier)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            )
        }
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
