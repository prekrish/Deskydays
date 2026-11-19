package com.deskdays.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What kind of presence signal was recorded. */
enum class EventType { ENTER, EXIT }

/** Where a presence signal came from. MANUAL events are authoritative. */
enum class EventSource { GEOFENCE, WIFI, MANUAL }

/** A raw presence signal, exactly as recorded. Never mutated after it is stored. */
data class PresenceEvent(
    val timestamp: Instant,
    val type: EventType,
    val source: EventSource,
)

/**
 * A continuous stretch of time on site.
 *
 * @property needsReview true when the visit had no matching EXIT and was closed at the last
 *   confirmed in-office signal instead. The UI should ask the user to fix these.
 * @property ongoing true when the visit is still open right now (its [end] is "now").
 * @property segments the raw visits this one was built from; more than one means short exits
 *   were merged. Empty for a raw (unmerged) visit.
 */
data class Visit(
    val start: Instant,
    val end: Instant,
    val sources: Set<EventSource>,
    val needsReview: Boolean = false,
    val ongoing: Boolean = false,
    val segments: List<Visit> = emptyList(),
) {
    init {
        require(!end.isBefore(start)) { "Visit end $end is before start $start" }
    }

    val duration: Duration get() = Duration.between(start, end)

    /** Gaps between merged segments (e.g. a lunch break that was ignored). */
    val mergedGaps: List<Duration>
        get() = segments.zipWithNext { a, b -> Duration.between(a.end, b.start).coerceAtLeast(Duration.ZERO) }
}

private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

/** How a single calendar day is classified. */
enum class DayStatus {
    /** At least [CountingRules.thresholdMinutes] on site, or marked as office manually. */
    OFFICE,

    /** A past working day that didn't reach the threshold. */
    REMOTE,

    /** Marked as leave by the user. Not counted as eligible. */
    LEAVE,

    /** A public holiday (or one the user marked). Not counted as eligible. */
    HOLIDAY,

    /** Not one of the configured working days (e.g. a weekend). */
    NON_WORKING,

    /** Today (not yet over the threshold) or a future working day. */
    PENDING,

    /** Before the user started tracking. Not counted as eligible. */
    UNTRACKED,
}

/** Statuses a user is allowed to set manually for a day. */
val MANUAL_STATUSES: Set<DayStatus> = setOf(DayStatus.OFFICE, DayStatus.REMOTE, DayStatus.LEAVE, DayStatus.HOLIDAY)

/** Every tunable counting rule. All of these are user-editable in settings. */
data class CountingRules(
    val thresholdMinutes: Int = 240,
    val mergeGapMinutes: Int = 15,
    val targetPercent: Int = 50,
    val workingDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    ),
    val zoneId: ZoneId = ZoneId.of("Australia/Sydney"),
    /** If true, an office day on a non-working day (e.g. Saturday) still adds to office days. */
    val countNonWorkingDayAttendance: Boolean = true,
) {
    init {
        require(thresholdMinutes in 1..(24 * 60)) { "thresholdMinutes must be 1..1440" }
        require(mergeGapMinutes in 0..(24 * 60)) { "mergeGapMinutes must be 0..1440" }
        require(targetPercent in 0..100) { "targetPercent must be 0..100" }
        require(workingDays.isNotEmpty()) { "At least one working day is required" }
    }
}

/** Everything known about one day. */
data class DaySummary(
    val date: LocalDate,
    val status: DayStatus,
    /** Minutes on site, after merging, clipped to this local day. */
    val minutes: Long,
    /** Merged visits that overlap this day (not clipped). */
    val visits: List<Visit>,
    val isWorkingDay: Boolean,
    val holidayName: String?,
    val manuallySet: Boolean,
) {
    val needsReview: Boolean get() = visits.any { it.needsReview }

    /** Whether this day counts toward the monthly "days that count" figure. */
    val isEligible: Boolean
        get() = isWorkingDay && status != DayStatus.LEAVE && status != DayStatus.HOLIDAY &&
            status != DayStatus.UNTRACKED
}
