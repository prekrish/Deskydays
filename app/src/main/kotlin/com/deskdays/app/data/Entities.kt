package com.deskdays.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Raw presence signal. Append-only: visits and day totals are always recomputed from these by
 * the :core module, so changing a counting rule re-counts history correctly.
 */
@Entity(tableName = "presence_events", indices = [Index("timestampMillis")])
data class PresenceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis, UTC. */
    val timestampMillis: Long,
    /** [com.deskdays.core.EventType] name. */
    val type: String,
    /** [com.deskdays.core.EventSource] name. */
    val source: String,
)

/** A manual day status (office / remote / leave / holiday). Always wins over computed status. */
@Entity(tableName = "day_overrides")
data class DayOverrideEntity(
    /** ISO local date in the office time zone, e.g. 2026-11-19. */
    @PrimaryKey val date: String,
    /** [com.deskdays.core.DayStatus] name; one of MANUAL_STATUSES. */
    val status: String,
    val note: String? = null,
)
