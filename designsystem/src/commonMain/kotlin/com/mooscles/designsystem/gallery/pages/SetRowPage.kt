package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.SetRow
import com.mooscles.designsystem.components.SetRowState
import com.mooscles.designsystem.components.SetStatus
import com.mooscles.designsystem.components.SetType
import com.mooscles.designsystem.components.setRowSwipe
import com.mooscles.designsystem.components.spokenLoadByReps
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_unit_kg_spoken
import com.mooscles.designsystem.resources.gallery_section_set_rows
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

private const val TOTAL = 8
private const val SUGGESTED_ROW = 4

private class SetSample(
    val load: String,
    val reps: Int,
    val type: SetType,
    val status: SetStatus,
    val previousLoad: String?,
    val suggested: Boolean = false,
    val editing: Boolean = false,
)

/** The previous set is spoken with its reps, as the spec reads it: "anterior 80 quilos por 6". */
@Composable
private fun SetSample.state(index: Int, locale: NumberLocale, spoken: (String) -> String): SetRowState = SetRowState(
    index = index,
    total = TOTAL,
    previous = previousLoad?.let { MoosclesNumbers.setText(it, "kg", reps, locale) },
    previousSpoken = previousLoad?.let { spokenLoadByReps(spoken(MoosclesNumbers.localize(it, locale)), reps) },
    load = MoosclesNumbers.localize(load, locale),
    loadSpoken = spoken(MoosclesNumbers.localize(load, locale)),
    reps = reps,
    type = type,
    status = status,
    loadSuggested = suggested,
    repsSuggested = suggested,
    editing = editing,
)

/**
 * A set row in every status and type. The first one can be checked and unchecked, and swiped
 * (right completes, left unchecks); the one with suggestions turns them into saved values when its
 * previous result is tapped.
 */
@Composable
fun SetRowPage() {
    val locale = currentNumberLocale()
    val spokenTemplate = stringResource(Res.string.ds_unit_kg_spoken, "%")
    val spoken: (String) -> String = { spokenTemplate.replace("%", it) }
    var first by remember { mutableStateOf(SetStatus.Pending) }
    var copied by remember { mutableStateOf(false) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.targets.gap),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_set_rows))
        val rows = listOf(
            SetSample("60", 10, SetType.Warmup, first, "55"),
            SetSample("80", 6, SetType.Normal, SetStatus.Pending, "77.5", editing = true),
            SetSample("82.5", 6, SetType.Normal, SetStatus.Completed, "80"),
            SetSample("85", 6, SetType.Normal, SetStatus.CompletedRecord, "80"),
            if (copied) SetSample("80", 8, SetType.Normal, SetStatus.Pending, "80") else SetSample("82.5", 8, SetType.Normal, SetStatus.Pending, "80", suggested = true),
            SetSample("20", 8, SetType.Assisted, SetStatus.Pending, "25"),
            SetSample("70", 12, SetType.Drop, SetStatus.Pending, null),
            SetSample("70", 12, SetType.Failure, SetStatus.Pending, "65"),
        ).mapIndexed { position, sample -> sample.state(position + 1, locale, spoken) }
        for ((position, row) in rows.withIndex()) {
            SetRow(
                state = row,
                onCompleteChange = { if (position == 0) first = if (it) SetStatus.Completed else SetStatus.Pending },
                onEditLoad = {},
                onEditReps = {},
                onChangeType = {},
                onRemove = {},
                modifier = if (position == 0) {
                    Modifier.setRowSwipe(enabled = true, onComplete = { first = SetStatus.Completed }, onRemove = { first = SetStatus.Pending })
                } else {
                    Modifier
                },
                onEditRow = {},
                onCopyPrevious = if (position == SUGGESTED_ROW) ({ copied = true }) else null,
            )
        }
    }
}
