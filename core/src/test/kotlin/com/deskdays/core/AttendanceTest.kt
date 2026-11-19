package com.deskdays.core

import java.time.DayOfWeek
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceTest {
    private val calc = AttendanceCalculator(CountingRules(), NswHolidays)
    private val nov = YearMonth.of(2026, 11)
    private val now = at("2026-11-19 11:52") // Thursday

    private fun status(events: List<PresenceEvent>, date: String, overrides: Map<java.time.LocalDate, DayStatus> = emptyMap()) =
        calc.days(day(date), day(date), events, overrides, now).single()

    @Test fun `four hours is an office day, just under is remote`() {
        assertEquals(DayStatus.OFFICE, status(officeDay("2026-11-18", "09:00", "13:00"), "2026-11-18").status)
        val d = status(officeDay("2026-11-18", "09:00", "12:59"), "2026-11-18")
        assertEquals(DayStatus.REMOTE, d.status)
        assertEquals(239, d.minutes)
    }

    @Test fun `short exit is merged so the day counts`() {
        val events = listOf(
            enter("2026-11-18 09:00"), exit("2026-11-18 11:00"),
            enter("2026-11-18 11:10"), exit("2026-11-18 13:00"),
        )
        val d = status(events, "2026-11-18")
        assertEquals(240, d.minutes)
        assertEquals(DayStatus.OFFICE, d.status)
        assertEquals(1, d.visits.size)
    }

    @Test fun `manual override always wins`() {
        val events = officeDay("2026-11-18")
        val d = status(events, "2026-11-18", mapOf(day("2026-11-18") to DayStatus.REMOTE))
        assertEquals(DayStatus.REMOTE, d.status)
        assertTrue(d.manuallySet)
        assertEquals(DayStatus.OFFICE, status(emptyList(), "2026-11-17", mapOf(day("2026-11-17") to DayStatus.OFFICE)).status)
    }

    @Test fun `only manual statuses can be overridden`() {
        assertFailsWith<IllegalArgumentException> {
            status(emptyList(), "2026-11-18", mapOf(day("2026-11-18") to DayStatus.PENDING))
        }
    }

    @Test fun `today and future days are pending, weekends non-working, holidays marked`() {
        assertEquals(DayStatus.PENDING, status(listOf(enter("2026-11-19 09:12")), "2026-11-19").status)
        assertEquals(DayStatus.PENDING, status(emptyList(), "2026-11-20").status)
        assertEquals(DayStatus.NON_WORKING, status(emptyList(), "2026-11-21").status)
        val xmas = status(emptyList(), "2026-12-25")
        assertEquals(DayStatus.HOLIDAY, xmas.status)
        assertEquals("Christmas Day", xmas.holidayName)
    }

    @Test fun `today becomes office as soon as the threshold is reached`() {
        val d = calc.days(day("2026-11-19"), day("2026-11-19"), listOf(enter("2026-11-19 07:30")), emptyMap(), now).single()
        assertEquals(262, d.minutes)
        assertEquals(DayStatus.OFFICE, d.status)
        assertTrue(d.visits.single().ongoing)
    }

    @Test fun `office attendance on a public holiday counts`() {
        val d = status(officeDay("2026-10-05"), "2026-10-05") // Labour Day
        assertEquals(DayStatus.OFFICE, d.status)
        assertEquals("Labour Day", d.holidayName)
    }

    /** Recreates the numbers on the Stats mockup: 21 working days, 1 leave, 7 office days. */
    @Test fun `month stats match the mockup`() {
        val officeDates = listOf(3, 5, 10, 12, 13, 17, 18)
        val events = officeDates.flatMap { officeDay("2026-11-%02d".format(it)) } +
            listOf(enter("2026-11-19 09:12")) // today, in progress
        val overrides = mapOf(day("2026-11-06") to DayStatus.LEAVE)

        val m = calc.month(nov, events, overrides, now)
        assertEquals(21, m.workingDays)
        assertEquals(1, m.leaveDays)
        assertEquals(0, m.holidayDays)
        assertEquals(20, m.eligibleDays)
        assertEquals(10, m.targetDays)
        assertEquals(7, m.officeDays)
        assertEquals(35, m.percent)
        assertEquals(3, m.stillNeeded)
        // Thu 19 (in progress) + 20, 23–27, 30 = 8 eligible days still to come.
        assertEquals(8, m.workingDaysRemaining)
        assertFalse(m.targetMet)
        assertTrue(m.stillAchievable)
    }

    @Test fun `once today counts it is no longer remaining`() {
        val events = listOf(enter("2026-11-19 07:00"))
        val m = calc.month(nov, events, emptyMap(), now)
        assertEquals(1, m.officeDays)
        assertEquals(7, m.workingDaysRemaining)
    }

    @Test fun `target rounds up`() {
        // April 2026: 22 weekdays − Good Friday, Easter Monday = 20 (Anzac Day is a Saturday).
        val m = calc.month(YearMonth.of(2026, 4), emptyList(), emptyMap(), at("2026-05-01 09:00"))
        assertEquals(22, m.workingDays)
        assertEquals(2, m.holidayDays)
        assertEquals(20, m.eligibleDays)
        assertEquals(10, m.targetDays)
        assertEquals(0, m.workingDaysRemaining)

        // 19 eligible days → ceil(9.5) = 10
        val m2 = calc.month(
            YearMonth.of(2026, 4), emptyList(), mapOf(day("2026-04-01") to DayStatus.LEAVE), at("2026-05-01 09:00"),
        )
        assertEquals(19, m2.eligibleDays)
        assertEquals(10, m2.targetDays)
    }

    @Test fun `weekend office day counts as office but not as eligible`() {
        val events = officeDay("2026-11-14") // Saturday
        val m = calc.month(nov, events, emptyMap(), now)
        assertEquals(1, m.officeDays)
        assertEquals(21, m.eligibleDays)

        val strict = AttendanceCalculator(CountingRules(countNonWorkingDayAttendance = false), NswHolidays)
        assertEquals(0, strict.month(nov, events, emptyMap(), now).officeDays)
    }

    @Test fun `days before tracking started do not count`() {
        val m = calc.month(nov, emptyList(), emptyMap(), now, trackedFrom = day("2026-11-16"))
        assertEquals(10, m.untrackedDays) // Nov 2–6, 9–13
        assertEquals(11, m.eligibleDays)
        assertEquals(6, m.targetDays)
    }

    @Test fun `zero eligible days does not divide by zero`() {
        val overrides = (1..30).map { day("2026-11-%02d".format(it)) }.associateWith { DayStatus.LEAVE }
        val m = calc.month(nov, emptyList(), overrides, now)
        assertEquals(0, m.eligibleDays)
        assertEquals(0.0, m.fraction)
        assertEquals(0, m.targetDays)
        assertTrue(m.targetMet)
    }

    @Test fun `custom rules are respected`() {
        val rules = CountingRules(
            thresholdMinutes = 360,
            mergeGapMinutes = 0,
            targetPercent = 60,
            workingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
        )
        val c = AttendanceCalculator(rules, NoHolidays)
        val d = c.days(day("2026-11-18"), day("2026-11-18"), officeDay("2026-11-18", "09:00", "14:00"), emptyMap(), now).single()
        assertEquals(DayStatus.REMOTE, d.status)
        val m = c.month(nov, emptyList(), emptyMap(), now)
        assertEquals(17, m.workingDays) // Mon–Thu in Nov 2026
        assertEquals(11, m.targetDays) // ceil(10.2)
        assertEquals(DayStatus.NON_WORKING, c.days(day("2026-11-20"), day("2026-11-20"), emptyList(), emptyMap(), now).single().status)
    }

    @Test fun `history returns oldest month first`() {
        val events = officeDay("2026-06-01") + officeDay("2026-11-18")
        val h = calc.history(nov, 6, events, emptyMap(), now)
        assertEquals((6..11).map { YearMonth.of(2026, it) }, h.map { it.month })
        assertEquals(1, h.first().officeDays)
        assertEquals(1, h.last().officeDays)
    }

    @Test fun `invalid rules are rejected`() {
        assertFailsWith<IllegalArgumentException> { CountingRules(targetPercent = 101) }
        assertFailsWith<IllegalArgumentException> { CountingRules(workingDays = emptySet()) }
    }

    @Test fun `csv export`() {
        val days = calc.days(day("2026-11-18"), day("2026-11-19"), officeDay("2026-11-18"), emptyMap(), now)
        val csv = daysToCsv(days, SYD).split("\r\n")
        assertEquals("date,weekday,status,minutes,hours,visits,manual,needs_review,holiday", csv[0])
        assertEquals("2026-11-18,Wednesday,OFFICE,360,6.00,09:00-15:00,false,false,", csv[1])
        assertEquals("2026-11-19,Thursday,PENDING,0,0.00,,false,false,", csv[2])
        assertEquals("\"a,b\"", csvEscape("a,b"))
        assertEquals("'=SUM(A1)", csvEscape("=SUM(A1)"))
    }
}
