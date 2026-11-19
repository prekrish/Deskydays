package com.deskdays.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PresenceEventEntity::class, DayOverrideEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DeskDaysDatabase : RoomDatabase() {
    abstract fun presenceEventDao(): PresenceEventDao
    abstract fun dayOverrideDao(): DayOverrideDao

    companion object {
        const val NAME = "deskdays.db"
    }
}
