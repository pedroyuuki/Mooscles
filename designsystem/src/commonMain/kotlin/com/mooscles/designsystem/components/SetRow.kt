package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_action_change_type
import com.mooscles.designsystem.resources.ds_action_complete_set
import com.mooscles.designsystem.resources.ds_action_edit_load
import com.mooscles.designsystem.resources.ds_action_edit_reps
import com.mooscles.designsystem.resources.ds_action_remove_set
import com.mooscles.designsystem.resources.ds_action_uncomplete_set
import com.mooscles.designsystem.resources.ds_load_by_reps_spoken
import com.mooscles.designsystem.resources.ds_pr_label
import com.mooscles.designsystem.resources.ds_pr_spoken
import com.mooscles.designsystem.resources.ds_reps_count
import com.mooscles.designsystem.resources.ds_set_done
import com.mooscles.designsystem.resources.ds_set_not_done
import com.mooscles.designsystem.resources.ds_set_row_a11y
import com.mooscles.designsystem.resources.ds_set_row_a11y_no_previous
import com.mooscles.designsystem.resources.ds_set_row_position
import com.mooscles.designsystem.resources.ds_set_type_drop_short
import com.mooscles.designsystem.resources.ds_set_type_drop_spoken
import com.mooscles.designsystem.resources.ds_set_type_failure_short
import com.mooscles.designsystem.resources.ds_set_type_failure_spoken
import com.mooscles.designsystem.resources.ds_set_type_warmup_short
import com.mooscles.designsystem.resources.ds_set_type_warmup_spoken
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

enum class SetType { Normal, Warmup, Drop, Failure }

enum class SetStatus { Pending, Editing, Completed, CompletedRecord }

/**
 * What a [SetRow] shows. [previous] is the text of the same set last time (null when there is none)
 * and [previousSpoken] how it is read, load and reps ("80 kilograms by 6", built with
 * [spokenLoadByReps]); [loadSpoken] is the load as spoken. The strings are already localized by
 * the caller; [reps] is a count.
 */
data class SetRowState(
    val index: Int,
    val total: Int,
    val previous: String?,
    val previousSpoken: String?,
    val load: String,
    val loadSpoken: String,
    val reps: Int,
    val type: SetType,
    val status: SetStatus,
)

const val SetRowPreviousTag = "set-row-previous"
const val SetRowLoadTag = "set-row-load"
const val SetRowRepsTag = "set-row-reps"
const val SetRowTypeTag = "set-row-type"
const val SetRowCheckTag = "set-row-check"

/** A load and its reps as read aloud: "80 kilograms by 6" ("80 quilos por 6"). [loadSpoken] is already spoken ("80 kilograms"). */
@Composable
fun spokenLoadByReps(loadSpoken: String, reps: Int): String = stringResource(Res.string.ds_load_by_reps_spoken, loadSpoken, reps)

/**
 * From this font scale on, a row is always two lines: the previous result on top; load, reps and
 * check below. It is also two lines whenever the one-line row would not fit its width.
 */
private const val TWO_LINE_FONT_SCALE = 1.5f

/**
 * One set of the active workout: number (and a letter for a warm-up, drop or failure set), the
 * previous result, load, reps and the check, at least 56 dp tall. With a font scale of 1.5 or more,
 * or when the measured one-line row is wider than the space it gets, it is two lines, so the
 * previous result never wraps in the middle of its value. The row is one accessibility node
 * ("Set 4 of 5, warm-up, previous ..., load ..., 6 reps, not completed") with five custom actions;
 * its parts are not separate nodes, except the check, a toggle of its own ("Complete set 4",
 * checked or not) that calls [onCompleteChange] like a tap, and the editable [loadField] and
 * [repsField], which stay their own nodes so a screen reader can reach and edit them. [loadField]
 * and [repsField] replace the default tappable numbers with a [NumericField]. The haptics follow
 * [SetRowState.status]: to `Completed` the plain one, to `CompletedRecord` the record one instead,
 * back to pending the unchecked one. Removing with "undo" is up to the caller (a toast).
 */
