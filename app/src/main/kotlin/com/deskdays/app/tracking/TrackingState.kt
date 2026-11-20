package com.deskdays.app.tracking

import android.content.Context
import androidx.core.content.edit
import java.time.Instant

/**
 * Device-local tracking state. Kept in SharedPreferences on purpose: backup rules only include
 * the database and DataStore, so after a restore to a new phone the geofence is re-registered
 * instead of being wrongly treated as already active.
 */
class TrackingState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)

    /** Identifies the geofence currently registered (lat,lng,radius), or null if none. */
    var registeredSignature: String?
        get() = prefs.getString(KEY_SIGNATURE, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_SIGNATURE) else putString(KEY_SIGNATURE, value) }

    data class Status(val text: String, val at: Instant, val healthy: Boolean)

    val status: Status?
        get() {
            val text = prefs.getString(KEY_STATUS, null) ?: return null
            return Status(text, Instant.ofEpochMilli(prefs.getLong(KEY_STATUS_AT, 0)), prefs.getBoolean(KEY_HEALTHY, false))
        }

    fun setStatus(text: String, healthy: Boolean) = prefs.edit {
        putString(KEY_STATUS, text)
        putLong(KEY_STATUS_AT, System.currentTimeMillis())
        putBoolean(KEY_HEALTHY, healthy)
    }

    private companion object {
        const val KEY_SIGNATURE = "geofence_signature"
        const val KEY_STATUS = "status"
        const val KEY_STATUS_AT = "status_at"
        const val KEY_HEALTHY = "status_healthy"
    }
}
