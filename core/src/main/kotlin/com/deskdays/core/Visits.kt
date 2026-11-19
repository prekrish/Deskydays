package com.deskdays.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns raw presence events into visits.
 *
 * Rules:
 * - A visit opens on the first ENTER from any source and stays open while at least one source
 *   still reports being inside. It closes when the last inside source sends EXIT.
 * - A MANUAL EXIT closes the visit regardless of other sources.
 * - Repeated ENTERs while inside, and EXITs while outside, are treated as confirmations / noise.
 * - Every event received while a visit is open is a "confirmed in-office signal".
 * - A visit must close on the local day it started. If the day ends without an EXIT (the next
 *   event is on a later day, or there are no more events and the day is over), the visit is
 *   closed at the last confirmed signal and flagged [Visit.needsReview].
 * - If the visit started today and is still open, it ends at [now] and is marked [Visit.ongoing].
 */
fun buildVisits(events: List<PresenceEvent>, now: Instant, zone: ZoneId): List<Visit> {
    val sorted = events.sortedBy { it.timestamp } // stable: keeps input order on ties
    val visits = mutableListOf<Visit>()

    var start: Instant? = null
    var lastSignal: Instant = Instant.EPOCH
    val inside = mutableSetOf<EventSource>()
    val sources = mutableSetOf<EventSource>()

    fun dayOf(i: Instant): LocalDate = i.atZone(zone).toLocalDate()

    fun close(end: Instant, needsReview: Boolean, ongoing: Boolean = false) {
        visits += Visit(start!!, end, sources.toSet(), needsReview = needsReview, ongoing = ongoing)
        start = null
        inside.clear()
        sources.clear()
    }

    for (e in sorted) {
        val open = start
        if (open != null && dayOf(e.timestamp) != dayOf(open)) {
            // The day ended without an EXIT.
            close(lastSignal, needsReview = true)
        }
        when (e.type) {
            EventType.ENTER -> {
                if (start == null) start = e.timestamp
                inside += e.source
                sources += e.source
                lastSignal = e.timestamp
            }
            EventType.EXIT -> {
                if (start == null) continue // exit while outside: ignore
                lastSignal = e.timestamp
                if (e.source == EventSource.MANUAL) inside.clear() else inside -= e.source
                if (inside.isEmpty()) close(e.timestamp, needsReview = false)
            }
        }
    }

    val open = start
    if (open != null) {
        if (dayOf(open) == dayOf(now) && !now.isBefore(open)) {
            close(now, needsReview = false, ongoing = true)
        } else {
            close(lastSignal, needsReview = true)
        }
    }
    return visits
}

/**
 * Merges visits separated by a gap shorter than [mergeGapMinutes]. The gap is counted as time
 * on site (a short exit is ignored). Overlapping visits are always merged.
 */
fun mergeVisits(visits: List<Visit>, mergeGapMinutes: Int): List<Visit> {
    if (visits.isEmpty()) return emptyList()
    val maxGap = Duration.ofMinutes(mergeGapMinutes.toLong())
    val result = mutableListOf<Visit>()
    val group = mutableListOf<Visit>()

    fun flush() {
        if (group.isEmpty()) return
        result += if (group.size == 1) {
            group.single()
        } else {
            Visit(
                start = group.first().start,
                end = group.maxOf { it.end },
                sources = group.flatMapTo(mutableSetOf()) { it.sources },
                needsReview = group.any { it.needsReview },
                ongoing = group.any { it.ongoing },
                segments = group.flatMap { it.segments.ifEmpty { listOf(it) } },
            )
        }
        group.clear()
    }

    for (v in visits.sortedBy { it.start }) {
        if (group.isNotEmpty()) {
            val groupEnd = group.maxOf { it.end }
            val gap = Duration.between(groupEnd, v.start)
            if (gap >= maxGap) flush()
        }
        group += v
    }
    flush()
    return result
}

/** Minutes of [visits] that fall inside [date] in [zone]. Visits must not overlap (merge first). */
fun minutesOnDay(visits: List<Visit>, date: LocalDate, zone: ZoneId): Long {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    var seconds = 0L
    for (v in visits) {
        val s = maxOf(v.start, dayStart)
        val e = minOf(v.end, dayEnd)
        if (e.isAfter(s)) seconds += Duration.between(s, e).seconds
    }
    return seconds / 60
}

/** Merged visits that overlap [date] in [zone]. */
fun visitsOnDay(visits: List<Visit>, date: LocalDate, zone: ZoneId): List<Visit> {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    return visits.filter { v ->
        v.start.isBefore(dayEnd) && (v.end.isAfter(dayStart) || (v.start == v.end && !v.start.isBefore(dayStart)))
    }
}
