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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.MToastKind
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MTextField
import com.mooscles.designsystem.components.RadioGroup
import com.mooscles.designsystem.components.RadioRow
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.photo.LocalExercisePhotos
import com.mooscles.screens.exercise.photo.PhotoRow
import com.mooscles.screens.exercise.photo.PhotoSource
import com.mooscles.screens.exercise.photo.rememberStoredPhoto
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
import com.mooscles.screens.resources.exercise_form_photo
import com.mooscles.screens.resources.exercise_photo_failed
import com.mooscles.screens.resources.exercise_form_name
import com.mooscles.screens.resources.exercise_form_name_long
import com.mooscles.screens.resources.exercise_form_name_required
import com.mooscles.screens.resources.exercise_form_new
import com.mooscles.screens.resources.exercise_form_required_hint
import com.mooscles.screens.resources.exercise_form_save
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import com.mooscles.screens.workout.EquipmentIcons
import com.mooscles.screens.workout.uiErrorText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * The custom exercise form (spec "Formulário de exercício personalizado"): a full screen page over the screen that opens it, in a window of its own, so Back cancels it
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
 * "Save" clears any earlier error ([MoosclesActions.clearError], so the host only sees this save's) and calls
 * [MoosclesActions.createCustomExercise] with the form's id, or [MoosclesActions.saveCustomExercise] for [initial].
 * A creation keeps the form open and busy (the button spins, further taps do nothing) until the state brings the
 * exercise, then reports its id to [onSaved]; a failure of that command (`UiState.subjectFailures` of the id grows
 * past its count when the command was sent) frees the form again, with the error shown over it and handed back
 * through `clearError()`, and an error about anything else changes nothing (pre-flight F10). A wait also ends after
 * `timing.saveEchoWait`. The wait is not saved: after a recreation the form is open and not busy, with its draft.
 * An edit reports [onSaved] once the command is sent. "Cancel" and Back call [onCancel] (and drop the photo draft)
 * and nothing else. The draft (name, choices, id and the photo's state) is kept with `rememberSaveable`, in the
 * host's composition, so it survives recreation.
 *
 * With [com.mooscles.screens.exercise.photo.LocalExercisePhotos] provided (and a capture), a "Photo" section follows
 * the name: the preview, "Choose from gallery", "Take photo" (only with a camera) and, with a photo, "Remove
 * photo". A picked photo is a draft in the store under the form's id; after the creation is echoed it is committed
 * under that id before [onSaved] (an edit commits it, or removes the saved photo, when it is saved). A photo that
 * cannot be kept is [onPhotoFailed], and the exercise stays.
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
    onPhotoFailed: () -> Unit = {},
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
    // The photo: a draft in the store under the form's id, and (editing) whether the saved photo goes on save.
    var hasDraft by rememberSaveable { mutableStateOf(false) }
    var removeOnSave by rememberSaveable { mutableStateOf(false) }
    // The wait for the echo of a creation: the failure count of [id] when the command was sent. Never saved (preflight
    // F10): after process death the view model starts with an empty map and the queued command is lost, so a restored
    // wait would never end; the form comes back open and not busy, with its draft.
    var wait by remember { mutableStateOf<Int?>(null) }
    // The save was echoed (or is an edit) and the photo is being applied; nothing else can be sent meanwhile.
    var finishing by remember { mutableStateOf(false) }
    val busy = wait != null || finishing
    val scope = rememberCoroutineScope()
    val toast = remember { MToastHostState() }
    val photoFailedText = stringResource(Res.string.exercise_photo_failed)
    val photos = LocalExercisePhotos.current
    val capture = photos?.capture
    val staged: (String?) -> Unit = { uri ->
        if (uri != null && photos != null && !busy) {
            scope.launch {
                if (photos.store.stage(id, uri)) {
                    hasDraft = true
                    removeOnSave = false
                } else {
                    toast.show(photoFailedText, MToastKind.Error)
                }
            }
        }
    }
    val latestStaged by rememberUpdatedState(staged)
    val pickGallery = capture?.rememberGalleryLauncher { latestStaged(it) }
    val pickCamera = if (capture != null && capture.cameraAvailable) capture.rememberCameraLauncher { latestStaged(it) } else null

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

    // Applies the photo to [id]: a creation has just been echoed (the exercise exists, so the draft can move under its
    // id), an edit has just been sent. A removal only happens here, on save. A photo that cannot be kept is the host's
    // toast; the exercise stays.
    suspend fun applyPhoto() {
        val store = photos?.store ?: return
        if (removeOnSave && initial != null) {
            store.remove(id)
        } else if (hasDraft && !store.commit(id, id)) {
            onPhotoFailed()
        }
    }

    fun finish() {
        finishing = true
        scope.launch {
            applyPhoto()
            onSaved(id)
        }
    }

    fun save() {
        if (busy) return
        attempted = true
        val chosenMuscle = muscle
        val chosenEquipment = equipment
        if (NameRule.problem(name.text) != null || chosenMuscle == null || chosenEquipment == null) {
            refusals++
            return
        }
        val trimmed = name.text.trim()
        actions.clearError()
        if (initial == null) {
            // The baseline is the failure count of [id] now. A reused id (the same form saved again after a recreation)
            // may already have failures counted before this send, so a failure of an older send can release this wait early.
            wait = state.subjectFailures[id] ?: 0
            actions.createCustomExercise(id, trimmed, chosenMuscle, chosenEquipment, convention)
        } else {
            actions.saveCustomExercise(id, trimmed, chosenMuscle, chosenEquipment, convention)
            finish()
        }
    }

    fun cancel() {
        if (finishing) return
        if (hasDraft) photos?.discardInBackground(id)
        onCancel()
    }

    // The creation's echo: the exercise comes in the state (the form closes through [onSaved], after the photo is
    // committed under the new id), or the count of failures about [id] grows past the baseline (an unrelated error
    // changes neither, and a second error cannot hide the first as it would [UiState.errorCode]): the form stays,
    // the error shows over it, and Save is free again.
    val failedText = uiErrorText(state.errorCode ?: UiError.SAVE_FAILED)
    LaunchedEffect(wait, state.exercises, state.subjectFailures) {
        val baseline = wait ?: return@LaunchedEffect
        if (finishing) return@LaunchedEffect
        if (state.exercises.any { it.id == id }) {
            finish()
        } else if ((state.subjectFailures[id] ?: 0) > baseline) {
            wait = null
            actions.clearError()
            scope.launch { toast.show(failedText, MToastKind.Error) }
        }
    }
    // A command that was lost must not hold the form for good.
    val echoWait = MTheme.timing.saveEchoWait
    LaunchedEffect(wait) {
        if (wait == null) return@LaunchedEffect
        delay(echoWait)
        if (!finishing && wait != null) {
            wait = null
            toast.show(failedText, MToastKind.Error)
        }
    }

    // The design system's overlay window: the host's density and font scale reach the page.
    MOverlayWindow(onDismissRequest = ::cancel) {
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
                        if (close) cancel()
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
                        onClick = ::cancel,
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
                        loading = busy,
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
                    if (photos != null && capture != null && pickGallery != null) {
                        val source = when {
                            hasDraft -> PhotoSource.Draft(id)
                            initial != null && !removeOnSave -> PhotoSource.Exercise(initial.id)
                            else -> null
                        }
                        val preview = if (source != null) rememberStoredPhoto(photos, source, thumbnail = true) else null
                        FormSection(title = stringResource(Res.string.exercise_form_photo), error = null) {
                            PhotoRow(
                                preview = preview,
                                icon = EquipmentIcons.forEquipment(equipment ?: ""),
                                cameraAvailable = pickCamera != null,
                                onGallery = { if (!busy) pickGallery() },
                                onCamera = { if (!busy) pickCamera?.invoke() },
                                onRemove = {
                                    if (!busy) {
                                        if (hasDraft) photos.discardInBackground(id)
                                        hasDraft = false
                                        removeOnSave = initial != null
                                    }
                                },
                            )
                        }
                    }
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
                                    Equipment.fromLabelOrName(label)?.let {
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
            MToastHost(toast, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
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
