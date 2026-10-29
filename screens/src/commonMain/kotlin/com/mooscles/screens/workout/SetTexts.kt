package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import com.mooscles.designsystem.components.spokenLoadByReps
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_unit_kg_spoken
import com.mooscles.designsystem.resources.ds_unit_lb_spoken
import com.mooscles.domain.WeightUnits
import com.mooscles.ui.PreviousSetUi
import org.jetbrains.compose.resources.stringResource

// The texts of a set: loads in the chosen unit, with the language's decimal separator. The value
// stays in exact kg everywhere else; these only show it.

/** The unit symbol, the same in every language. */
internal fun unitSymbol(pounds: Boolean): String = if (pounds) "lb" else "kg"

/** Shown for a set the previous session has no pair for. */
internal const val NO_PREVIOUS = "—"

/** [weightKg] as shown in a field, in the unit of [pounds], without the unit: "82,5", "80". */
@Composable
internal fun loadText(weightKg: Double, pounds: Boolean): String =
    MoosclesNumbers.localize(MoosclesNumbers.plain(WeightUnits.fromKg(weightKg, pounds)), currentNumberLocale())

/** [weightKg] as read aloud: "82,5 quilos", "80 pounds". */
@Composable
internal fun loadSpoken(weightKg: Double, pounds: Boolean): String =
    stringResource(if (pounds) Res.string.ds_unit_lb_spoken else Res.string.ds_unit_kg_spoken, loadText(weightKg, pounds))

/** The previous session's set as the "previous" column shows it: "80 kg × 6", or [NO_PREVIOUS] without a pair. */
@Composable
internal fun previousText(previous: PreviousSetUi?, pounds: Boolean): String {
    if (previous == null) return NO_PREVIOUS
    val plain = MoosclesNumbers.plain(WeightUnits.fromKg(previous.weightKg, pounds))
    return MoosclesNumbers.setText(plain, unitSymbol(pounds), previous.reps, currentNumberLocale())
}

/** The previous session's set as read aloud: "80 quilos por 6". */
@Composable
internal fun previousSpoken(previous: PreviousSetUi, pounds: Boolean): String =
    spokenLoadByReps(loadSpoken(previous.weightKg, pounds), previous.reps)
