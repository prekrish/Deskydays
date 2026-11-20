package com.deskdays.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.deskdays.core.CountingRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** The single office a user tracks. */
data class Office(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 150,
    val address: String = "",
    val confirmWithWifi: Boolean = false,
    val wifiSsid: String? = null,
    val wifiBssid: String? = null,
)

/** How presence is recorded. MANUAL is the fallback when location access is declined. */
enum class TrackingMode { AUTOMATIC, MANUAL }

data class Settings(
    val office: Office?,
    val rules: CountingRules,
    /** First day of tracking; earlier days don't count against the target. */
    val trackedFrom: LocalDate?,
    val onboardingDone: Boolean,
    val trackingMode: TrackingMode,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val lat = doublePreferencesKey("office_lat")
        val lng = doublePreferencesKey("office_lng")
        val radius = intPreferencesKey("office_radius_m")
        val address = stringPreferencesKey("office_address")
        val wifiConfirm = booleanPreferencesKey("wifi_confirm")
        val wifiSsid = stringPreferencesKey("wifi_ssid")
        val wifiBssid = stringPreferencesKey("wifi_bssid")
        val threshold = intPreferencesKey("threshold_minutes")
        val mergeGap = intPreferencesKey("merge_gap_minutes")
        val target = intPreferencesKey("target_percent")
        val workingDays = stringPreferencesKey("working_days") // e.g. "1,2,3,4,5" (ISO day numbers)
        val zone = stringPreferencesKey("zone_id")
        val countNonWorking = booleanPreferencesKey("count_non_working_days")
        val trackedFrom = stringPreferencesKey("tracked_from")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val trackingMode = stringPreferencesKey("tracking_mode")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun setOffice(office: Office) = context.dataStore.edit { p ->
        p[Keys.lat] = office.latitude
        p[Keys.lng] = office.longitude
        p[Keys.radius] = office.radiusMeters
        p[Keys.address] = office.address
        p[Keys.wifiConfirm] = office.confirmWithWifi
        if (office.wifiSsid != null) p[Keys.wifiSsid] = office.wifiSsid else p.remove(Keys.wifiSsid)
        if (office.wifiBssid != null) p[Keys.wifiBssid] = office.wifiBssid else p.remove(Keys.wifiBssid)
        markTrackingStarted(p)
    }

    suspend fun clearOffice() = context.dataStore.edit { p ->
        listOf(Keys.lat, Keys.lng, Keys.radius, Keys.address, Keys.wifiConfirm, Keys.wifiSsid, Keys.wifiBssid)
            .forEach { p.remove(it) }
    }

    suspend fun setTrackingMode(mode: TrackingMode) = context.dataStore.edit { it[Keys.trackingMode] = mode.name }

    /** Finishes onboarding and fixes the first counted day to today (only the first time). */
    suspend fun completeOnboarding() = context.dataStore.edit { p ->
        p[Keys.onboardingDone] = true
        markTrackingStarted(p)
    }

    private fun markTrackingStarted(p: androidx.datastore.preferences.core.MutablePreferences) {
        if (p[Keys.trackedFrom] == null) {
            val zone = p[Keys.zone]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of(DEFAULT_ZONE)
            p[Keys.trackedFrom] = LocalDate.now(zone).toString()
        }
    }

    suspend fun setRules(rules: CountingRules) = context.dataStore.edit { p ->
        p[Keys.threshold] = rules.thresholdMinutes
        p[Keys.mergeGap] = rules.mergeGapMinutes
        p[Keys.target] = rules.targetPercent
        p[Keys.workingDays] = rules.workingDays.map { it.value }.sorted().joinToString(",")
        p[Keys.zone] = rules.zoneId.id
        p[Keys.countNonWorking] = rules.countNonWorkingDayAttendance
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = CountingRules()
        val lat = this[Keys.lat]
        val lng = this[Keys.lng]
        val office = if (lat != null && lng != null) {
            Office(
                latitude = lat,
                longitude = lng,
                radiusMeters = this[Keys.radius] ?: 150,
                address = this[Keys.address].orEmpty(),
                confirmWithWifi = this[Keys.wifiConfirm] ?: false,
                wifiSsid = this[Keys.wifiSsid],
                wifiBssid = this[Keys.wifiBssid],
            )
        } else {
            null
        }
        val rules = runCatching {
            CountingRules(
                thresholdMinutes = this[Keys.threshold] ?: defaults.thresholdMinutes,
                mergeGapMinutes = this[Keys.mergeGap] ?: defaults.mergeGapMinutes,
                targetPercent = this[Keys.target] ?: defaults.targetPercent,
                workingDays = this[Keys.workingDays]
                    ?.split(',')?.mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 1..7 } }
                    ?.map(DayOfWeek::of)?.toSet()?.takeIf { it.isNotEmpty() }
                    ?: defaults.workingDays,
                zoneId = this[Keys.zone]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaults.zoneId,
                countNonWorkingDayAttendance = this[Keys.countNonWorking] ?: defaults.countNonWorkingDayAttendance,
            )
        }.getOrDefault(defaults)
        return Settings(
            office = office,
            rules = rules,
            trackedFrom = this[Keys.trackedFrom]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onboardingDone = this[Keys.onboardingDone] ?: false,
            trackingMode = this[Keys.trackingMode]
                ?.let { runCatching { TrackingMode.valueOf(it) }.getOrNull() } ?: TrackingMode.MANUAL,
        )
    }

    private companion object {
        const val DEFAULT_ZONE = "Australia/Sydney"
    }
}
