package com.mooscles.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.components.EmptyState
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MSearchField
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.library_all
import com.mooscles.screens.resources.library_catalog_empty
import com.mooscles.screens.resources.library_catalog_empty_body
import com.mooscles.screens.resources.library_clear_filters
import com.mooscles.screens.resources.library_count
import com.mooscles.screens.resources.library_create
import com.mooscles.screens.resources.library_create_named
import com.mooscles.screens.resources.library_empty
import com.mooscles.screens.resources.library_empty_body
import com.mooscles.screens.resources.library_recent
import com.mooscles.screens.resources.library_search
import com.mooscles.screens.resources.library_settings
import com.mooscles.screens.resources.library_title
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutUi
import org.jetbrains.compose.resources.stringResource

/** The tag of the library route's root. */
const val LibraryScreenTag = "library-screen"

/** The test tags of the library (Task 15) and of the filters the picker reuses (Task 18). */
object LibraryTags {
    const val SEARCH = "library-search"
    const val LIST = "library-list"
    const val EQUIPMENT_CHIP = "library-equipment-chip"
    const val EQUIPMENT_SHEET = "library-equipment-sheet"
    const val MINE_CHIP = "library-mine-chip"
    const val CLEAR_FILTERS = "library-clear-filters"
    const val COUNT = "library-count"
    const val CREATE = "library-create"
    const val SETTINGS = "library-settings"
    const val SKELETON_ROW = "library-skeleton-row"
    const val EMPTY = "library-empty"
    const val RECENT_HEADER = "library-header-recent"
    const val ALL_HEADER = "library-header-all"

    /** A row's name text (unmerged tree). */
    const val ROW_NAME = "library-row-name"

    /** A custom row's "Custom" tag (unmerged tree). */
    const val ROW_CUSTOM = "library-row-custom"

    /** The row of [exerciseId] in "All" or in the results. */
    fun row(exerciseId: String) = "library-row-$exerciseId"

    /** The row of [exerciseId] in "Recently used" (the same exercise is also in "All"). */
    fun recentRow(exerciseId: String) = "library-recent-$exerciseId"

    /** The chip of the muscle group whose English label is [label] ("Chest"). */
    fun muscleChip(label: String) = "library-chip-$label"

    /** The sheet's chip of the equipment whose English label is [label] ("Barbell"). */
    fun equipmentOption(label: String) = "library-equipment-option-$label"
}

/**
 * The seams of [LibraryRoute] for tests (pre-flight F19): the model [cache] and [build], which a
 * test holds to set "before the index is ready"; the row [media]; and [onCreate], which gets the
 * name to start from ("" from the top bar, the search from "Create “…”").
 */
internal class LibrarySeams(
    val cache: LibraryModelCache = LibraryModelCache.process,
    val build: suspend (List<ExerciseUi>, Boolean) -> LibraryModel = ::buildOffTheMainThread,
    val media: (ExerciseUi) -> ExerciseMedia = { ExerciseMedia.None },
    val onCreate: (prefill: String) -> Unit = {},
) {
    companion object {
        // The media is Task 8's `exerciseMedia` (frame B, still) once the poses are merged.
        val Default = LibrarySeams()
    }
}

/**
 * The library tab (spec "Biblioteca (aba)"): "Library" with the Settings shortcut ([onOpenSettings])
 * and "Create exercise"; the search field, fixed under the top bar; and, scrolling under it, the
 * filters ([LibraryFilterBar], the equipment in [EquipmentFilterSheet]), the count ("214
 * exercises", announced politely when it changes) and the list. Without a search or a filter the
 * list is "Recently used" (up to 8, [LibraryModel.recents]) and then "All"; with either, only the
 * results, in alphabetical order of the shown name. Until the model is built ([rememberLibraryModel])
 * the list shows skeleton rows, while the search and the filters already take input, which applies
 * when the model arrives. Nothing found shows an empty state with "Create “…”" from the search and
 * "Clear filters".
 *
 * A row opens [onOpenExercise] with its id, only while the exercise is still in [UiState.exercises]
 * (for the moment a new list is being built, the rows can be the previous list's). The search, the
 * filters, the open equipment sheet and the scroll are kept with `rememberSaveable`, so they
 * survive going to the detail and back, and recreation. Errors show in the app's language over the
 * screen ([ScreenErrorToast], plan decision 14), never [UiState.error]. Task 16 adds the custom
 * exercise form to "Create exercise".
 */
