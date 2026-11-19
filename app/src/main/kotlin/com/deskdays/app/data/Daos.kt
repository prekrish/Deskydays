package com.deskdays.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PresenceEventDao {
    @Insert
    suspend fun insert(event: PresenceEventEntity): Long

    @Query("SELECT * FROM presence_events WHERE timestampMillis >= :fromMillis AND timestampMillis < :toMillis ORDER BY timestampMillis")
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<PresenceEventEntity>>

    @Query("SELECT * FROM presence_events WHERE timestampMillis >= :fromMillis AND timestampMillis < :toMillis ORDER BY timestampMillis")
    suspend fun between(fromMillis: Long, toMillis: Long): List<PresenceEventEntity>

    @Query("DELETE FROM presence_events WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DayOverrideDao {
    @Upsert
    suspend fun upsert(override: DayOverrideEntity)

    @Query("DELETE FROM day_overrides WHERE date = :date")
    suspend fun clear(date: String)

    @Query("SELECT * FROM day_overrides WHERE date >= :from AND date <= :to")
    fun observeBetween(from: String, to: String): Flow<List<DayOverrideEntity>>

    @Query("SELECT * FROM day_overrides WHERE date >= :from AND date <= :to")
    suspend fun between(from: String, to: String): List<DayOverrideEntity>
}
