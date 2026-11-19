package com.deskdays.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Monthly numbers shown on Today and Stats. */
data class MonthStats(
    val month: YearMonth,
    /** Configured working days in the month (e.g. Mon–Fri), before subtractions. */
    val workingDays: Int,
    val leaveDays: Int,
    val holidayDays: Int,
    /** Working days before tracking started; excluded like leave. */
    val untrackedDays: Int,
    /** Days that count: working days − leave − holidays − untracked. */
    val eligibleDays: Int,
    /** ceil(eligible × target%). */
    val targetDays: Int,
    val officeDays: Int,
    /** officeDays ÷ eligibleDays, or 0 when there are no eligible days. May exceed 1. */
    val fraction: Double,
    /** max(0, target − office days). */
    val stillNeeded: Int,
    /** Eligible days still to come (today included if it isn't an office day yet). */
    val workingDaysRemaining: Int,
) {
    val targetMet: Boolean get() = officeDays >= targetDays
    val stillAchievable: Boolean get() = stillNeeded <= workingDaysRemaining
    val percent: Int get() = Math.round(fraction * 100).toInt()
}

/**
 * Pure counting engine. Give it the raw data, get day and month summaries back.
 *
 * @param overrides manual day statuses (only [MANUAL_STATUSES]); these always win.
 * @param trackedFrom days before this are [DayStatus.UNTRACKED] and don't count. Null = no limit.
 */
class AttendanceCalculator(
    private val rules: CountingRules = CountingRules(),
    private val holidays: HolidayCalendar = NswHolidays,
) {
    private val zone get() = rules.zoneId

    fun days(
        from: LocalDate,
        toInclusive: LocalDate,
        events: List<PresenceEvent>,
        overrides: Map<LocalDate, DayStatus>,
        now: Instant,
        trackedFrom: LocalDate? = null,
    ): List<DaySummary> {
        require(!toInclusive.isBefore(from)) { "toInclusive must not be before from" }
        overrides.values.forEach { require(it in MANUAL_STATUSES) { "$it can't be set manually" } }

        val merged = mergeVisits(buildVisits(events, now, zone), rules.mergeGapMinutes)
        val today = now.atZone(zone).toLocalDate()
        val result = ArrayList<DaySummary>()
        var d = from
        while (!d.isAfter(toInclusive)) {
            result += summarize(d, merged, overrides[d], today, trackedFrom)
            d = d.plusDays(1)
        }
        return result
    }

    fun month(
        month: YearMonth,
        events: List<PresenceEvent>,
        overrides: Map<LocalDate, DayStatus>,
        now: Instant,
        trackedFrom: LocalDate? = null,
    ): MonthStats = monthFromDays(
        month,
        days(month.atDay(1), month.atEndOfMonth(), events, overrides, now, trackedFrom),
        now.atZone(zone).toLocalDate(),
    )

    /** Last [count] months ending with [endMonth], oldest first (for the 6-month chart). */
    fun history(
        endMonth: YearMonth,
        count: Int,
        events: List<PresenceEvent>,
        overrides: Map<LocalDate, DayStatus>,
        now: Instant,
        trackedFrom: LocalDate? = null,
    ): List<MonthStats> {
        require(count > 0)
        val first = endMonth.minusMonths((count - 1).toLong())
        val all = days(first.atDay(1), endMonth.atEndOfMonth(), events, overrides, now, trackedFrom)
        val today = now.atZone(zone).toLocalDate()
        return all.groupBy { YearMonth.from(it.date) }.map { (m, ds) -> monthFromDays(m, ds, today) }
    }

    fun monthFromDays(month: YearMonth, days: List<DaySummary>, today: LocalDate): MonthStats {
        val inMonth = days.filter { YearMonth.from(it.date) == month }
        val working = inMonth.filter { it.isWorkingDay }
        val leave = working.count { it.status == DayStatus.LEAVE }
        val holiday = working.count { it.status == DayStatus.HOLIDAY }
        val untracked = working.count { it.status == DayStatus.UNTRACKED }
        val eligible = working.size - leave - holiday - untracked
        val target = ceilDiv(eligible * rules.targetPercent, 100)
        val office = inMonth.count {
            it.status == DayStatus.OFFICE && (it.isWorkingDay || rules.countNonWorkingDayAttendance)
        }
        val remaining = inMonth.count {
            it.isEligible && (it.date.isAfter(today) || (it.date == today && it.status != DayStatus.OFFICE))
        }
        return MonthStats(
            month = month,
            workingDays = working.size,
            leaveDays = leave,
            holidayDays = holiday,
            untrackedDays = untracked,
            eligibleDays = eligible,
            targetDays = target,
            officeDays = office,
            fraction = if (eligible == 0) 0.0 else office.toDouble() / eligible,
            stillNeeded = maxOf(0, target - office),
            workingDaysRemaining = remaining,
        )
    }

    private fun summarize(
        date: LocalDate,
        merged: List<Visit>,
        override: DayStatus?,
        today: LocalDate,
        trackedFrom: LocalDate?,
    ): DaySummary {
        val minutes = minutesOnDay(merged, date, zone)
        val isWorking = date.dayOfWeek in rules.workingDays
        val holidayName = holidays.holidays(date.year)[date]
        val status = override ?: when {
            minutes >= rules.thresholdMinutes -> DayStatus.OFFICE
            trackedFrom != null && date.isBefore(trackedFrom) -> DayStatus.UNTRACKED
            holidayName != null -> DayStatus.HOLIDAY
            !isWorking -> DayStatus.NON_WORKING
            !date.isBefore(today) -> DayStatus.PENDING
            else -> DayStatus.REMOTE
        }
        return DaySummary(
            date = date,
            status = status,
            minutes = minutes,
            visits = visitsOnDay(merged, date, zone),
            isWorkingDay = isWorking,
            holidayName = holidayName,
            manuallySet = override != null,
        )
    }
}

internal fun ceilDiv(a: Int, b: Int): Int = Math.floorDiv(a + b - 1, b)
