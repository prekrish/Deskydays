package com.deskdays.core

import com.deskdays.core.EventSource.GEOFENCE
import com.deskdays.core.EventSource.MANUAL
import com.deskdays.core.EventSource.WIFI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitsTest {
    private val now = at("2026-11-19 12:00")

    @Test fun `simple enter exit pair`() {
        val v = buildVisits(officeDay("2026-11-18"), now, SYD).single()
        assertEquals(at("2026-11-18 09:00"), v.start)
        assertEquals(at("2026-11-18 15:00"), v.end)
        assertFalse(v.needsReview)
        assertFalse(v.ongoing)
    }

    @Test fun `unsorted input is sorted`() {
        val v = buildVisits(officeDay("2026-11-18").reversed(), now, SYD).single()
        assertEquals(360, v.duration.toMinutes())
    }

    @Test fun `duplicate enters and orphan exits are ignored`() {
        val events = listOf(
            exit("2026-11-18 08:00"),
            enter("2026-11-18 09:00"),
            enter("2026-11-18 09:30"),
            exit("2026-11-18 15:00"),
            exit("2026-11-18 15:05"),
        )
        val v = buildVisits(events, now, SYD).single()
        assertEquals(at("2026-11-18 09:00"), v.start)
        assertEquals(at("2026-11-18 15:00"), v.end)
    }

    @Test fun `visit stays open while another source is still inside`() {
        val events = listOf(
            enter("2026-11-18 09:00", GEOFENCE),
            enter("2026-11-18 09:05", WIFI),
            exit("2026-11-18 12:00", WIFI), // Wi-Fi dropped, still in geofence
            exit("2026-11-18 17:00", GEOFENCE),
        )
        val v = buildVisits(events, now, SYD).single()
        assertEquals(at("2026-11-18 17:00"), v.end)
        assertEquals(setOf(GEOFENCE, WIFI), v.sources)
    }

    @Test fun `manual exit closes regardless of other sources`() {
        val events = listOf(
            enter("2026-11-18 09:00", GEOFENCE),
            enter("2026-11-18 09:05", WIFI),
            exit("2026-11-18 14:00", MANUAL),
        )
        val v = buildVisits(events, now, SYD).single()
        assertEquals(at("2026-11-18 14:00"), v.end)
        assertFalse(v.needsReview)
    }

    @Test fun `missing exit at end of day closes at last confirmed signal and is flagged`() {
        val events = listOf(
            enter("2026-11-17 09:00", GEOFENCE),
            enter("2026-11-17 13:30", WIFI), // confirmation
            enter("2026-11-18 09:00"),
            exit("2026-11-18 15:00"),
        )
        val visits = buildVisits(events, now, SYD)
        assertEquals(2, visits.size)
        assertEquals(at("2026-11-17 13:30"), visits[0].end)
        assertTrue(visits[0].needsReview)
        assertFalse(visits[1].needsReview)
    }

    @Test fun `exit arriving the next day is not trusted`() {
        val events = listOf(enter("2026-11-17 09:00"), exit("2026-11-18 07:00"))
        val v = buildVisits(events, now, SYD).single()
        assertEquals(at("2026-11-17 09:00"), v.end)
        assertTrue(v.needsReview)
    }

    @Test fun `open visit today is ongoing until now`() {
        val v = buildVisits(listOf(enter("2026-11-19 09:12")), now, SYD).single()
        assertTrue(v.ongoing)
        assertEquals(now, v.end)
        assertEquals(168, v.duration.toMinutes())
    }

    @Test fun `open visit from an earlier day with no more events is flagged`() {
        val v = buildVisits(listOf(enter("2026-11-18 09:00")), now, SYD).single()
        assertTrue(v.needsReview)
        assertFalse(v.ongoing)
    }

    @Test fun `short gaps are merged and long ones are not`() {
        val raw = listOf(
            Visit(at("2026-11-18 09:00"), at("2026-11-18 12:00"), setOf(GEOFENCE)),
            Visit(at("2026-11-18 12:14"), at("2026-11-18 14:00"), setOf(WIFI)), // 14 min gap
            Visit(at("2026-11-18 14:15"), at("2026-11-18 16:00"), setOf(GEOFENCE)), // 15 min gap
        )
        val merged = mergeVisits(raw, mergeGapMinutes = 15)
        assertEquals(2, merged.size)
        assertEquals(at("2026-11-18 09:00"), merged[0].start)
        assertEquals(at("2026-11-18 14:00"), merged[0].end)
        assertEquals(2, merged[0].segments.size)
        assertEquals(listOf(14L), merged[0].mergedGaps.map { it.toMinutes() })
        assertEquals(setOf(GEOFENCE, WIFI), merged[0].sources)
        assertTrue(merged[1].segments.isEmpty())
    }

    @Test fun `overlapping visits merge even with zero gap setting`() {
        val raw = listOf(
            Visit(at("2026-11-18 09:00"), at("2026-11-18 12:00"), setOf(GEOFENCE)),
            Visit(at("2026-11-18 11:00"), at("2026-11-18 11:30"), setOf(MANUAL)),
        )
        val merged = mergeVisits(raw, mergeGapMinutes = 0).single()
        assertEquals(at("2026-11-18 12:00"), merged.end)
    }

    @Test fun `merge flags propagate`() {
        val raw = listOf(
            Visit(at("2026-11-19 09:00"), at("2026-11-19 10:00"), setOf(GEOFENCE), needsReview = true),
            Visit(at("2026-11-19 10:05"), now, setOf(GEOFENCE), ongoing = true),
        )
        val merged = mergeVisits(raw, 15).single()
        assertTrue(merged.needsReview)
        assertTrue(merged.ongoing)
    }

    @Test fun `minutes are clipped to the local day`() {
        val v = Visit(at("2026-11-18 22:00"), at("2026-11-19 02:30"), setOf(MANUAL))
        assertEquals(120, minutesOnDay(listOf(v), day("2026-11-18"), SYD))
        assertEquals(150, minutesOnDay(listOf(v), day("2026-11-19"), SYD))
        assertEquals(0, minutesOnDay(listOf(v), day("2026-11-20"), SYD))
    }

    @Test fun `day clipping handles DST start in Sydney`() {
        // 2026-10-04: clocks go forward at 02:00 → 03:00, so the day is 23 hours long.
        val start = day("2026-10-04").atStartOfDay(SYD).toInstant()
        val end = day("2026-10-05").atStartOfDay(SYD).toInstant()
        val v = Visit(start, end, setOf(MANUAL))
        assertEquals(23 * 60, minutesOnDay(listOf(v), day("2026-10-04"), SYD))
    }

    @Test fun `events crossing midnight in Sydney but not in UTC split correctly`() {
        // 23:30 and 00:30 Sydney are the same UTC date; the visit must still be flagged.
        val events = listOf(enter("2026-11-18 23:30"), exit("2026-11-19 00:30"))
        val v = buildVisits(events, now, SYD).single()
        assertTrue(v.needsReview)
    }
}
