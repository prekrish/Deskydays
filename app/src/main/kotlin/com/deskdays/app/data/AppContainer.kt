package com.deskdays.app.data

import android.content.Context
import androidx.room.Room

/** Manual dependency container. Swap for Hilt if the graph grows. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val database: DeskDaysDatabase =
        Room.databaseBuilder(app, DeskDaysDatabase::class.java, DeskDaysDatabase.NAME).build()

    val settings = SettingsRepository(app)

    val attendance = AttendanceRepository(database.presenceEventDao(), database.dayOverrideDao())
}
