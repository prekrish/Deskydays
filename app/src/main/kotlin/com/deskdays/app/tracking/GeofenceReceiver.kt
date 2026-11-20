package com.deskdays.app.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.deskdays.app.DeskDaysApp
import com.deskdays.core.EventSource
import com.deskdays.core.EventType
import com.deskdays.core.resolveEventTime
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Receives geofence transitions from Play services and records them. */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val app = context.applicationContext as DeskDaysApp
        val container = app.container

        if (event.hasError()) {
            Log.w(TAG, "Geofence error ${event.errorCode}")
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                // Location was turned off and Play services dropped our geofence.
                container.trackingState.registeredSignature = null
                container.trackingState.setStatus("Waiting for location to be turned on", healthy = false)
                Tracking.sync(app, force = true)
            }
            return
        }

        val type = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> EventType.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> EventType.EXIT
            else -> return
        }
        if (event.triggeringGeofences?.none { it.requestId == GeofenceManager.OFFICE_ID } == true) return

        val receivedAt = Instant.now()
        val at = resolveEventTime(event.triggeringLocation?.time?.let(Instant::ofEpochMilli), receivedAt)

        val pending = goAsync()
        scope.launch {
            try {
                container.attendance.record(type, EventSource.GEOFENCE, at)
                val office = container.settings.settings.first().office
                if (office?.confirmWithWifi == true && office.wifiSsid != null) {
                    when (type) {
                        EventType.ENTER -> Tracking.startWifiChecks(app)
                        EventType.EXIT -> Tracking.checkWifiNow(app, exitAt = at)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record geofence event", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.deskdays.app.GEOFENCE_TRANSITION"
        private const val TAG = "GeofenceReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
