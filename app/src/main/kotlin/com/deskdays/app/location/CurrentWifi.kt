package com.deskdays.app.location

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class WifiNetwork(val ssid: String, val bssid: String?)

/**
 * The Wi‑Fi network the phone is connected to right now, or null. Android only reveals the
 * network name to apps holding precise location permission.
 */
suspend fun currentWifi(context: Context): WifiNetwork? {
    if (!LocationPermissions.hasPrecise(context)) return null
    val info: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        withTimeoutOrNull(3_000) { Api31.wifiInfo(context) }
    } else {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        wifi?.connectionInfo
    }
    return info?.toNetwork()
}

private fun WifiInfo.toNetwork(): WifiNetwork? {
    val name = ssid?.removeSurrounding("\"")
    if (name.isNullOrBlank() || name == WifiManager.UNKNOWN_SSID.removeSurrounding("\"") || name == "<unknown ssid>") {
        return null
    }
    val b = bssid?.takeUnless { it == "02:00:00:00:00:00" }
    return WifiNetwork(name, b)
}

@RequiresApi(Build.VERSION_CODES.S)
private object Api31 {
    /** On 12+, the SSID is only included via a network callback with FLAG_INCLUDE_LOCATION_INFO. */
    suspend fun wifiInfo(context: Context): WifiInfo? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        return suspendCancellableCoroutine { cont ->
            val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val info = caps.transportInfo as? WifiInfo ?: return
                    runCatching { cm.unregisterNetworkCallback(this) }
                    if (cont.isActive) cont.resume(info)
                }
            }
            cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(callback) } }
            // With no Wi‑Fi connected, no callback fires and the caller's timeout returns null.
            cm.registerNetworkCallback(request, callback)
        }
    }
}
