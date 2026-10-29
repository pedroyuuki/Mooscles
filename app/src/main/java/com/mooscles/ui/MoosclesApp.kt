package com.mooscles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import com.mooscles.designsystem.licenses.LicensesScreen
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.shell.AppShellBottom
import com.mooscles.screens.shell.ShellTabs
import com.mooscles.screens.summary.WorkoutSummaryScreen
import com.mooscles.screens.workout.ActiveWorkoutRoute
import com.mooscles.screens.workout.ScreenErrorToast

/** The full-screen active workout (spec D1): the only route without the shell. */
internal const val WORKOUT_ROUTE = "workout"
private const val DETAIL_ROUTE = "workoutDetail/{id}?summary={summary}"
private const val SUMMARY_ARG = "summary"

private fun detailRoute(id: String, summary: Boolean) = "workoutDetail/$id?summary=$summary"

/**
 * The app: the old screens and, from `:screens`, the active workout ([ActiveWorkoutRoute]), the
 * end-of-workout summary ([WorkoutSummaryScreen]) and the shell's bottom ([AppShellBottom]).
 *
 * Every route but [WORKOUT_ROUTE] has the shell: the workout bar while a workout is active and the
 * four tabs. The selected tab is the root of the stack in view, kept across recreation; tapping it
 * again goes back to that root, also from the gallery and the licenses (#20). The workout is
 * opened by a start (Train, a routine), the workout bar, or a new [workoutRequest] (the rest
 * notification), each new value once. Minimizing it, or the system Back, returns to where it was
 * opened. Finishing replaces it with `workoutDetail/{id}?summary=true`.
 *
 * [timerState] is the ticking rest timer, read only by the workout bar and the workout's timer;
 * without it they read [UiState.timer].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoosclesApp(state: UiState, actions: MoosclesActions, timerState: State<TimerUi>? = null, workoutRequest: Long = 0L) {
    MoosclesTheme(state.preferences.theme) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: ShellTabs.TRAIN
        val summary = route == DETAIL_ROUTE && entry?.arguments?.getBoolean(SUMMARY_ARG) == true
        val fallbackTimer = rememberUpdatedState(state.timer)
        val timer = timerState ?: fallbackTimer

        // The root of the stack in view: set by a tab, and by reaching a tab's route any other way
        // (shown at once, in the same frame; kept for the routes above it).
        var savedTab by rememberSaveable { mutableStateOf(ShellTabs.TRAIN) }
        val selectedTab = if (route in ShellTabs.keys) route else savedTab
        SideEffect { if (route in ShellTabs.keys) savedTab = route }
        fun selectTab(tab: String) {
            // A saved tab stack never keeps the workout: restored after the workout ended, it
            // would show "No workout in progress". Leaving drops what was opened from it.
            nav.popBackStack(WORKOUT_ROUTE, inclusive = true)
            // Train is the root itself, never a restored tail that happens to have been saved under its ID.
            if (tab == ShellTabs.TRAIN) nav.clearBackStack(ShellTabs.TRAIN)
            nav.navigate(tab) {
                popUpTo(nav.graph.startDestinationId) { saveState = tab != ShellTabs.TRAIN }
                launchSingleTop = true
                restoreState = tab != ShellTabs.TRAIN
            }
            savedTab = tab
        }
        // Back to the tab's own root, dropping what was opened on it (the gallery and licenses too).
        fun reselectTab(tab: String) {
            if (!nav.popBackStack(tab, inclusive = false)) selectTab(tab)
        }
        // Back to the workout already in the stack (a screen opened from it), never a second one.
        fun openWorkout() {
            if (!nav.popBackStack(WORKOUT_ROUTE, inclusive = false)) nav.navigate(WORKOUT_ROUTE) { launchSingleTop = true }
        }

        // Decision 17: the routine of the last start gives the skeleton one card per exercise.
        var startedRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
        val hosted = remember(actions) {
            object : MoosclesActions by actions {
                override fun startWorkout(routineId: String?) {
                    startedRoutineId = routineId
                    actions.startWorkout(routineId)
                }
            }
        }
        val skeletonCards = startedRoutineId?.let { id -> state.routines.find { it.id == id } }
            ?.exercises?.size?.coerceAtLeast(1) ?: 1

        var handledDiscard by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(state.discardNavigationRequest, state.loading) {
            if (!state.loading && state.discardNavigationRequest == 0L) {
                // A new process starts a new ViewModel event sequence, while
                // Android may restore this composition's saved navigation state.
                handledDiscard = 0L
            } else if (!state.loading && state.discardNavigationRequest != handledDiscard) {
                // A saved tab tail can otherwise restore the discarded workout route.
                (ShellTabs.keys + WORKOUT_ROUTE).forEach { nav.clearBackStack(it) }
                nav.navigate(ShellTabs.TRAIN) {
                    popUpTo(nav.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                    restoreState = false
                }
                savedTab = ShellTabs.TRAIN
                handledDiscard = state.discardNavigationRequest
            }
        }
        var handledUpdateRequest by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(state.updateSettingsRequest) {
            if (state.updateSettingsRequest == 0L) {
                // A new process starts a new ViewModel request sequence (see handledDiscard).
                handledUpdateRequest = 0L
            } else if (state.updateSettingsRequest != handledUpdateRequest) {
                // Lands on the updates section, also when Settings is already open: that entry is
                // replaced, since a single-top navigation would keep its old section argument.
                // A request already handled is not repeated when the composition is recreated.
                // The effect can run before the NavHost sets its graph: wait for the first entry.
                nav.currentBackStackEntryFlow.first()
                nav.navigate(SettingsRoutes.settings(SettingsRoutes.UPDATES)) {
                    popUpTo(SettingsRoutes.SETTINGS) { inclusive = true }
                    launchSingleTop = true
                }
                handledUpdateRequest = state.updateSettingsRequest
            }
        }
        var handledWorkoutRequest by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(workoutRequest) {
            if (workoutRequest == 0L) {
                handledWorkoutRequest = 0L
            } else if (workoutRequest != handledWorkoutRequest) {
                // Opened once per request, as the update request above: never again on recreation.
                nav.currentBackStackEntryFlow.first()
                openWorkout()
                handledWorkoutRequest = workoutRequest
            }
        }

        // Decision 19: the new screens show their own localized error and clear it; the global
        // snackbar waits until the workout or the summary is left.
        val snack = remember { SnackbarHostState() }
        val newScreen = route == WORKOUT_ROUTE || summary
        LaunchedEffect(state.error, newScreen) {
            if (!newScreen) state.error?.let { snack.showSnackbar(it); actions.clearError() }
        }
        Scaffold(
            containerColor = if (newScreen) MTheme.colors.bgBase else MaterialTheme.colorScheme.background,
            topBar = {
                // Settings has the design system's top bar; the gallery, the licenses and the new
                // screens bring their own.
                if (route == SettingsRoutes.SETTINGS) SettingsTopBar(onBack = { nav.popBackStack() })
                else if (!newScreen && route != DeveloperGallery.ROUTE && route != SettingsRoutes.LICENSES) TopAppBar(title = { Text("mooscles", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
                    navigationIcon = { if (route !in ShellTabs.keys) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                    actions = { IconButton(onClick = { nav.navigate("settings") { launchSingleTop = true } }) { Icon(Icons.Outlined.Tune, "Settings") } })
            },
            bottomBar = {
                if (route != WORKOUT_ROUTE) ShellBottom(state, timer, selectedTab, ::selectTab, ::reselectTab, ::openWorkout, hosted)
            }, snackbarHost = { SnackbarHost(snack) },
        ) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                // Keep the graph installed even while loading, including restores.
                NavHost(nav, startDestination = ShellTabs.TRAIN, modifier = Modifier.fillMaxSize()) {
                    composable(ShellTabs.TRAIN) { RoutinesScreen(state, hosted, onEdit = { nav.navigate("routine/$it") }, onWorkout = ::openWorkout) }
                    composable("routine/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { page ->
                        val routine = state.routines.find { it.id == page.arguments?.getString("id") }
                        if (routine != null) RoutineEditor(routine, state, hosted, onWorkout = ::openWorkout)
                        else MissingScreen("Routine unavailable")
                    }
                    composable(WORKOUT_ROUTE) { WorkoutRoute(state, timer, hosted, nav, skeletonCards, onChooseRoutine = { selectTab(ShellTabs.TRAIN) }) }
                    composable(ShellTabs.LIBRARY) { LibraryScreen(state, hosted, onHistory = { nav.navigate("exercise/$it") }) }
                    composable(ShellTabs.HISTORY) { HistoryScreen(state, hosted, onDetail = { nav.navigate(detailRoute(it, summary = false)) }) }
                    composable(DETAIL_ROUTE, arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument(SUMMARY_ARG) { type = NavType.BoolType; defaultValue = false })) { page ->
                        val workout = state.history.find { it.id == page.arguments?.getString("id") }
                        val asSummary = page.arguments?.getBoolean(SUMMARY_ARG) ?: false
                        when {
                            workout == null -> MissingScreen("Workout unavailable")
                            asSummary -> SummaryRoute(workout, state, hosted, onDone = { selectTab(ShellTabs.TRAIN) },
                                onDetails = { nav.navigate(detailRoute(workout.id, summary = false)) })
                            else -> WorkoutDetailScreen(workout, state, hosted, summary = false, onDeleted = { nav.popBackStack() }, onExerciseHistory = { nav.navigate("exercise/$it") })
                        }
                    }
                    composable("exercise/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { page ->
                        val exercise = state.exercises.find { it.id == page.arguments?.getString("id") }
                        if (exercise != null) ExerciseHistoryScreen(exercise, state, hosted, onWorkout = { nav.navigate(detailRoute(it, summary = false)) })
                        else MissingScreen("Exercise unavailable")
                    }
                    composable(ShellTabs.RECORDS) { RecordsScreen(state, onExercise = { nav.navigate("exercise/$it") }) }
                    composable(SettingsRoutes.SETTINGS, arguments = listOf(navArgument(SettingsRoutes.SECTION_ARG) { type = NavType.StringType; defaultValue = "" })) { page ->
                        SettingsScreen(state, hosted, section = page.arguments?.getString(SettingsRoutes.SECTION_ARG).orEmpty(),
                            onOpenLicenses = { nav.navigate(SettingsRoutes.LICENSES) },
                            onOpenGallery = { nav.navigate(DeveloperGallery.ROUTE) })
                    }
                    composable(SettingsRoutes.LICENSES) { LicensesScreen(onBack = { nav.popBackStack() }) }
                    composable(DeveloperGallery.ROUTE) { DeveloperGalleryRoute(nav) }
                }
                // The workout route shows its own placeholders while loading (decision 17).
                if (state.loading && route != WORKOUT_ROUTE) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

/**
 * The shell under every route but the workout. The tab bar does not pad itself for the system
 * bars, so the room they take is added here in the bar's own color: under it for the navigation
 * bar, and at the sides for a navigation bar or a cutout there (landscape). The keyboard is then
 * counted without the bottom room, which keeps the old `WorkoutBottomBar` geometry: the tabs stay
 * put and the workout bar rides on the keyboard. [systemBars] and [ime] are the real insets in the
 * app; tests inject their own.
 */
