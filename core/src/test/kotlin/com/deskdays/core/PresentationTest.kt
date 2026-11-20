package com.deskdays.core

import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentationTest {
    private val calc = AttendanceCalculator()

    @Test fun `minutes format`() {
        assertEquals("0m", formatMinutes(0))
        assertEquals("45m", formatMinutes(45))
        assertEquals("4h", formatMinutes(240))
        assertEquals("2h 40m", formatMinutes(160))
        assertEquals("0m", formatMinutes(-5))
    }

    /** The Today mockup: arrived 9:12, now 11:52 → 2h 40m of 4h, 1h 20m to go. */
    @Test fun `today progress matches the mockup`() {
        val now = at("2026-11-19 11:52")
        val events = listOf(
            enter("2026-11-19 09:12", EventSource.GEOFENCE),
            enter("2026-11-19 09:14", EventSource.WIFI),
        )
        val day = calc.days(day("2026-11-19"), day("2026-11-19"), events, emptyMap(), now).single()
        val p = todayProgress(day, 240, SYD)
        assertEquals(160, p.minutes)
        assertEquals(80, p.remainingMinutes)
        assertEquals(at("2026-11-19 09:12"), p.arrivedAt)
        assertEquals(setOf(EventSource.GEOFENCE, EventSource.WIFI), p.sources)
        assertTrue(p.inOfficeNow)
        assertFalse(p.counts)
        assertEquals(160f / 240f, p.fraction)
    }

    @Test fun `progress caps at full and clips arrival to midnight`() {
        val now = at("2026-11-19 10:00")
        val events = listOf(enter("2026-11-19 05:00"))
        val day = calc.days(day("2026-11-19"), day("2026-11-19"), events, emptyMap(), now).single()
        val p = todayProgress(day, 240, SYD)
        assertEquals(1f, p.fraction)
        assertEquals(0, p.remainingMinutes)
        assertTrue(p.counts)

        val empty = calc.days(day("2026-11-19"), day("2026-11-19"), emptyList(), emptyMap(), now).single()
        val e = todayProgress(empty, 240, SYD)
        assertEquals(null, e.arrivedAt)
        assertFalse(e.inOfficeNow)
    }

    @Test fun `nudges`() {
        val nov = YearMonth.of(2026, 11)
        val now = at("2026-11-19 11:52")
        val seven = listOf(3, 5, 10, 12, 13, 17, 18).flatMap { officeDay("2026-11-%02d".format(it)) }
        val overrides = mapOf(day("2026-11-06") to DayStatus.LEAVE)
        assertEquals(MonthNudge.OnTrack(3, 8), monthNudge(calc.month(nov, seven, overrides, now)))

        val ten = seven + listOf(2, 4, 9).flatMap { officeDay("2026-11-%02d".format(it)) }
        assertEquals(MonthNudge.TargetMet, monthNudge(calc.month(nov, ten, overrides, now)))

        // 30 Nov is still "remaining" until midnight, but 3 days can't fit in 1.
        val late = at("2026-11-30 18:00")
        assertEquals(MonthNudge.OutOfReach(3, 1), monthNudge(calc.month(nov, seven, overrides, late)))

        val allLeave = (1..30).map { day("2026-11-%02d".format(it)) }.associateWith { DayStatus.LEAVE }
        assertEquals(MonthNudge.NoEligibleDays, monthNudge(calc.month(nov, emptyList(), allLeave, now)))
    }

    @Test fun `break minutes of a merged visit`() {
        val events = listOf(
            enter("2026-11-18 08:41"), exit("2026-11-18 12:30"),
            enter("2026-11-18 12:40"), exit("2026-11-18 16:46"),
        )
        val d = calc.days(day("2026-11-18"), day("2026-11-18"), events, emptyMap(), at("2026-11-19 09:00")).single()
        assertEquals(listOf(10L), d.visits.single().breakMinutes())
    }
}
