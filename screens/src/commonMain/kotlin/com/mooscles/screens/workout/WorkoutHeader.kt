package com.mooscles.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_finish
import com.mooscles.screens.resources.workout_header_sets
import com.mooscles.screens.resources.workout_menu_discard
import com.mooscles.screens.resources.workout_menu_rename
import com.mooscles.screens.resources.workout_minimize
import com.mooscles.screens.resources.workout_more_options
import org.jetbrains.compose.resources.stringResource

/** The sets done (as drawn) of all the sets of the workout. */
@Immutable
internal data class SetProgress(val done: Int, val total: Int)

/** The test tags of the header and its parts. */
internal object WorkoutHeaderTags {
    const val HEADER = "workout-header"
    const val MINIMIZE = "workout-minimize"
    const val TITLE = "workout-title"
    const val SETS = "workout-header-sets"
    const val FINISH = "workout-header-finish"
    const val MORE = "workout-more"
    const val MENU_RENAME = "workout-menu-rename"
    const val MENU_DISCARD = "workout-menu-discard"
}

/** From this font scale the header is laid out in two rows, as the in-progress bar is. */
private const val TWO_ROW_FONT_SCALE = 1.5f

/** In one row, the name keeps at least this share (1/3) of the width. */
private const val TITLE_SHARE = 3

/**
 * The fixed header of the active workout (spec "Cabeçalho"), at least `barMin` (64 dp) tall:
 * "Minimize workout" (`ChevronDown`, 48 dp), the workout's [name] (`title.sm`, up to two lines),
 * under it the [WorkoutClock] and "· 9 of 15 sets" (`caption`, from [progress]), then "Finish"
 * (tonal S, a 48 dp target) and "More options" (`EllipsisVertical`), which opens a menu with
 * "Rename workout" and "Discard workout".
 *
 * In one row while it fits: the name keeps a third of the width and the clock its whole width.
 * Otherwise, and from a font scale of 1.5, the name shares the first row with the two icon
 * buttons, and the clock, the sets and "Finish" go on a second row (the sets under the clock and
 * "Finish" on a third when even that is too narrow). The layout is decided in the layout phase,
 * so the clock's tick never recomposes the header. [progress] is read here only, so a check
 * recomposes the header, not the screen. "Finish" shows its loading state while [finishing] and
 * is off without [finishEnabled] (a discard running); the menu is off without [menuEnabled].
 */
