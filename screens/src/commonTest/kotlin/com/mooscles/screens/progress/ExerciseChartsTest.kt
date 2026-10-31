package com.mooscles.screens.progress

import com.mooscles.screens.format.ScreenDates
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class ExerciseChartsTest {
    private val utc = TimeZone.UTC
    private val today = LocalDate(2026, 10, 31)

    private fun ms(date: LocalDate, hour: Int = 12, minute: Int = 0, zone: TimeZone = utc): Long =
        LocalDateTime(date.year, date.month, date.day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d)

    private fun session(id: String, startedAt: Long, kg: Double, reps: Int = 5, exercise: ExerciseUi = bench): WorkoutUi =
        workout(id, startedAt, entry(exercise, set(kg, reps)))

    private fun index(vararg workouts: WorkoutUi, records: List<RecordUi> = emptyList()): ProgressIndex =
        ProgressIndex.build(workouts.toList().asReversed(), records)

    private fun query(
        metric: ProgressMetric = ProgressMetric.LOAD,
        period: ExercisePeriod = ExercisePeriod.ALL,
        today: LocalDate = this.today,
        zone: TimeZone = utc,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
        key: SeriesKey = keyOf(bench),
    ) = ExerciseChartQuery(key, metric, period, today, zone, weekStart)

    /** One session a day for 110 days ending on 2026-10-31, [kg] taking the day number (0 = the oldest). */
    private fun daily(exercise: ExerciseUi = bench, assistance: Boolean = false, kg: (Int) -> Double): List<WorkoutUi> {
        val first = day(2026, 7, 14)
        return (0 until 110).map { n ->
            val date = LocalDate.fromEpochDays(first.toEpochDays() + n)
            workout("d$n", ms(date), entry(exercise, set(kg(n), 5, assistance = assistance)))
        }
    }

    @Test
    fun pointsAreOnePerSessionOldestFirstInsideThePeriod() {
        val old = session("old", ms(day(2025, 1, 5)), 60.0)
        val a = session("a", ms(day(2026, 8, 1)), 80.0)
        val b = session("b", ms(day(2026, 9, 1)), 85.0)
        val c = session("c", ms(day(2026, 10, 1)), 90.0)
        val chart = ExerciseCharts.query(index(old, a, b, c), query(period = ExercisePeriod.THREE_MONTHS))
        assertEquals(listOf("a", "b", "c"), chart.points.map { it.workoutId })
        assertEquals(listOf(80.0, 85.0, 90.0), chart.points.map { it.value })
        assertEquals(3, chart.sessionCount)
        assertFalse(chart.weekly)
        assertNull(chart.empty)
    }

    @Test
    fun periodsCountCalendarMonthsFromToday() {
        val out = session("out", ms(day(2026, 7, 30)), 80.0)
        val inside = session("in", ms(day(2026, 7, 31)), 85.0)
        val ix = index(out, inside)
        assertEquals(listOf("in"), ExerciseCharts.query(ix, query(period = ExercisePeriod.THREE_MONTHS)).points.map { it.workoutId })
        // Six months back from 31 Oct is 30 Apr (April has no 31st); a year back is 31 Oct 2025.
        val april = session("apr", ms(day(2026, 4, 30)), 70.0)
        val before = session("before", ms(day(2026, 4, 29), 23, 59), 70.0)
        val ix2 = index(before, april)
        assertEquals(listOf("apr"), ExerciseCharts.query(ix2, query(period = ExercisePeriod.SIX_MONTHS)).points.map { it.workoutId })
        val yearEdge = session("y", ms(day(2025, 10, 31)), 60.0)
        val yearOut = session("yo", ms(day(2025, 10, 30)), 60.0)
        assertEquals(listOf("y"), ExerciseCharts.query(index(yearOut, yearEdge), query(period = ExercisePeriod.ONE_YEAR)).points.map { it.workoutId })
    }

    @Test
    fun periodStartIsInclusiveAtLocalMidnight() {
        val zone = TimeZone.of("America/Sao_Paulo")
        val midnight = session("mid", ms(day(2026, 7, 31), 0, 0, zone), 80.0)
        val before = session("before", ms(day(2026, 7, 30), 23, 59, zone), 85.0)
        val chart = ExerciseCharts.query(index(before, midnight), query(period = ExercisePeriod.THREE_MONTHS, zone = zone))
        assertEquals(listOf("mid"), chart.points.map { it.workoutId })
    }

    @Test
    fun allIncludesEverySession() {
        val ws = listOf(session("a", ms(day(2010, 1, 1)), 40.0), session("b", ms(day(2026, 10, 31)), 90.0))
        assertEquals(2, ExerciseCharts.query(index(*ws.toTypedArray()), query()).points.size)
    }

    @Test
    fun nearMidnightWorkoutCountsOnItsStartDay() {
        // Starts 23:50 on 30 Oct and ends 00:40 on 31 Oct: it belongs to the 30th.
        val start = ms(day(2026, 10, 30), 23, 50)
        val w = WorkoutUi("late", "Late", start, start + 50 * 60_000L, listOf(entry(bench, set(80.0, 5))), emptyList())
        val ix = ProgressIndex.build(listOf(w), emptyList())
        // Three months back from 30 Jan 2027 is 30 Oct: the workout is in; from 31 Jan it is the 31st: out.
        assertEquals(1, ExerciseCharts.query(ix, query(period = ExercisePeriod.THREE_MONTHS, today = day(2027, 1, 30))).points.size)
        val after = ExerciseCharts.query(ix, query(period = ExercisePeriod.THREE_MONTHS, today = day(2027, 1, 31)))
        assertEquals(EmptyReason.NO_POINTS, after.empty)
    }

    @Test
    fun dstChangeKeepsWorkoutsOnTheirStartDay() {
        // Sao Paulo's clocks jumped from 00:00 to 01:00 on 4 Nov 2018, so that day has no local midnight.
        val sp = TimeZone.of("America/Sao_Paulo")
        val spIn = session("sp-in", ms(day(2018, 11, 4), 1, 0, sp), 80.0)
        val spOut = session("sp-out", ms(day(2018, 11, 3), 23, 30, sp), 80.0)
        val spChart = ExerciseCharts.query(index(spOut, spIn), query(period = ExercisePeriod.THREE_MONTHS, today = day(2019, 2, 4), zone = sp))
        assertEquals(listOf("sp-in"), spChart.points.map { it.workoutId })
        // Berlin: 29 Mar 2026 has 23 hours; both sides of the 02:00 jump are the 29th, the evening before is not.
        val berlin = TimeZone.of("Europe/Berlin")
        val before = session("b-before", ms(day(2026, 3, 28), 23, 30, berlin), 80.0)
        val early = session("b-early", ms(day(2026, 3, 29), 0, 30, berlin), 82.0)
        val late = session("b-late", ms(day(2026, 3, 29), 3, 30, berlin), 84.0)
        val chart = ExerciseCharts.query(index(before, early, late), query(period = ExercisePeriod.THREE_MONTHS, today = day(2026, 6, 29), zone = berlin))
        assertEquals(listOf("b-early", "b-late"), chart.points.map { it.workoutId })
    }

    @Test
    fun moreThan104PointsReduceToTheWeeklyBest() {
        val ix = index(*daily { 50.0 + it }.toTypedArray())
        val chart = ExerciseCharts.query(ix, query(weekStart = DayOfWeek.MONDAY))
        assertTrue(chart.weekly)
        assertTrue(chart.points.size in 15..17)
        assertTrue(chart.points.all { it.workoutId == null })
        chart.points.forEach { point ->
            val date = ScreenDates.localDate(point.startedAt, utc)
            assertEquals(DayOfWeek.MONDAY, date.dayOfWeek)
            // Load rises every day, so the week's best is its last day inside the data.
            val lastDay = minOf(date.toEpochDays() + 6, today.toEpochDays())
            assertEquals(50.0 + (lastDay - day(2026, 7, 14).toEpochDays()), point.value)
        }
        assertEquals(110, chart.sessionCount)
        val exactly104 = ExerciseCharts.query(index(*daily { 50.0 + it }.take(104).toTypedArray()), query())
        assertFalse(exactly104.weekly)
        assertEquals(104, exactly104.points.size)
    }

    @Test
    fun weeklyPointsOnASaoPauloDstDayStartWhereTheDayStarts() {
        val sp = TimeZone.of("America/Sao_Paulo")
        val first = day(2018, 8, 1)
        val ws = (0 until 110).map { n ->
            workout("s$n", ms(LocalDate.fromEpochDays(first.toEpochDays() + n), 12, 0, sp), entry(bench, set(60.0, 5)))
        }
        val chart = ExerciseCharts.query(index(*ws.toTypedArray()), query(zone = sp, weekStart = DayOfWeek.SUNDAY, today = day(2018, 11, 18)))
        val dstWeek = chart.points.map { ScreenDates.localDate(it.startedAt, sp) }
        assertTrue(day(2018, 11, 4) in dstWeek)
        assertTrue(dstWeek.all { it.dayOfWeek == DayOfWeek.SUNDAY })
    }

    @Test
    fun weeklyAssistanceKeepsTheLowest() {
        // Less help each day: the week's lowest assistance is its last day inside the data.
        val ix = index(*daily(assistedDip, assistance = true) { 60.0 - it * 0.25 }.toTypedArray())
        val chart = ExerciseCharts.query(ix, query(key = keyOf(assistedDip), weekStart = DayOfWeek.MONDAY))
        assertTrue(chart.weekly)
        chart.points.forEach { point ->
            val date = ScreenDates.localDate(point.startedAt, utc)
            val lastDay = minOf(date.toEpochDays() + 6, today.toEpochDays())
            assertEquals(60.0 - (lastDay - day(2026, 7, 14).toEpochDays()) * 0.25, point.value)
        }
        // Best is the lowest assistance and "down" is progress: the wording is not inverted.
        assertEquals(chart.points.last().value, chart.best!!.value)
        assertEquals(Trend.DOWN, chart.trend)
        // The reps chart of the same series keeps the highest.
        val reps = ExerciseCharts.query(ix, query(metric = ProgressMetric.REPS, key = keyOf(assistedDip)))
        assertTrue(reps.points.all { it.value == 5.0 })
    }

    @Test
    fun weeklyReductionUsesTheGivenWeekStart() {
        val ix = index(*daily { 50.0 + it }.toTypedArray())
        val starts = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY).map { first ->
            val chart = ExerciseCharts.query(ix, query(weekStart = first))
            assertTrue(chart.points.all { ScreenDates.localDate(it.startedAt, utc).dayOfWeek == first })
            chart.points.map { it.startedAt }
        }
        assertEquals(3, starts.toSet().size)
        assertNotEquals(starts[0], starts[1])
    }

    @Test
    fun weeklyPointIsMarkedWhenAnySessionOfTheWeekIs() {
        val plain = daily { 50.0 + it }
        // Day 10 is a beaten weight record at its own load; the week's best point is a later, unmarked day.
        val rec = weightRecord(bench, 60.0, 5, previousKg = 59.0, workout = plain[10])
        val marked = plain[10].copy(records = listOf(rec))
        val ws = plain.toMutableList().also { it[10] = marked }
        val chart = ExerciseCharts.query(index(*ws.toTypedArray(), records = listOf(currentOf(rec))), query())
        val markedWeek = ScreenDates.localDate(marked.startedAt, utc).let { d ->
            d.toEpochDays() - ((d.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7)
        }
        val flagged = chart.points.filter { it.record }
        assertEquals(1, flagged.size)
        assertEquals(markedWeek, ScreenDates.localDate(flagged.single().startedAt, utc).toEpochDays())
        assertTrue(flagged.single().value > 60.0)
        // Per session (a short period) the mark sits on that session only; E1RM never has one.
        val perSession = ExerciseCharts.query(index(*ws.take(20).toTypedArray(), records = listOf(currentOf(rec))), query())
        assertEquals(listOf("d10"), perSession.points.filter { it.record }.map { it.workoutId })
        val e1rm = ExerciseCharts.query(index(*ws.take(20).toTypedArray(), records = listOf(currentOf(rec))), query(metric = ProgressMetric.E1RM))
        assertTrue(e1rm.points.none { it.record })
    }

    @Test
    fun firstLastBestAndTrend() {
        val ws = listOf(
            session("a", ms(day(2026, 8, 1)), 80.0), session("b", ms(day(2026, 8, 15)), 100.0),
            session("c", ms(day(2026, 9, 1)), 90.0), session("d", ms(day(2026, 9, 15)), 95.0),
        )
        val chart = ExerciseCharts.query(index(*ws.toTypedArray()), query())
        assertEquals("a", chart.first!!.workoutId)
        assertEquals("d", chart.last!!.workoutId)
        assertEquals(100.0, chart.best!!.value)
        assertEquals(Trend.UP, chart.trend)
        val two = ExerciseCharts.query(index(*ws.take(2).toTypedArray()), query())
        assertNull(two.trend)
        assertEquals(2, two.sessionCount)
    }

    @Test
    fun weeklyTrendLeavesOutTheRunningWeek() {
        // Flat 100 kg, but the week in progress (from Monday 26 Oct) shows 300: with it the trend would be UP.
        val ix = index(*daily { if (it >= 104) 300.0 else 100.0 }.toTypedArray())
        val chart = ExerciseCharts.query(ix, query(weekStart = DayOfWeek.MONDAY))
        assertTrue(chart.weekly)
        assertEquals(300.0, chart.last!!.value)
        assertEquals(Trend.FLAT, chart.trend)
    }

    @Test
    fun metricWithoutPointsInPeriodSaysWhy() {
        val heavy = session("h", ms(day(2026, 10, 1)), 100.0, reps = 12)
        val ix = index(heavy)
        assertEquals(EmptyReason.NO_ESTIMATE, ExerciseCharts.query(ix, query(metric = ProgressMetric.E1RM)).empty)
        assertEquals(EmptyReason.NO_POINTS, ExerciseCharts.query(ix, query(today = day(2027, 10, 31), period = ExercisePeriod.THREE_MONTHS)).empty)
        assertEquals(EmptyReason.NO_POINTS,
            ExerciseCharts.query(ix, query(metric = ProgressMetric.E1RM, today = day(2027, 10, 31), period = ExercisePeriod.THREE_MONTHS)).empty)
        assertEquals(EmptyReason.NO_SESSIONS, ExerciseCharts.query(ix, query(key = keyOf(dumbbellPress))).empty)
        val empty = ExerciseCharts.query(ix, query(metric = ProgressMetric.E1RM))
        assertTrue(empty.points.isEmpty())
        assertNull(empty.first)
        assertNull(empty.trend)
        assertEquals(0, empty.sessionCount)
    }

    @Test
    fun volumeIsTheSessionSumWithoutASetOrAMark() {
        val w = workout("v", ms(day(2026, 10, 1)), entry(bench, set(100.0, 5), set(80.0, 10)))
        val chart = ExerciseCharts.query(index(w), query(metric = ProgressMetric.VOLUME))
        assertEquals(1300.0, chart.points.single().value)
        assertNull(chart.points.single().set)
        assertFalse(chart.points.single().record)
    }

    @Test
    fun sparklineIsTheFirstMetricOfTheLastTwelveSessions() {
        val ws = (0 until 15).map { session("s$it", ms(day(2026, 9, 1 + it)), 60.0 + it) }
        val ix = index(*ws.toTypedArray())
        assertEquals((3 until 15).map { 60.0 + it }, ExerciseCharts.sparkline(ix, keyOf(bench)))
        assertEquals(listOf(72.0, 73.0, 74.0), ExerciseCharts.sparkline(ix, keyOf(bench), last = 3))
        assertEquals(emptyList(), ExerciseCharts.sparkline(ix, keyOf(dumbbellPress)))
        // Added weight opens on reps.
        val pulls = index(workout("p", ms(day(2026, 9, 1)), entry(pullUp, set(0.0, 9))))
        assertEquals(listOf(9.0), ExerciseCharts.sparkline(pulls, keyOf(pullUp)))
    }
}
