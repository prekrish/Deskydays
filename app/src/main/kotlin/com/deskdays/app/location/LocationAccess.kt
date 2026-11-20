package com.deskdays.app.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** What location access the app currently has. Geofencing needs [PRECISE_ALWAYS]. */
enum class LocationAccess {
    NONE,

    /** "Approximate" only. Geofences need precise location. */
    APPROXIMATE_ONLY,

    /** Precise, but only while the app is in use. */
    PRECISE_WHILE_IN_USE,

    /** Precise and "Allow all the time". */
    PRECISE_ALWAYS,
}

object LocationPermissions {
    val FOREGROUND = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Android 10+ has a separate background permission; before that, foreground implies it. */
    val hasSeparateBackgroundPermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * On Android 11+ the background request can't show a dialog; the system opens the app's
     * location settings page instead, and the user must pick "Allow all the time" there.
     */
    val backgroundOpensSettings: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasPrecise(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun current(context: Context): LocationAccess {
        val fine = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val background = !hasSeparateBackgroundPermission ||
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        return when {
            fine && background -> LocationAccess.PRECISE_ALWAYS
            fine -> LocationAccess.PRECISE_WHILE_IN_USE
            coarse -> LocationAccess.APPROXIMATE_ONLY
            else -> LocationAccess.NONE
        }
    }

    /** The app's system settings page, for when a permission was permanently denied. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
