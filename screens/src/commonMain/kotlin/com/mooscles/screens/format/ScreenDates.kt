package com.mooscles.screens.format

import com.mooscles.ui.WorkoutUi
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Calendar math in common code: kotlinx-datetime only for the time zone rules, never for text. */
object ScreenDates {
    /** The wall-clock date and time of [epochMillis] in [zone], daylight saving included. */
    fun localDateTime(epochMillis: Long, zone: TimeZone): LocalDateTime =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)

    /**
     * Finished workouts whose end falls on one of the last 28 calendar days of [zone], today
     * included (today minus 27 days through today). A workout still running does not count.
     */
    fun finishedInLast28Days(history: List<WorkoutUi>, nowMillis: Long, zone: TimeZone): Int {
        val today = localDateTime(nowMillis, zone).date
        val first = today.minus(DatePeriod(days = 27))
        return history.count { workout ->
            val end = workout.endedAt ?: return@count false
            val day = localDateTime(end, zone).date
            day >= first && day <= today
        }
    }
}
