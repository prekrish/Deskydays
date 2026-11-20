package com.deskdays.app.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Address ⇄ coordinates using the platform Geocoder (no API key needed). */
class AddressLookup(private val context: Context) {
    val isAvailable: Boolean get() = Geocoder.isPresent()

    private fun geocoder() = Geocoder(context, Locale.getDefault())

    suspend fun find(query: String): LatLng? {
        if (!isAvailable || query.isBlank()) return null
        val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.byName(geocoder(), query)
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder().getFromLocationName(query, 1) }.getOrNull()
            }
        }
        return results?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
    }

    suspend fun describe(point: LatLng): String? {
        if (!isAvailable) return null
        val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.byLocation(geocoder(), point)
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder().getFromLocation(point.latitude, point.longitude, 1) }.getOrNull()
            }
        }
        return results?.firstOrNull()?.format()
    }

    /** Kept in its own object so older devices never load the API 33 listener class. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {
        suspend fun byName(g: Geocoder, query: String): List<Address>? =
            suspendCancellableCoroutine { cont ->
                g.getFromLocationName(query, 1, listener { if (cont.isActive) cont.resume(it) })
            }

        suspend fun byLocation(g: Geocoder, p: LatLng): List<Address>? =
            suspendCancellableCoroutine { cont ->
                g.getFromLocation(p.latitude, p.longitude, 1, listener { if (cont.isActive) cont.resume(it) })
            }

        private fun listener(done: (List<Address>?) -> Unit) = object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) = done(addresses)
            override fun onError(errorMessage: String?) = done(null)
        }
    }
}

private fun Address.format(): String =
    (0..maxAddressLineIndex).mapNotNull { getAddressLine(it) }.joinToString(", ").ifBlank {
        listOfNotNull(thoroughfare, locality, adminArea).joinToString(", ")
    }