@Composable
internal fun ShellBottom(
    state: UiState,
    timer: State<TimerUi>,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    onReselectTab: (String) -> Unit,
    onOpenWorkout: () -> Unit,
    actions: MoosclesActions,
    systemBars: WindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
    ime: WindowInsets = WindowInsets.ime,
) {
    val raised = MTheme.colors.bgRaised
    val keyboard = remember(ime, systemBars) { ime.exclude(systemBars.only(WindowInsetsSides.Bottom)) }
    Column(
        Modifier
            .fillMaxWidth()
            // Only the side strips: behind the bar's rounded top the screen shows through.
            .drawBehind {
                val left = systemBars.getLeft(this, layoutDirection).toFloat()
                val right = systemBars.getRight(this, layoutDirection).toFloat()
                if (left > 0f) drawRect(raised, size = Size(left, size.height))
                if (right > 0f) drawRect(raised, topLeft = Offset(size.width - right, 0f), size = Size(right, size.height))
            }
            .windowInsetsPadding(systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        AppShellBottom(state, timer, selectedTab, onSelectTab, onReselectTab, onOpenWorkout, actions, imeInsets = keyboard)
        Spacer(Modifier.fillMaxWidth().background(raised).windowInsetsBottomHeight(systemBars).testTag(SHELL_SYSTEM_BAR_TAG))
    }
}

/** The room under the tab bar for the system navigation bar. */
internal const val SHELL_SYSTEM_BAR_TAG = "shell-system-bar"

/**
 * The `workout` route: minimizing, and the system Back, return to where the workout was opened
 * (Train when nothing is under it); the summary replaces the workout in the stack, which the
 * route relies on to open it once.
 */
@Composable
private fun WorkoutRoute(
    state: UiState,
    timer: State<TimerUi>,
    actions: MoosclesActions,
    nav: NavHostController,
    skeletonCards: Int,
    onChooseRoutine: () -> Unit,
) {
    val minimize = { if (!nav.popBackStack()) nav.navigate(ShellTabs.TRAIN) }
    BackHandler(onBack = minimize)
    ActiveWorkoutRoute(
        state = state,
        timer = timer,
        actions = actions,
        onMinimize = minimize,
        onOpenSummary = { id -> nav.navigate(detailRoute(id, summary = true)) { popUpTo(WORKOUT_ROUTE) { inclusive = true } } },
        onExerciseHistory = { nav.navigate("exercise/$it") },
        onChooseRoutine = onChooseRoutine,
        skeletonCards = skeletonCards,
    )
}

/**
 * The summary of a finished [workout]; Share opens the card preview over it. An error shows in the
 * app's language and is handed back, as in the workout (decision 19).
 */
@Composable
private fun SummaryRoute(workout: WorkoutUi, state: UiState, actions: MoosclesActions, onDone: () -> Unit, onDetails: () -> Unit) {
    var share by rememberSaveable(workout.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        WorkoutSummaryScreen(workout, state.history, state.preferences.pounds, onDone = onDone, onShare = { share = true }, onDetails = onDetails)
        ScreenErrorToast(state.errorCode, actions, Modifier.fillMaxSize())
    }
    if (share) WorkoutSharePreview(workout.id, state.preferences.pounds, onDismiss = { share = false })
}

@Composable
private fun MissingScreen(title: String) = EmptyState(Icons.Outlined.SearchOff, title, "This item may have been deleted. Return to your library to continue.")
