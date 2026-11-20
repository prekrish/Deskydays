package com.deskdays.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Picks the timestamp to store for a geofence transition. The location fix that triggered it is
 * usually more accurate than when the broadcast arrived (delivery can lag by minutes), but a fix
 * from the future or older than [maxFixAge] isn't trusted.
 */
fun resolveEventTime(
    fixTime: Instant?,
    receivedAt: Instant,
    maxFixAge: Duration = Duration.ofMinutes(30),
): Instant {
    if (fixTime == null) return receivedAt
    if (fixTime.isAfter(receivedAt.plusSeconds(60))) return receivedAt
    if (fixTime.isBefore(receivedAt.minus(maxFixAge))) return receivedAt
    return minOf(fixTime, receivedAt)
}

/**
 * Whether the current Wi‑Fi is the saved office network. Matches on network name only, because
 * offices usually have many access points (BSSIDs) under one name.
 */
fun ssidMatches(saved: String?, current: String?): Boolean {
    val a = saved?.trim()?.removeSurrounding("\"")
    val b = current?.trim()?.removeSurrounding("\"")
    return !a.isNullOrEmpty() && a == b
}

/** What a periodic Wi‑Fi check should do. */
data class WifiCheckDecision(
    /** Event to record with source WIFI, or null for none. */
    val record: EventType?,
    /** Whether to keep checking periodically. */
    val keepChecking: Boolean,
)

/**
 * One Wi‑Fi presence check.
 *
 * - On the office network: record a WIFI ENTER. Repeated ENTERs are deliberate: each is a
 *   "confirmed in-office signal", so a visit with a missing EXIT closes at the last one.
 * - Off the network after being on it: record a WIFI EXIT.
 * - Keep checking while on the network or while the geofence says we're inside today.
 */
fun decideWifiCheck(onOfficeWifi: Boolean, lastWifiEvent: EventType?, insideGeofenceToday: Boolean): WifiCheckDecision =
    when {
        onOfficeWifi -> WifiCheckDecision(EventType.ENTER, keepChecking = true)
        lastWifiEvent == EventType.ENTER -> WifiCheckDecision(EventType.EXIT, keepChecking = insideGeofenceToday)
        else -> WifiCheckDecision(null, keepChecking = insideGeofenceToday)
    }

/** True if [event] is a GEOFENCE ENTER that happened on the same local day as [now]. */
fun isInsideGeofenceToday(lastGeofenceEvent: PresenceEvent?, now: Instant, zone: ZoneId): Boolean =
    lastGeofenceEvent != null &&
        lastGeofenceEvent.type == EventType.ENTER &&
        lastGeofenceEvent.timestamp.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
