package com.deskdays.app.ui.components

import com.deskdays.core.DayStatus
import com.deskdays.core.DaySummary
import com.deskdays.core.EventSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** "9:12 am" */
fun formatTime(instant: Instant, zone: ZoneId): String = TIME.format(instant.atZone(zone)).lowercase(Locale.ENGLISH)

fun sourceLabel(source: EventSource): String = when (source) {
    EventSource.GEOFENCE -> "GPS"
    EventSource.WIFI -> "Wi‑Fi"
    EventSource.MANUAL -> "Manual"
}

/** "GPS + Wi‑Fi", or "—" when nothing was detected. */
fun sourcesLabel(sources: Set<EventSource>): String =
    if (sources.isEmpty()) "—" else EventSource.entries.filter { it in sources }.joinToString(" + ") { sourceLabel(it) }

fun statusLabel(day: DaySummary, isToday: Boolean): String = when (day.status) {
    DayStatus.OFFICE -> "In office"
    DayStatus.REMOTE -> "Remote"
    DayStatus.LEAVE -> "Leave"
    DayStatus.HOLIDAY -> day.holidayName ?: "Holiday"
    DayStatus.NON_WORKING -> "Not a work day"
    DayStatus.PENDING -> if (isToday) "Today (in progress)" else "Upcoming"
    DayStatus.UNTRACKED -> "Before tracking started"
}
