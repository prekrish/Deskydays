package com.deskdays.core

import com.deskdays.core.EventType.ENTER
import com.deskdays.core.EventType.EXIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetectionTest {
    private val now = at("2026-11-20 09:30")

    @Test fun `fix time is used when recent`() {
        assertEquals(at("2026-11-20 09:22"), resolveEventTime(at("2026-11-20 09:22"), now))
    }

    @Test fun `missing, future or stale fix falls back to receipt time`() {
        assertEquals(now, resolveEventTime(null, now))
        assertEquals(now, resolveEventTime(at("2026-11-20 09:45"), now))
        assertEquals(now, resolveEventTime(at("2026-11-20 08:30"), now))
        // Slightly in the future (clock skew within a minute) is clamped to receipt time.
        assertEquals(now, resolveEventTime(now.plusSeconds(20), now))
    }

    @Test fun `ssid matching`() {
        assertTrue(ssidMatches("Office", "\"Office\""))
        assertTrue(ssidMatches(" Office ", "Office"))
        assertFalse(ssidMatches("Office", "office"))
        assertFalse(ssidMatches("Office", null))
        assertFalse(ssidMatches(null, "Office"))
        assertFalse(ssidMatches("", ""))
    }

    @Test fun `wifi check decisions`() {
        assertEquals(WifiCheckDecision(ENTER, true), decideWifiCheck(true, null, false))
        assertEquals(WifiCheckDecision(ENTER, true), decideWifiCheck(true, ENTER, true))
        assertEquals(WifiCheckDecision(EXIT, true), decideWifiCheck(false, ENTER, true))
        assertEquals(WifiCheckDecision(EXIT, false), decideWifiCheck(false, ENTER, false))
        assertEquals(WifiCheckDecision(null, true), decideWifiCheck(false, EXIT, true))
        assertEquals(WifiCheckDecision(null, false), decideWifiCheck(false, null, false))
    }

    @Test fun `inside geofence only counts for today`() {
        val e = { s: String, t: EventType -> PresenceEvent(at(s), t, EventSource.GEOFENCE) }
        assertTrue(isInsideGeofenceToday(e("2026-11-20 08:50", ENTER), now, SYD))
        assertFalse(isInsideGeofenceToday(e("2026-11-19 08:50", ENTER), now, SYD))
        assertFalse(isInsideGeofenceToday(e("2026-11-20 08:50", EXIT), now, SYD))
        assertFalse(isInsideGeofenceToday(null, now, SYD))
    }

    /** End to end: Wi‑Fi heartbeats give a missing-exit visit a sensible end. */
    @Test fun `wifi heartbeats close a visit with a missing geofence exit`() {
        val events = listOf(
            enter("2026-11-19 09:00", EventSource.GEOFENCE),
            enter("2026-11-19 09:05", EventSource.WIFI),
            enter("2026-11-19 16:50", EventSource.WIFI),
        )
        val v = buildVisits(events, now, SYD).single()
        assertEquals(at("2026-11-19 16:50"), v.end)
        assertTrue(v.needsReview)
    }
}