@Composable
internal fun WorkoutHeader(
    name: String,
    startedAt: Long,
    nowMillis: () -> Long,
    progress: State<SetProgress>,
    finishing: Boolean,
    finishEnabled: Boolean,
    menuEnabled: Boolean,
    onMinimize: () -> Unit,
    onFinish: () -> Unit,
    onRename: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MTheme.colors
    val space = MTheme.space
    val minHeight = MTheme.sizes.barMin
    val large = LocalDensity.current.fontScale >= TWO_ROW_FONT_SCALE
    var menu by rememberSaveable { mutableStateOf(false) }
    val sets by progress

    Layout(
        modifier = modifier
            .testTag(WorkoutHeaderTags.HEADER)
            .background(colors.bgBase)
            .padding(horizontal = space.s2)
            .semantics { isTraversalGroup = true },
        content = {
            MIconButton(
                MIcons.ChevronDown,
                stringResource(Res.string.workout_minimize),
                onClick = onMinimize,
                modifier = Modifier.testTag(WorkoutHeaderTags.MINIMIZE),
            )
            MText(
                name,
                MTheme.type.titleSm,
                modifier = Modifier.testTag(WorkoutHeaderTags.TITLE).semantics { heading() },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            WorkoutClock(startedAt, nowMillis)
            MText(
                pluralCount(Res.plurals.workout_header_sets, sets.total, sets.done, sets.total),
                MTheme.type.caption,
                modifier = Modifier.testTag(WorkoutHeaderTags.SETS),
                color = colors.textSecondary,
            )
            MButton(
                stringResource(Res.string.workout_finish),
                onClick = onFinish,
                modifier = Modifier.testTag(WorkoutHeaderTags.FINISH),
                variant = MButtonVariant.Tonal,
                size = MButtonSize.S,
                enabled = finishEnabled,
                loading = finishing,
            )
            MIconButton(
                MIcons.EllipsisVertical,
                stringResource(Res.string.workout_more_options),
                onClick = { menu = true },
                modifier = Modifier.testTag(WorkoutHeaderTags.MORE),
                enabled = menuEnabled,
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = space.s2.roundToPx()
        val loose = Constraints(maxWidth = width)
        val minimize = measurables[0].measure(loose)
        val clock = measurables[2].measure(loose)
        val finish = measurables[4].measure(loose)
        val more = measurables[5].measure(loose)
        val middle = width - minimize.width - finish.width - more.width - gap * 3
        val oneRow = !large && middle >= width / TITLE_SHARE && middle >= clock.width

        // The clock and the sets after it on one line when they fit in [lineWidth], else under it.
        fun clockLine(lineWidth: Int): ClockLine {
            val sets = measurables[3].measure(Constraints(maxWidth = lineWidth.coerceAtLeast(0)))
            val sameLine = clock.width + gap + sets.width <= lineWidth
            return ClockLine(clock, sets, sameLine, gap)
        }

        val placements = mutableListOf<Triple<Placeable, Int, Int>>()
        val height: Int
        if (oneRow) {
            val title = measurables[1].measure(Constraints(maxWidth = middle))
            val line = clockLine(middle)
            val middleHeight = title.height + line.height
            height = maxOf(minHeight.roundToPx(), minimize.height, finish.height, more.height, middleHeight)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            val x = minimize.width + gap
            val top = (height - middleHeight) / 2
            placements += Triple(minimize, 0, (height - minimize.height) / 2)
            placements += Triple(title, x, top)
            line.place(x, top + title.height, placements)
            placements += Triple(finish, x + middle + gap, (height - finish.height) / 2)
            placements += Triple(more, width - more.width, (height - more.height) / 2)
        } else {
            val titleWidth = (width - minimize.width - more.width - gap * 2).coerceAtLeast(0)
            val title = measurables[1].measure(Constraints(maxWidth = titleWidth))
            val first = maxOf(minimize.height, title.height, more.height)
            placements += Triple(minimize, 0, (first - minimize.height) / 2)
            placements += Triple(title, minimize.width + gap, (first - title.height) / 2)
            placements += Triple(more, width - more.width, (first - more.height) / 2)
            // The second row starts under the name when it fits there, at the edge otherwise.
            val under = minimize.width + gap
            val start = if (width - under - gap - finish.width >= clock.width) under else 0
            val beside = width - start - gap - finish.width
            if (beside >= clock.width) {
                val line = clockLine(beside)
                val second = maxOf(line.height, finish.height)
                line.place(start, first + (second - line.height) / 2, placements)
                placements += Triple(finish, width - finish.width, first + (second - finish.height) / 2)
                height = (first + second).coerceIn(constraints.minHeight, constraints.maxHeight)
            } else {
                val line = clockLine(width)
                line.place(0, first, placements)
                placements += Triple(finish, width - finish.width, first + line.height)
                height = (first + line.height + finish.height).coerceIn(constraints.minHeight, constraints.maxHeight)
            }
        }
        layout(width, maxOf(height, minHeight.roundToPx())) {
            placements.forEach { (placeable, x, y) -> placeable.placeRelative(x, y) }
        }
    }

    if (menu) {
        WorkoutMenuSheet(
            onRename = {
                menu = false
                onRename()
            },
            onDiscard = {
                menu = false
                onDiscard()
            },
            onDismiss = { menu = false },
        )
    }
}

/** The clock and the sets text, on one line (baselines aligned) or the sets under the clock. */
private class ClockLine(val clock: Placeable, val sets: Placeable, val sameLine: Boolean, val gap: Int) {
    private val clockBaseline = clock[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: clock.height
    private val setsBaseline = sets[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: sets.height
    private val setsTop = if (sameLine) (clockBaseline - setsBaseline).coerceAtLeast(0) else clock.height
    private val clockTop = if (sameLine) (setsBaseline - clockBaseline).coerceAtLeast(0) else 0
    val height: Int = maxOf(clockTop + clock.height, setsTop + sets.height)

    fun place(x: Int, y: Int, into: MutableList<Triple<Placeable, Int, Int>>) {
        into += Triple(clock, x, y + clockTop)
        into += Triple(sets, if (sameLine) x + clock.width + gap else x, y + setsTop)
    }
}

/** The header's menu: "Rename workout" and "Discard workout" (destructive). */
@Composable
private fun WorkoutMenuSheet(onRename: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    MBottomSheet(onDismiss = onDismiss) {
        MenuItem(MIcons.Pencil, stringResource(Res.string.workout_menu_rename), WorkoutHeaderTags.MENU_RENAME, onClick = onRename)
        MenuItem(
            MIcons.Trash2, stringResource(Res.string.workout_menu_discard), WorkoutHeaderTags.MENU_DISCARD,
            destructive = true, onClick = onDiscard,
        )
        Spacer(Modifier.height(MTheme.space.s4))
    }
}
