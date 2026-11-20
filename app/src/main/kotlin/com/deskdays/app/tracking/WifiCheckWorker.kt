package com.deskdays.app.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deskdays.app.DeskDaysApp
import com.deskdays.app.location.currentWifi
import com.deskdays.core.EventSource
import com.deskdays.core.decideWifiCheck
import com.deskdays.core.isInsideGeofenceToday
import com.deskdays.core.ssidMatches
import java.time.Instant
import kotlinx.coroutines.flow.first

/** Checks whether the phone is on the office Wi‑Fi and records WIFI ENTER/EXIT signals. */
class WifiCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DeskDaysApp).container
        val settings = container.settings.settings.first()
        val office = settings.office
        if (office == null || !office.confirmWithWifi || office.wifiSsid == null) {
            Tracking.stopWifiChecks(applicationContext)
            return Result.success()
        }

        val now = Instant.now()
        val repo = container.attendance
        val onOffice = ssidMatches(office.wifiSsid, currentWifi(applicationContext)?.ssid)
        val decision = decideWifiCheck(
            onOfficeWifi = onOffice,
            lastWifiEvent = repo.latest(EventSource.WIFI)?.type,
            insideGeofenceToday = isInsideGeofenceToday(repo.latest(EventSource.GEOFENCE), now, settings.rules.zoneId),
        )

        decision.record?.let { type ->
            // Leaving: use the geofence exit time when this check was triggered by one, so the
            // visit doesn't stretch to whenever the check happened to run.
            val exitAt = inputData.getLong(KEY_EXIT_AT, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            val at = if (type == com.deskdays.core.EventType.EXIT && exitAt != null) exitAt else now
            repo.record(type, EventSource.WIFI, at)
        }
        if (!decision.keepChecking) Tracking.stopWifiChecks(applicationContext)
        return Result.success()
    }

    companion object {
        const val KEY_EXIT_AT = "exit_at"
    }
}
