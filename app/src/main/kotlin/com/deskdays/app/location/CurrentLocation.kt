package com.deskdays.app.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class LatLng(val latitude: Double, val longitude: Double)

/** One fresh location fix, or null if unavailable. Caller must hold a location permission. */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): LatLng? {
    if (LocationPermissions.current(context) == LocationAccess.NONE) return null
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancel = CancellationTokenSource()
    return suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel.cancel() }
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
            .addOnSuccessListener { loc -> cont.resume(loc?.let { LatLng(it.latitude, it.longitude) }) }
            .addOnFailureListener { cont.resume(null) }
            .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }
}