@Composable
fun LibraryRoute(
    state: UiState,
    actions: MoosclesActions,
    onOpenExercise: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) = LibraryRoute(state, actions, onOpenExercise, onOpenSettings, modifier, LibrarySeams.Default)

/** [LibraryRoute] with its [seams]. */
@Composable
internal fun LibraryRoute(
    state: UiState,
    actions: MoosclesActions,
    onOpenExercise: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
    seams: LibrarySeams,
) {
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var filters by rememberSaveable(stateSaver = LibraryFilters.Saver) { mutableStateOf(LibraryFilters()) }
    var equipmentOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val equipmentFocus = remember { FocusRequester() }
    var focusReturns by remember { mutableIntStateOf(0) }
    val model = rememberLibraryModel(state.exercises, catalogInPortuguese(), seams.cache, seams.build)
    val currentExercises by rememberUpdatedState(state.exercises)
    val openExercise by rememberUpdatedState(onOpenExercise)
    val open: (String) -> Unit = remember {
        { id -> if (currentExercises.any { it.id == id }) openExercise(id) }
    }

    // A new search or filter starts the list from the top; a recreation keeps the scroll it had.
    var resultsShown by remember { mutableStateOf(false) }
    LaunchedEffect(query.text, filters) {
        if (resultsShown) listState.scrollToItem(0) else resultsShown = true
    }
    LaunchedEffect(focusReturns) {
        if (focusReturns == 0) return@LaunchedEffect
        // Once the chip is back in the layout. It is the list's first item, composed while the
        // sheet covers the screen; were it scrolled away, there is no chip to give the focus to.
        withFrameNanos { }
        runCatching { equipmentFocus.requestFocus() }
    }

    Box(modifier.fillMaxSize().testTag(LibraryScreenTag).background(MTheme.colors.bgBase)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            TopBar(
                title = stringResource(Res.string.library_title),
                actions = {
                    MIconButton(
                        MIcons.Settings,
                        contentDescription = stringResource(Res.string.library_settings),
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag(LibraryTags.SETTINGS),
                    )
                    MIconButton(
                        MIcons.Plus,
                        contentDescription = stringResource(Res.string.library_create),
                        onClick = { seams.onCreate("") },
                        modifier = Modifier.testTag(LibraryTags.CREATE),
                    )
                },
            )
            Box(Modifier.fillMaxWidth().testTag(LibraryTags.SEARCH).padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2)) {
                MSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(Res.string.library_search),
                    onClear = { query = TextFieldValue() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LibraryList(
                model = model,
                query = query.text,
                filters = filters,
                history = state.history,
                listState = listState,
                media = seams.media,
                onFilters = { filters = it },
                onOpenEquipment = { equipmentOpen = true },
                equipmentFocus = equipmentFocus,
                onOpen = open,
                onCreate = seams.onCreate,
                modifier = Modifier.weight(1f),
            )
        }
        ScreenErrorToast(
            state.errorCode,
            actions,
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        )
    }
    if (equipmentOpen) {
        EquipmentFilterSheet(
            selected = filters.equipment,
            onChange = { filters = filters.copy(equipment = it) },
            onDismiss = {
                equipmentOpen = false
                focusReturns++
            },
        )
    }
}

