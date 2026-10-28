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
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.components.SetRow
import com.mooscles.designsystem.components.SetRowState
import com.mooscles.designsystem.components.SetStatus
import com.mooscles.designsystem.components.SetType
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_unit_kg_spoken
import com.mooscles.designsystem.resources.gallery_section_set_rows
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

private const val TOTAL = 6

private fun sample(index: Int, load: String, reps: Int, type: SetType, status: SetStatus, previousLoad: String?, locale: NumberLocale, kg: String, spoken: (String) -> String) =
    SetRowState(
        index = index,
        total = TOTAL,
        previous = previousLoad?.let { MoosclesNumbers.setText(it, kg, reps, locale) },
        previousSpoken = previousLoad?.let { spoken(MoosclesNumbers.localize(it, locale)) },
        load = MoosclesNumbers.localize(load, locale),
        loadSpoken = spoken(MoosclesNumbers.localize(load, locale)),
        reps = reps,
        type = type,
        status = status,
    )

/** A set row in every status and type; the first one can be checked and unchecked. */
@Composable
fun SetRowPage() {
    val locale = currentNumberLocale()
    val kg = "kg"
    val spokenTemplate = stringResource(Res.string.ds_unit_kg_spoken, "%")
    val spoken: (String) -> String = { spokenTemplate.replace("%", it) }
    var first by remember { mutableStateOf(SetStatus.Pending) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.targets.gap),
    ) {
        SectionHeader(stringResource(Res.string.gallery_section_set_rows))
        val rows = listOf(
            sample(1, "60", 10, SetType.Warmup, first, "55", locale, kg, spoken),
            sample(2, "80", 6, SetType.Normal, SetStatus.Editing, "77.5", locale, kg, spoken),
            sample(3, "82.5", 6, SetType.Normal, SetStatus.Completed, "80", locale, kg, spoken),
            sample(4, "85", 6, SetType.Normal, SetStatus.CompletedRecord, "80", locale, kg, spoken),
            sample(5, "70", 12, SetType.Drop, SetStatus.Pending, null, locale, kg, spoken),
            sample(6, "70", 12, SetType.Failure, SetStatus.Pending, "65", locale, kg, spoken),
        )
        for ((position, row) in rows.withIndex()) {
            SetRow(
                state = row,
                onCompleteChange = { if (position == 0) first = if (it) SetStatus.Completed else SetStatus.Pending },
                onEditLoad = {},
                onEditReps = {},
                onChangeType = {},
                onRemove = {},
            )
        }
    }
}