@Composable
fun SetRow(
    state: SetRowState,
    onCompleteChange: (Boolean) -> Unit,
    onEditLoad: () -> Unit,
    onEditReps: () -> Unit,
    onChangeType: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    loadField: (@Composable () -> Unit)? = null,
    repsField: (@Composable () -> Unit)? = null,
) {
    val colors = MTheme.colors
    val haptics = MTheme.haptics
    val completed = state.status == SetStatus.Completed || state.status == SetStatus.CompletedRecord
    val record = state.status == SetStatus.CompletedRecord

    var lastStatus by remember { mutableStateOf(state.status) }
    val enteredRecord = record && lastStatus != SetStatus.CompletedRecord
    LaunchedEffect(state.status) {
        val before = lastStatus
        lastStatus = state.status
        val wasDone = before == SetStatus.Completed || before == SetStatus.CompletedRecord
        when {
            state.status == before -> Unit
            state.status == SetStatus.CompletedRecord -> haptics.perform(HapticEvent.PersonalRecord)
            state.status == SetStatus.Completed -> haptics.perform(HapticEvent.SetCompleted)
            wasDone -> haptics.perform(HapticEvent.SetUnchecked)
        }
    }

    val doneText = stringResource(if (completed) Res.string.ds_set_done else Res.string.ds_set_not_done)
    val statusText = if (record) "$doneText, ${stringResource(Res.string.ds_pr_spoken)}" else doneText
    val repsText = pluralCount(Res.plurals.ds_reps_count, state.reps)
    val position = stringResource(Res.string.ds_set_row_position, state.index, state.total)
    val typeSpoken = when (state.type) {
        SetType.Normal -> null
        SetType.Warmup -> stringResource(Res.string.ds_set_type_warmup_spoken)
        SetType.Drop -> stringResource(Res.string.ds_set_type_drop_spoken)
        SetType.Failure -> stringResource(Res.string.ds_set_type_failure_spoken)
    }
    val heading = if (typeSpoken != null) "$position, $typeSpoken" else position
    val description = if (state.previous != null) {
        stringResource(
            Res.string.ds_set_row_a11y,
            heading, state.previousSpoken ?: state.previous, state.loadSpoken, repsText, statusText,
        )
    } else {
        stringResource(Res.string.ds_set_row_a11y_no_previous, heading, state.loadSpoken, repsText, statusText)
    }
    val labelComplete = stringResource(if (completed) Res.string.ds_action_uncomplete_set else Res.string.ds_action_complete_set)
    val labelLoad = stringResource(Res.string.ds_action_edit_load)
    val labelReps = stringResource(Res.string.ds_action_edit_reps)
    val labelType = stringResource(Res.string.ds_action_change_type)
    val labelRemove = stringResource(Res.string.ds_action_remove_set)

    val shape = RoundedCornerShape(MTheme.radius.md)
    val background = when (state.status) {
        SetStatus.Editing -> colors.bgInset
        SetStatus.Completed, SetStatus.CompletedRecord -> colors.accentSubtle
        SetStatus.Pending -> colors.bgSurface
    }
    val letter = typeLetter(state.type)
    val oneLineWidth = oneLineWidthPx(state, letter, record, loadField != null, repsField != null)
    val fontScale = LocalDensity.current.fontScale

    val indexCell: @Composable () -> Unit = {
        IndexCell(state.index, letter, onChangeType)
    }
    val previousCell: @Composable (Modifier) -> Unit = { cellModifier ->
        PreviousCell(state, record, enteredRecord, cellModifier)
    }
    val loadCell: @Composable (Modifier) -> Unit = { cellModifier ->
        Box(cellModifier.testTag(SetRowLoadTag).sizeIn(minWidth = MTheme.sizes.numericFieldWidth, minHeight = MTheme.targets.workout), contentAlignment = Alignment.Center) {
            if (loadField != null) loadField() else NumberCell(state.load, onEditLoad)
        }
    }
    val repsCell: @Composable (Modifier) -> Unit = { cellModifier ->
        Box(cellModifier.testTag(SetRowRepsTag).sizeIn(minWidth = MTheme.sizes.numericFieldWidth, minHeight = MTheme.targets.workout), contentAlignment = Alignment.Center) {
            if (repsField != null) repsField() else NumberCell(state.reps.toString(), onEditReps)
        }
    }
    val check: @Composable () -> Unit = {
        // Its own toggle node, outside the row's merged description: a screen reader reaches it
        // and a double tap goes through onCompleteChange, the same path (and haptics) as a tap.
        SetCheck(completed, onCompleteChange, state.index, modifier = Modifier.testTag(SetRowCheckTag), haptics = false)
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(background, shape)
            .padding(horizontal = MTheme.space.s2)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(labelComplete) { onCompleteChange(!completed); true },
                    CustomAccessibilityAction(labelLoad) { onEditLoad(); true },
                    CustomAccessibilityAction(labelReps) { onEditReps(); true },
                    CustomAccessibilityAction(labelType) { onChangeType(); true },
                    CustomAccessibilityAction(labelRemove) { onRemove(); true },
                )
            },
    ) {
        val fits = !constraints.hasBoundedWidth || oneLineWidth <= constraints.maxWidth
        val twoLines = fontScale >= TWO_LINE_FONT_SCALE || !fits
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MTheme.targets.gap)) {
            if (twoLines) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MTheme.targets.gap), verticalAlignment = Alignment.Top) {
                    indexCell()
                    previousCell(Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MTheme.targets.gap), verticalAlignment = Alignment.Top) {
                    loadCell(Modifier.weight(1f))
                    repsCell(Modifier.weight(1f))
                    check()
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MTheme.targets.gap), verticalAlignment = Alignment.Top) {
                    indexCell()
                    previousCell(Modifier.weight(1f))
                    loadCell(Modifier)
                    repsCell(Modifier)
                    check()
                }
            }
        }
    }
}

