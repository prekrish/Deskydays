package com.deskdays.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/** Supplies public holidays for a year. */
fun interface HolidayCalendar {
    fun holidays(year: Int): Map<LocalDate, String>
}

/** No public holidays at all. */
val NoHolidays = HolidayCalendar { emptyMap() }

/**
 * New South Wales public holidays (Public Holidays Act 2010 NSW), computed from rules so it
 * works for any year. Bank Holiday (first Monday in August) is excluded because it only applies
 * to banks. Additional days are added when New Year's Day, Australia Day, Christmas Day or
 * Boxing Day fall on a weekend. Anzac Day has no substitute day in NSW.
 *
 * One-off proclaimed holidays (e.g. the 2022 National Day of Mourning) aren't covered; users can
 * mark those days manually.
 */
object NswHolidays : HolidayCalendar {
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, Map<LocalDate, String>>()

    override fun holidays(year: Int): Map<LocalDate, String> = cache.getOrPut(year) { compute(year) }

    private fun compute(year: Int): Map<LocalDate, String> {
        val h = linkedMapOf<LocalDate, String>()

        fun isWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
        fun nextFreeWeekday(from: LocalDate): LocalDate {
            var d = from.plusDays(1)
            while (isWeekend(d) || d in h) d = d.plusDays(1)
            return d
        }

        val newYear = LocalDate.of(year, Month.JANUARY, 1)
        h[newYear] = "New Year's Day"
        if (isWeekend(newYear)) h[nextFreeWeekday(newYear)] = "New Year's Day (additional day)"

        val australiaDay = LocalDate.of(year, Month.JANUARY, 26)
        if (isWeekend(australiaDay)) {
            h[australiaDay] = "Australia Day"
            h[nextFreeWeekday(australiaDay)] = "Australia Day (observed)"
        } else {
            h[australiaDay] = "Australia Day"
        }

        val easter = easterSunday(year)
        h[easter.minusDays(2)] = "Good Friday"
        h[easter.minusDays(1)] = "Easter Saturday"
        h[easter] = "Easter Sunday"
        h[easter.plusDays(1)] = "Easter Monday"

        h[LocalDate.of(year, Month.APRIL, 25)] = "Anzac Day"

        h[LocalDate.of(year, Month.JUNE, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY))] =
            if (year >= 2023) "King's Birthday" else "Queen's Birthday"

        h[LocalDate.of(year, Month.OCTOBER, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))] = "Labour Day"

        val christmas = LocalDate.of(year, Month.DECEMBER, 25)
        val boxing = LocalDate.of(year, Month.DECEMBER, 26)
        h[christmas] = "Christmas Day"
        h[boxing] = "Boxing Day"
        if (isWeekend(christmas)) h[nextFreeWeekday(christmas)] = "Christmas Day (additional day)"
        if (isWeekend(boxing)) h[nextFreeWeekday(boxing)] = "Boxing Day (additional day)"

        return h.toSortedMap()
    }

    /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
    internal fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val hh = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - hh - k) % 7
        val m = (a + 11 * hh + 22 * l) / 451
        val month = (hh + l - 7 * m + 114) / 31
        val day = ((hh + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}
