package com.deskdays.app.data

import android.content.Context
import androidx.room.Room
import com.deskdays.app.tracking.GeofenceManager
import com.deskdays.app.tracking.Tracking
import com.deskdays.app.tracking.TrackingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual dependency container. Swap for Hilt if the graph grows. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: DeskDaysDatabase =
        Room.databaseBuilder(app, DeskDaysDatabase::class.java, DeskDaysDatabase.NAME).build()

    val settings = SettingsRepository(app)

    val attendance = AttendanceRepository(database.presenceEventDao(), database.dayOverrideDao())

    val feed = AttendanceFeed(settings, attendance)

    val trackingState = TrackingState(app)

    val geofences = GeofenceManager(app)

    /**
     * Keeps the geofence in step with settings: whenever tracking mode, office location or
     * radius changes (and once per process start), queue a sync. The sync is a no-op when the
     * registered geofence already matches, so this doesn't re-register on every cold start.
     */
    fun startTrackingSync() {
        scope.launch {
            settings.settings
                .map { s -> Triple(s.trackingMode, s.office?.let { "${it.latitude},${it.longitude},${it.radiusMeters}" }, s.office?.confirmWithWifi) }
                .distinctUntilChanged()
                .collect { Tracking.sync(app) }
        }
        Tracking.scheduleDailySync(app)
    }
}
