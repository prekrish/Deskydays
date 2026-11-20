package com.deskdays.app.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.deskdays.app.data.Office
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Registers the single office geofence with Google Play services. */
class GeofenceManager(private val context: Context) {
    private val client = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceReceiver::class.java).setAction(GeofenceReceiver.ACTION)
        // Play services fills in the transition details, so the intent must be mutable on 12+.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /** Caller must have checked precise + background location. Throws ApiException on failure. */
    @SuppressLint("MissingPermission")
    suspend fun register(office: Office) {
        val fence = Geofence.Builder()
            .setRequestId(OFFICE_ID)
            .setCircularRegion(office.latitude, office.longitude, office.radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            // Up to 2 minutes' delay saves battery; the stored time comes from the location fix.
            .setNotificationResponsiveness(120_000)
            .build()
        val request = GeofencingRequest.Builder()
            // If already inside when registering, report an ENTER. No initial EXIT: after a reboot
            // at home it would wrongly stretch a visit with a missed exit until now.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(fence)
            .build()
        client.addGeofences(request, pendingIntent).await()
    }

    suspend fun unregister() {
        runCatching { client.removeGeofences(pendingIntent).await() }
    }

    companion object {
        const val OFFICE_ID = "office"

        fun signature(office: Office) = "%.6f,%.6f,%d".format(java.util.Locale.ROOT, office.latitude, office.longitude, office.radiusMeters)
    }
}

/** Minimal Task → coroutine bridge (avoids an extra dependency). */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
