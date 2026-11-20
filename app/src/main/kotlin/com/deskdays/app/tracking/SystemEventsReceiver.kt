package com.deskdays.app.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Geofences don't survive a reboot, and may be dropped on app update: re-register. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Tracking.sync(context.applicationContext, force = true)
        }
    }
}
