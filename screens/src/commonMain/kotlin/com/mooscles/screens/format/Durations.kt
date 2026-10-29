package com.mooscles.screens.format

import androidx.compose.runtime.Composable
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.date_day_fri
import com.mooscles.screens.resources.date_day_mon
import com.mooscles.screens.resources.date_day_sat
import com.mooscles.screens.resources.date_day_sun
import com.mooscles.screens.resources.date_day_thu
import com.mooscles.screens.resources.date_day_tue
import com.mooscles.screens.resources.date_day_wed
import com.mooscles.screens.resources.date_month_apr
import com.mooscles.screens.resources.date_month_aug
import com.mooscles.screens.resources.date_month_dec
import com.mooscles.screens.resources.date_month_feb
import com.mooscles.screens.resources.date_month_jan
import com.mooscles.screens.resources.date_month_jul
import com.mooscles.screens.resources.date_month_jun
import com.mooscles.screens.resources.date_month_mar
import com.mooscles.screens.resources.date_month_may
import com.mooscles.screens.resources.date_month_nov
import com.mooscles.screens.resources.date_month_oct
import com.mooscles.screens.resources.date_month_sep
import com.mooscles.screens.resources.date_range_format
import com.mooscles.screens.resources.date_short_format
import com.mooscles.screens.resources.duration_hours_minutes
import com.mooscles.screens.resources.duration_minutes
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Elapsed time as a clock, the same in every language. */
object Durations {
    /** `42:18` under an hour (the design system's m:ss), `1:02:18` from an hour on. */
    fun clock(elapsedSeconds: Long): String {
        val seconds = elapsedSeconds.coerceAtLeast(0L)
        if (seconds < 3600L) return MoosclesNumbers.clock(seconds.toInt())
        val hours = seconds / 3600L
        val rest = (seconds % 3600L).toInt()
        val minutes = rest / 60
        val secs = rest % 60
        return "$hours:${two(minutes)}:${two(secs)}"
    }
}

private fun two(value: Int) = if (value < 10) "0$value" else "$value"

private val monthNames: List<StringResource> = listOf(
    Res.string.date_month_jan, Res.string.date_month_feb, Res.string.date_month_mar,
    Res.string.date_month_apr, Res.string.date_month_may, Res.string.date_month_jun,
    Res.string.date_month_jul, Res.string.date_month_aug, Res.string.date_month_sep,
    Res.string.date_month_oct, Res.string.date_month_nov, Res.string.date_month_dec,
)

private fun weekdayName(day: DayOfWeek): StringResource = when (day) {
    DayOfWeek.MONDAY -> Res.string.date_day_mon
    DayOfWeek.TUESDAY -> Res.string.date_day_tue
    DayOfWeek.WEDNESDAY -> Res.string.date_day_wed
    DayOfWeek.THURSDAY -> Res.string.date_day_thu
    DayOfWeek.FRIDAY -> Res.string.date_day_fri
    DayOfWeek.SATURDAY -> Res.string.date_day_sat
    DayOfWeek.SUNDAY -> Res.string.date_day_sun
}

/** `21 out` / `Oct 21`. */
@Composable
fun shortDate(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = ScreenDates.localDateTime(epochMillis, zone).date
    return stringResource(Res.string.date_short_format, date.day, stringResource(monthNames[date.month.number - 1]))
}

/** `ter., 29 out · 18:02–18:44` / `Tue, Oct 29 · 18:02–18:44` (24 h clock in both languages). */
@Composable
fun summaryDateRange(startMillis: Long, endMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val start = ScreenDates.localDateTime(startMillis, zone)
    val end = ScreenDates.localDateTime(endMillis, zone)
    return stringResource(
        Res.string.date_range_format,
        stringResource(weekdayName(start.dayOfWeek)),
        shortDate(startMillis, zone),
        "${two(start.hour)}:${two(start.minute)}",
        "${two(end.hour)}:${two(end.minute)}",
    )
}

/** `42 min` under an hour, `1 h 12 min` from an hour on (whole minutes). */
@Composable
fun durationText(seconds: Long): String {
    val minutes = (seconds.coerceAtLeast(0L) / 60L).toInt()
    return if (minutes < 60) stringResource(Res.string.duration_minutes, minutes)
    else stringResource(Res.string.duration_hours_minutes, minutes / 60, minutes % 60)
}
