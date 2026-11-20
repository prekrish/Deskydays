package com.deskdays.app.ui.office

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdays.app.data.Office
import com.deskdays.app.data.SettingsRepository
import com.deskdays.app.data.TrackingMode
import com.deskdays.app.location.AddressLookup
import com.deskdays.app.location.LatLng
import com.deskdays.app.location.currentLocation
import com.deskdays.app.location.currentWifi
import com.deskdays.core.CountingRules
import java.time.DayOfWeek
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OfficeForm(
    val loaded: Boolean = false,
    val pin: LatLng? = null,
    val radiusMeters: Int = 150,
    val address: String = "",
    val confirmWithWifi: Boolean = false,
    val wifiSsid: String? = null,
    val wifiBssid: String? = null,
    val thresholdMinutes: Int = 240,
    val mergeGapMinutes: Int = 15,
    val targetPercent: Int = 50,
    val workingDays: Set<DayOfWeek> = CountingRules().workingDays,
    val trackingMode: TrackingMode = TrackingMode.MANUAL,
    val busy: Busy? = null,
    val message: String? = null,
    /** Bumped whenever the map camera should move to [pin]. */
    val cameraRequest: Int = 0,
    val dirty: Boolean = false,
) {
    enum class Busy { LOCATING, SEARCHING, READING_WIFI, SAVING }
}

class OfficeSetupViewModel(
    private val context: Context,
    private val settings: SettingsRepository,
    private val addresses: AddressLookup,
) : ViewModel() {
    private val _form = MutableStateFlow(OfficeForm())
    val form: StateFlow<OfficeForm> = _form.asStateFlow()

    private var baseRules = CountingRules()
    private var reverseLookup: Job? = null

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            baseRules = s.rules
            val o = s.office
            _form.value = OfficeForm(
                loaded = true,
                pin = o?.let { LatLng(it.latitude, it.longitude) },
                radiusMeters = o?.radiusMeters ?: 150,
                address = o?.address.orEmpty(),
                confirmWithWifi = o?.confirmWithWifi ?: false,
                wifiSsid = o?.wifiSsid,
                wifiBssid = o?.wifiBssid,
                thresholdMinutes = s.rules.thresholdMinutes,
                mergeGapMinutes = s.rules.mergeGapMinutes,
                targetPercent = s.rules.targetPercent,
                workingDays = s.rules.workingDays,
                trackingMode = s.trackingMode,
                cameraRequest = 1,
            )
        }
        // Keep the tracking mode live (it can change from the location access flow).
        viewModelScope.launch {
            settings.settings.collect { s -> _form.update { it.copy(trackingMode = s.trackingMode) } }
        }
    }

    private fun edit(block: (OfficeForm) -> OfficeForm) = _form.update { block(it).copy(dirty = true, message = null) }

    /** Pin moved by tapping or dragging on the map. Fills the address from the new point. */
    fun movePin(point: LatLng) {
        edit { it.copy(pin = point) }
        reverseLookup?.cancel()
        reverseLookup = viewModelScope.launch {
            addresses.describe(point)?.let { a -> _form.update { it.copy(address = a) } }
        }
    }

    fun setAddress(text: String) = edit { it.copy(address = text) }

    fun searchAddress() {
        val query = _form.value.address
        if (query.isBlank()) return
        viewModelScope.launch {
            _form.update { it.copy(busy = OfficeForm.Busy.SEARCHING, message = null) }
            val found = addresses.find(query)
            _form.update {
                if (found != null) {
                    it.copy(pin = found, busy = null, cameraRequest = it.cameraRequest + 1, dirty = true)
                } else {
                    it.copy(busy = null, message = "Couldn't find that address. Try adding the suburb, or place the pin on the map.")
                }
            }
        }
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _form.update { it.copy(busy = OfficeForm.Busy.LOCATING, message = null) }
            val here = currentLocation(context)
            _form.update {
                if (here != null) {
                    it.copy(pin = here, busy = null, cameraRequest = it.cameraRequest + 1, dirty = true)
                } else {
                    it.copy(busy = null, message = "Couldn't get your location. Check that location is on and allowed for Desk Days.")
                }
            }
            if (here != null) movePin(here)
        }
    }

    fun setRadius(meters: Int) = edit { it.copy(radiusMeters = meters.coerceIn(MIN_RADIUS, MAX_RADIUS)) }

    /** Turning Wi‑Fi confirmation on saves the network the phone is on right now. */
    fun setConfirmWithWifi(on: Boolean) {
        if (!on) {
            edit { it.copy(confirmWithWifi = false) }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(busy = OfficeForm.Busy.READING_WIFI, message = null) }
            val wifi = currentWifi(context)
            _form.update {
                if (wifi != null) {
                    it.copy(confirmWithWifi = true, wifiSsid = wifi.ssid, wifiBssid = wifi.bssid, busy = null, dirty = true)
                } else {
                    it.copy(
                        busy = null,
                        message = "Connect to your office Wi‑Fi first, then turn this on. (Needs precise location access.)",
                    )
                }
            }
        }
    }

    fun setThreshold(minutes: Int) = edit { it.copy(thresholdMinutes = minutes.coerceIn(30, 12 * 60)) }
    fun setMergeGap(minutes: Int) = edit { it.copy(mergeGapMinutes = minutes.coerceIn(0, 60)) }
    fun setTarget(percent: Int) = edit { it.copy(targetPercent = percent.coerceIn(5, 100)) }

    fun toggleWorkingDay(day: DayOfWeek) = edit {
        val next = if (day in it.workingDays) it.workingDays - day else it.workingDays + day
        if (next.isEmpty()) it else it.copy(workingDays = next)
    }

    fun clearMessage() = _form.update { it.copy(message = null) }

    /**
     * Saves office and rules. A location is required unless [allowWithoutOffice] (manual mode).
     * Calls [onSaved] on success.
     */
    fun save(allowWithoutOffice: Boolean, onSaved: () -> Unit) {
        val f = _form.value
        if (f.pin == null && !allowWithoutOffice) {
            _form.update { it.copy(message = "Set your office location first: search the address, use your current location, or tap the map.") }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(busy = OfficeForm.Busy.SAVING) }
            if (f.pin != null) {
                settings.setOffice(
                    Office(
                        latitude = f.pin.latitude,
                        longitude = f.pin.longitude,
                        radiusMeters = f.radiusMeters,
                        address = f.address.trim(),
                        confirmWithWifi = f.confirmWithWifi,
                        wifiSsid = f.wifiSsid,
                        wifiBssid = f.wifiBssid,
                    ),
                )
            }
            baseRules = baseRules.copy(
                thresholdMinutes = f.thresholdMinutes,
                mergeGapMinutes = f.mergeGapMinutes,
                targetPercent = f.targetPercent,
                workingDays = f.workingDays,
            )
            settings.setRules(baseRules)
            _form.update { it.copy(busy = null, dirty = false, message = null) }
            onSaved()
        }
    }

    companion object {
        const val MIN_RADIUS = 50
        const val MAX_RADIUS = 500

        /** Android recommends at least 100–150 m for reliable geofence transitions. */
        const val RELIABLE_RADIUS = 100
    }
}
