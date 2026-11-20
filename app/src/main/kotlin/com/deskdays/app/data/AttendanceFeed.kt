package com.deskdays.app.data

import com.deskdays.core.AttendanceCalculator
import com.deskdays.core.DaySummary
import com.deskdays.core.NswHolidays
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Computed days for a date range, plus what was used to compute them. */
data class Snapshot(
    val settings: Settings,
    val calculator: AttendanceCalculator,
    val now: Instant,
    val today: LocalDate,
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DaySummary>,
) {
    val zone: ZoneId get() = settings.rules.zoneId
    fun day(date: LocalDate): DaySummary? = days.firstOrNull { it.date == date }
}

/**
 * Live attendance for the UI: recomputes from raw events whenever settings or stored data change,
 * and once a minute so an ongoing visit keeps counting up.
 */
class AttendanceFeed(
    private val settings: SettingsRepository,
    private val repo: AttendanceRepository,
) {
    /** [range] gives the dates to compute for; it may depend on settings (e.g. the time zone). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(range: (Settings) -> Flow<Pair<LocalDate, LocalDate>>): Flow<Snapshot> =
        settings.settings.flatMapLatest { s ->
            val zone = s.rules.zoneId
            val calc = AttendanceCalculator(s.rules, NswHolidays)
            range(s).distinctUntilChanged().flatMapLatest { (from, to) ->
                combine(repo.observe(from, to, zone), minuteTicker()) { inputs, now ->
                    Snapshot(
                        settings = s,
                        calculator = calc,
                        now = now,
                        today = now.atZone(zone).toLocalDate(),
                        from = from,
                        to = to,
                        days = calc.days(from, to, inputs.events, inputs.overrides, now, s.trackedFrom),
                    )
                }
            }
        }.flowOn(Dispatchers.Default)

    /** One-off computation, e.g. for CSV export. */
    suspend fun compute(from: LocalDate, to: LocalDate): Snapshot = observe { flowOf(from to to) }.first()

    /** Today's date in [zone], re-emitted only when it changes. */
    fun todayIn(zone: ZoneId): Flow<LocalDate> = minuteTicker().map { it.atZone(zone).toLocalDate() }.distinctUntilChanged()
}

private fun <T> flowOf(value: T): Flow<T> = flow { emit(value) }

/** Emits now, then at the start of every minute. */
fun minuteTicker(): Flow<Instant> = flow {
    while (true) {
        val now = Instant.now()
        emit(now)
        delay(60_000 - now.toEpochMilli() % 60_000 + 50)
    }
}
