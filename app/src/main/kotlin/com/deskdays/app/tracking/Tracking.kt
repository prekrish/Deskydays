package com.deskdays.app.tracking

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.Instant

/** Entry points for scheduling background tracking work. */
object Tracking {
    private const val SYNC = "geofence-sync"
    private const val SYNC_DAILY = "geofence-sync-daily"
    private const val WIFI_PERIODIC = "wifi-check"
    private const val WIFI_NOW = "wifi-check-now"

    /**
     * Makes the registered geofence match settings. With [force], re-registers even if it looks
     * current (after boot, app update, or when Play services dropped it).
     */
    fun sync(context: Context, force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<GeofenceSyncWorker>()
            .setInputData(workDataOf(GeofenceSyncWorker.KEY_FORCE to force))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
            .build()
        // A forced sync must not be swallowed by a pending non-forced one.
        val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC, policy, request)
    }

    /** Daily safety net: geofences can be silently dropped (e.g. Play services data cleared). */
    fun scheduleDailySync(context: Context) {
        val request = PeriodicWorkRequestBuilder<GeofenceSyncWorker>(Duration.ofDays(1))
            .setInputData(workDataOf(GeofenceSyncWorker.KEY_FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SYNC_DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Wi‑Fi confirmation: check now, then every 15 minutes (WorkManager's minimum) while inside. */
    fun startWifiChecks(context: Context) {
        checkWifiNow(context, exitAt = null)
        val periodic = PeriodicWorkRequestBuilder<WifiCheckWorker>(Duration.ofMinutes(15)).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WIFI_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    /** One check right away. [exitAt] = geofence exit time, used if this check records a WIFI EXIT. */
    fun checkWifiNow(context: Context, exitAt: Instant?) {
        val request = OneTimeWorkRequestBuilder<WifiCheckWorker>()
            .setInputData(workDataOf(WifiCheckWorker.KEY_EXIT_AT to (exitAt?.toEpochMilli() ?: 0L)))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WIFI_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun stopWifiChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WIFI_PERIODIC)
    }
}
