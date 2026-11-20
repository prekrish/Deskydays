package com.deskdays.app.data

import com.deskdays.core.DayStatus
import com.deskdays.core.EventSource
import com.deskdays.core.EventType
import com.deskdays.core.MANUAL_STATUSES
import com.deskdays.core.PresenceEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Raw inputs the :core calculator needs for a date range. */
data class AttendanceInputs(
    val events: List<PresenceEvent>,
    val overrides: Map<LocalDate, DayStatus>,
)

class AttendanceRepository(
    private val events: PresenceEventDao,
    private val overrides: DayOverrideDao,
) {
    suspend fun record(type: EventType, source: EventSource, at: Instant = Instant.now()) {
        events.insert(PresenceEventEntity(timestampMillis = at.toEpochMilli(), type = type.name, source = source.name))
    }

    suspend fun latest(source: EventSource): PresenceEvent? = events.latestBySource(source.name)?.toCore()

    /** Newest first, for the detection log. */
    fun observeRecent(limit: Int = 100): Flow<List<PresenceEvent>> =
        events.observeRecent(limit).map { list -> list.mapNotNull { it.toCore() } }

    suspend fun setOverride(date: LocalDate, status: DayStatus, note: String? = null) {
        require(status in MANUAL_STATUSES)
        overrides.upsert(DayOverrideEntity(date.toString(), status.name, note))
    }

    suspend fun clearOverride(date: LocalDate) = overrides.clear(date.toString())

    /**
     * Inputs for [from]..[toInclusive]. Events are loaded from one day earlier so a visit that
     * started the previous evening is still seen.
     */
    fun observe(from: LocalDate, toInclusive: LocalDate, zone: ZoneId): Flow<AttendanceInputs> {
        val fromMillis = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = toInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return combine(
            events.observeBetween(fromMillis, toMillis),
            overrides.observeBetween(from.toString(), toInclusive.toString()),
        ) { e, o -> AttendanceInputs(e.mapNotNull { it.toCore() }, o.toCoreMap()) }
    }
}

private fun PresenceEventEntity.toCore(): PresenceEvent? {
    val t = runCatching { EventType.valueOf(type) }.getOrNull() ?: return null
    val s = runCatching { EventSource.valueOf(source) }.getOrNull() ?: return null
    return PresenceEvent(Instant.ofEpochMilli(timestampMillis), t, s)
}

private fun List<DayOverrideEntity>.toCoreMap(): Map<LocalDate, DayStatus> = mapNotNull { o ->
    val status = runCatching { DayStatus.valueOf(o.status) }.getOrNull()?.takeIf { it in MANUAL_STATUSES }
    val date = runCatching { LocalDate.parse(o.date) }.getOrNull()
    if (status != null && date != null) date to status else null
}.toMap()
