package com.deskdays.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** "2h 40m", "45m", "4h", "0m". */
fun formatMinutes(minutes: Long): String {
    val m = minutes.coerceAtLeast(0)
    val h = m / 60
    val r = m % 60
    return when {
        h == 0L -> "${r}m"
        r == 0L -> "${h}h"
        else -> "${h}h ${r}m"
    }
}

/** Everything the Today ring and tiles need. */
data class TodayProgress(
    val minutes: Long,
    val thresholdMinutes: Int,
    /** 0..1, for the ring. */
    val fraction: Float,
    /** Minutes still needed for today to count; 0 once it counts. */
    val remainingMinutes: Long,
    /** Start of the first visit today (clipped to midnight if it began the day before). */
    val arrivedAt: Instant?,
    val sources: Set<EventSource>,
    /** A visit is open right now. */
    val inOfficeNow: Boolean,
    val counts: Boolean,
)

fun todayProgress(day: DaySummary, thresholdMinutes: Int, zone: ZoneId): TodayProgress {
    val dayStart = day.date.atStartOfDay(zone).toInstant()
    val counts = day.status == DayStatus.OFFICE
    return TodayProgress(
        minutes = day.minutes,
        thresholdMinutes = thresholdMinutes,
        fraction = (day.minutes.toFloat() / thresholdMinutes).coerceIn(0f, 1f),
        remainingMinutes = if (counts) 0 else (thresholdMinutes - day.minutes).coerceAtLeast(0),
        arrivedAt = day.visits.minOfOrNull { it.start }?.let { maxOf(it, dayStart) },
        sources = day.visits.flatMapTo(sortedSetOf()) { it.sources },
        inOfficeNow = day.visits.any { it.ongoing },
        counts = counts,
    )
}

/** The "go in N of the next M days" message on Today and Stats. */
sealed interface MonthNudge {
    data object TargetMet : MonthNudge
    data class OnTrack(val needed: Int, val remaining: Int) : MonthNudge
    data class OutOfReach(val needed: Int, val remaining: Int) : MonthNudge
    data object NoEligibleDays : MonthNudge
}

fun monthNudge(stats: MonthStats): MonthNudge = when {
    stats.eligibleDays == 0 -> MonthNudge.NoEligibleDays
    stats.targetMet -> MonthNudge.TargetMet
    stats.stillAchievable -> MonthNudge.OnTrack(stats.stillNeeded, stats.workingDaysRemaining)
    else -> MonthNudge.OutOfReach(stats.stillNeeded, stats.workingDaysRemaining)
}

/** Breaks ignored between merged segments, e.g. "lunch break 22 min". */
fun Visit.breakMinutes(): List<Long> = mergedGaps.map(Duration::toMinutes)
