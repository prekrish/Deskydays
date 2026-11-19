package com.deskdays.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class NswHolidaysTest {
    private fun dates(year: Int) = NswHolidays.holidays(year).keys.map(LocalDate::toString)

    @Test fun `easter dates`() {
        assertEquals(day("2024-03-31"), NswHolidays.easterSunday(2024))
        assertEquals(day("2025-04-20"), NswHolidays.easterSunday(2025))
        assertEquals(day("2026-04-05"), NswHolidays.easterSunday(2026))
        assertEquals(day("2027-03-28"), NswHolidays.easterSunday(2027))
    }

    @Test fun `nsw 2025`() {
        assertEquals(
            listOf(
                "2025-01-01", "2025-01-26", "2025-01-27", "2025-04-18", "2025-04-19", "2025-04-20", "2025-04-21",
                "2025-04-25", "2025-06-09", "2025-10-06", "2025-12-25", "2025-12-26",
            ),
            dates(2025),
        )
    }

    @Test fun `nsw 2026 - Boxing Day on Saturday moves to Monday, no Anzac substitute`() {
        assertEquals(
            listOf(
                "2026-01-01", "2026-01-26", "2026-04-03", "2026-04-04", "2026-04-05", "2026-04-06",
                "2026-04-25", "2026-06-08", "2026-10-05", "2026-12-25", "2026-12-26", "2026-12-28",
            ),
            dates(2026),
        )
    }

    @Test fun `christmas on saturday - 2021 additional days on 27 and 28`() {
        val h = NswHolidays.holidays(2021)
        assertEquals("Christmas Day (additional day)", h[day("2021-12-27")])
        assertEquals("Boxing Day (additional day)", h[day("2021-12-28")])
    }

    @Test fun `christmas on sunday - 2022 Boxing Monday then Tuesday additional`() {
        val h = NswHolidays.holidays(2022)
        assertEquals("Boxing Day", h[day("2022-12-26")])
        assertEquals("Christmas Day (additional day)", h[day("2022-12-27")])
    }

    @Test fun `new year on weekend gets a monday`() {
        assertEquals("New Year's Day (additional day)", NswHolidays.holidays(2022)[day("2022-01-03")])
        assertEquals("New Year's Day (additional day)", NswHolidays.holidays(2023)[day("2023-01-02")])
    }

    @Test fun `king vs queen naming`() {
        assertEquals("Queen's Birthday", NswHolidays.holidays(2022)[day("2022-06-13")])
        assertEquals("King's Birthday", NswHolidays.holidays(2027)[day("2027-06-14")])
    }
}
