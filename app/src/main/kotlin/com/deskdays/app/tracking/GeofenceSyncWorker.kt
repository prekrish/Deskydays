package com.deskdays.app.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deskdays.app.DeskDaysApp
import com.deskdays.app.data.TrackingMode
import com.deskdays.app.location.LocationAccess
import com.deskdays.app.location.LocationPermissions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.GeofenceStatusCodes
import com.deskdays.core.EventSource
import com.deskdays.core.isInsideGeofenceToday
import java.time.Instant
import kotlinx.coroutines.flow.first

/** Registers or removes the office geofence so it matches settings and permissions. */
class GeofenceSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DeskDaysApp).container
        val state = container.trackingState
        val geofences = container.geofences
        val settings = container.settings.settings.first()
        val force = inputData.getBoolean(KEY_FORCE, false)
        val office = settings.office
        val access = LocationPermissions.current(applicationContext)

        val reasonOff = when {
            settings.trackingMode != TrackingMode.AUTOMATIC -> "Manual logging"
            office == null -> "No office set"
            access != LocationAccess.PRECISE_ALWAYS -> "Paused: location \"Allow all the time\" is off"
            else -> null
        }

        if (reasonOff != null || office == null) {
            if (state.registeredSignature != null || force) geofences.unregister()
            state.registeredSignature = null
            state.setStatus(reasonOff ?: "No office set", healthy = settings.trackingMode == TrackingMode.MANUAL)
            Tracking.stopWifiChecks(applicationContext)
            return Result.success()
        }

        // Wi‑Fi confirmation may have just been turned on while already at the office.
        if (office.confirmWithWifi && office.wifiSsid != null &&
            isInsideGeofenceToday(container.attendance.latest(EventSource.GEOFENCE), Instant.now(), settings.rules.zoneId)
        ) {
            Tracking.startWifiChecks(applicationContext)
        }

        val signature = GeofenceManager.signature(office)
        if (!force && state.registeredSignature == signature) return Result.success()

        return try {
            geofences.register(office)
            state.registeredSignature = signature
            state.setStatus("Watching your office", healthy = true)
            Result.success()
        } catch (e: ApiException) {
            state.registeredSignature = null
            when (e.statusCode) {
                GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> {
                    state.setStatus("Waiting for location to be turned on", healthy = false)
                    Result.retry()
                }
                GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION -> {
                    state.setStatus("Paused: location permission missing", healthy = false)
                    Result.failure()
                }
                else -> {
                    state.setStatus("Couldn't start tracking (code ${e.statusCode}). Retrying.", healthy = false)
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                }
            }
        } catch (e: SecurityException) {
            state.registeredSignature = null
            state.setStatus("Paused: location permission missing", healthy = false)
            Result.failure()
        }
    }

    companion object {
        const val KEY_FORCE = "force"
        private const val MAX_ATTEMPTS = 8
    }
}