/** The library's scrolling part: the filters, the count and the rows, or what stands in for them. */
@Composable
private fun LibraryList(
    model: LibraryModel?,
    query: String,
    filters: LibraryFilters,
    history: List<WorkoutUi>,
    listState: LazyListState,
    media: (ExerciseUi) -> ExerciseMedia,
    onFilters: (LibraryFilters) -> Unit,
    onOpenEquipment: () -> Unit,
    equipmentFocus: FocusRequester,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searching = query.isNotBlank() || filters.active
    val results = remember(model, query, filters) { model?.results(query, filters).orEmpty() }
    val recents = remember(model, history) { model?.recents(history).orEmpty() }
    val count = if (searching) results.size else model?.exercises?.size ?: 0
    val countText = pluralCount(Res.plurals.library_count, count, count)
    val recentTitle = stringResource(Res.string.library_recent)
    val allTitle = stringResource(Res.string.library_all)
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier.fillMaxWidth().testTag(LibraryTags.LIST),
        state = listState,
        contentPadding = PaddingValues(bottom = bottom + MTheme.space.s6),
    ) {
        item(key = "filters") {
            LibraryFilterBar(filters, onFilters, onOpenEquipment, equipmentFocus, Modifier.padding(top = MTheme.space.s1))
        }
        if (model == null) {
            items(SKELETON_ROWS, key = { "skeleton-$it" }) { LibrarySkeletonRow() }
            return@LazyColumn
        }
        item(key = "count") {
            MText(
                countText,
                MTheme.type.caption,
                color = MTheme.colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LibraryTags.COUNT)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2),
            )
        }
        when {
            !searching && model.exercises.isEmpty() -> item(key = "empty") {
                EmptyState(
                    icon = MIcons.Dumbbell,
                    title = stringResource(Res.string.library_catalog_empty),
                    body = stringResource(Res.string.library_catalog_empty_body),
                    modifier = Modifier.testTag(LibraryTags.EMPTY),
                    primaryAction = stringResource(Res.string.library_create) to { onCreate("") },
                )
            }
            !searching -> {
                if (recents.isNotEmpty()) {
                    item(key = "recent-header") { SectionHeader(recentTitle, Modifier.testTag(LibraryTags.RECENT_HEADER)) }
                    items(recents, key = { "recent-${it.id}" }) { exercise ->
                        LibraryRow(exercise, model.shownName(exercise), media(exercise), LibraryTags.recentRow(exercise.id), { onOpen(exercise.id) })
                    }
                }
                item(key = "all-header") { SectionHeader(allTitle, Modifier.testTag(LibraryTags.ALL_HEADER)) }
                items(model.exercises, key = { "row-${it.id}" }) { exercise ->
                    LibraryRow(exercise, model.shownName(exercise), media(exercise), LibraryTags.row(exercise.id), { onOpen(exercise.id) })
                }
            }
            results.isEmpty() -> item(key = "empty") { NoResults(query, filters, onFilters, onCreate) }
            else -> items(results, key = { "row-${it.id}" }) { exercise ->
                LibraryRow(exercise, model.shownName(exercise), media(exercise), LibraryTags.row(exercise.id), { onOpen(exercise.id) })
            }
        }
    }
}

/**
 * Nothing matches: "No exercises found", with "Create “…”" (the search as the new exercise's name)
 * when there is a search, and "Clear filters" when a filter is on.
 */
@Composable
private fun NoResults(query: String, filters: LibraryFilters, onFilters: (LibraryFilters) -> Unit, onCreate: (String) -> Unit) {
    val name = query.trim()
    val create = if (name.isNotEmpty()) stringResource(Res.string.library_create_named, name) to { onCreate(name) } else null
    val clear = if (filters.active) stringResource(Res.string.library_clear_filters) to { onFilters(LibraryFilters()) } else null
    EmptyState(
        icon = MIcons.SearchX,
        title = stringResource(Res.string.library_empty),
        body = stringResource(Res.string.library_empty_body),
        modifier = Modifier.testTag(LibraryTags.EMPTY),
        primaryAction = create ?: clear,
        secondaryAction = if (create != null) clear else null,
    )
}

/** How many skeleton rows stand in for the list while it is built. */
private const val SKELETON_ROWS = 6