/** The letter of a warm-up, drop or failure set; none for a normal one. */
@Composable
private fun typeLetter(type: SetType): String? = when (type) {
    SetType.Normal -> null
    SetType.Warmup -> stringResource(Res.string.ds_set_type_warmup_short)
    SetType.Drop -> stringResource(Res.string.ds_set_type_drop_short)
    SetType.Failure -> stringResource(Res.string.ds_set_type_failure_short)
}

/**
 * The width, in whole pixels inside the row's side padding, of the one-line row with every text on
 * one line: the index cell, the previous result (or its record badge, if wider), the load and reps
 * cells (a given field counts as `numericFieldWidth`), the check and the four gaps between them.
 */
@Composable
private fun oneLineWidthPx(state: SetRowState, letter: String?, record: Boolean, customLoad: Boolean, customReps: Boolean): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val type = MTheme.type
    val space = MTheme.space
    val sizes = MTheme.sizes
    val targets = MTheme.targets
    val prLabel = stringResource(Res.string.ds_pr_label)
    return with(density) {
        fun width(text: String, style: TextStyle) =
            measurer.measure(text, style, softWrap = false, maxLines = 1, layoutDirection = direction, density = density).size.width
        val index = maxOf(
            targets.min.roundToPx(),
            width(state.index.toString(), type.numSetIndex),
            letter?.let { width(it, type.labelMd) + space.s2.roundToPx() * 2 } ?: 0,
        )
        val badge = if (record) {
            sizes.icon16.roundToPx() + space.s1.roundToPx() + width(prLabel, type.labelMd) + space.s2.roundToPx() * 2
        } else {
            0
        }
        val previous = maxOf(width(state.previous ?: EMPTY_VALUE, type.numSm), badge)
        fun number(text: String?) = maxOf(
            sizes.numericFieldWidth.roundToPx(),
            text?.let { width(it, type.numMd) + space.s2.roundToPx() * 2 } ?: 0,
        )
        val load = number(if (customLoad) null else state.load)
        val reps = number(if (customReps) null else state.reps.toString())
        index + previous + load + reps + targets.workout.roundToPx() + targets.gap.roundToPx() * 4
    }
}

/** The set number, with a letter under it for a warm-up, drop or failure set; tapping changes the type. */
@Composable
private fun IndexCell(index: Int, letter: String?, onChangeType: () -> Unit) {
    Box(Modifier.clearAndSetSemantics {}) { Column(
        Modifier
            .sizeIn(minWidth = MTheme.targets.min, minHeight = MTheme.targets.workout)
            .clickable(role = Role.Button, onClick = onChangeType),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MText(index.toString(), MTheme.type.numSetIndex)
        if (letter != null) {
            MText(
                letter,
                MTheme.type.labelMd,
                color = MTheme.colors.accentText,
                modifier = Modifier
                    .testTag(SetRowTypeTag)
                    .background(MTheme.colors.accentSubtle, MTheme.radius.full)
                    .padding(horizontal = MTheme.space.s2),
            )
        }
    } }
}

@Composable
private fun PreviousCell(state: SetRowState, record: Boolean, animateBadge: Boolean, modifier: Modifier) {
    Column(
        modifier.testTag(SetRowPreviousTag).sizeIn(minHeight = MTheme.targets.workout).clearAndSetSemantics {},
        verticalArrangement = Arrangement.Center,
    ) {
        MText(state.previous ?: EMPTY_VALUE, MTheme.type.numSm, color = MTheme.colors.textSecondary)
        if (record) PrBadge(soft = true, animateIn = animateBadge)
    }
}

/** Shown when there is no previous result: an em dash, which Barlow Condensed draws clearly longer than a hyphen (the en dash is barely wider). */
internal const val EMPTY_VALUE = "—"

/** The default load or reps: the number as a 56 dp tall tappable text. */
@Composable
private fun NumberCell(text: String, onClick: () -> Unit) {
    Box(Modifier.clearAndSetSemantics {}) {
        Box(
            Modifier
                .widthIn(min = MTheme.sizes.numericFieldWidth)
                .sizeIn(minHeight = MTheme.targets.workout)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            MText(text, MTheme.type.numMd, modifier = Modifier.padding(horizontal = MTheme.space.s2))
        }
    }
}
