package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_action_change_type
import com.mooscles.designsystem.resources.ds_action_complete_set
import com.mooscles.designsystem.resources.ds_action_edit_load
import com.mooscles.designsystem.resources.ds_action_edit_reps
import com.mooscles.designsystem.resources.ds_action_remove_set
import com.mooscles.designsystem.resources.ds_pr_spoken
import com.mooscles.designsystem.resources.ds_reps_count
import com.mooscles.designsystem.resources.ds_set_done
import com.mooscles.designsystem.resources.ds_set_not_done
import com.mooscles.designsystem.resources.ds_set_row_a11y
import com.mooscles.designsystem.resources.ds_set_row_a11y_no_previous
import com.mooscles.designsystem.resources.ds_set_type_drop_short
import com.mooscles.designsystem.resources.ds_set_type_failure_short
import com.mooscles.designsystem.resources.ds_set_type_warmup_short
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

enum class SetType { Normal, Warmup, Drop, Failure }

enum class SetStatus { Pending, Editing, Completed, CompletedRecord }

/**
 * What a [SetRow] shows. [previous] is the text of the same set last time (null when there is none)
 * and [previousSpoken] how it is read ("80 kilograms by 6"); [loadSpoken] is the load as spoken.
 * The strings are already localized by the caller; [reps] is a count.
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

/** From this font scale on, a row is two lines: the previous result on top; load, reps and check below. */
private const val TWO_LINE_FONT_SCALE = 1.5f

/**
 * One set of the active workout: number (and a letter for a warm-up, drop or failure set), the
 * previous result, load, reps and the check, at least 56 dp tall. With a font scale of 1.5 or more
 * it is two lines. The whole row is one accessibility node ("Set 4 of 5, previous ..., load ...,
 * 6 reps, not completed") with five custom actions; its parts are not separate nodes. [loadField]
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
    val description = if (state.previous != null) {
        stringResource(
            Res.string.ds_set_row_a11y,
            state.index, state.total, state.previousSpoken ?: state.previous, state.loadSpoken, repsText, statusText,
        )
    } else {
        stringResource(Res.string.ds_set_row_a11y_no_previous, state.index, state.total, state.loadSpoken, repsText, statusText)
    }
    val labelComplete = stringResource(Res.string.ds_action_complete_set)
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
    val twoLines = LocalDensity.current.fontScale >= TWO_LINE_FONT_SCALE

    val indexCell: @Composable () -> Unit = {
        IndexCell(state, onChangeType)
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
        SetCheck(completed, onCompleteChange, state.index, haptics = false)
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(background, shape)
            .padding(horizontal = MTheme.space.s2)
            .clearAndSetSemantics {
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

/** The set number, with a letter under it for a warm-up, drop or failure set; tapping changes the type. */
@Composable
private fun IndexCell(state: SetRowState, onChangeType: () -> Unit) {
    val letter = when (state.type) {
        SetType.Normal -> null
        SetType.Warmup -> stringResource(Res.string.ds_set_type_warmup_short)
        SetType.Drop -> stringResource(Res.string.ds_set_type_drop_short)
        SetType.Failure -> stringResource(Res.string.ds_set_type_failure_short)
    }
    Column(
        Modifier
            .sizeIn(minWidth = MTheme.targets.min, minHeight = MTheme.targets.workout)
            .clickable(role = Role.Button, onClick = onChangeType),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MText(state.index.toString(), MTheme.type.numSetIndex)
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
    }
}

@Composable
private fun PreviousCell(state: SetRowState, record: Boolean, animateBadge: Boolean, modifier: Modifier) {
    Column(
        modifier.testTag(SetRowPreviousTag).sizeIn(minHeight = MTheme.targets.workout),
        verticalArrangement = Arrangement.Center,
    ) {
        MText(state.previous ?: DASH, MTheme.type.numSm, color = MTheme.colors.textSecondary)
        if (record) PrBadge(soft = true, animateIn = animateBadge)
    }
}

private const val DASH = "–"

/** The default load or reps: the number as a 56 dp tall tappable text. */
@Composable
private fun NumberCell(text: String, onClick: () -> Unit) {
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
