package com.deskdays.app.ui.office

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deskdays.app.BuildConfig
import com.deskdays.app.location.LatLng
import com.deskdays.app.ui.theme.DeskColors
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.LatLng as GLatLng

/** Sydney CBD, used before an office is set. */
private val DEFAULT_CENTER = GLatLng(-33.8688, 151.2093)

@Composable
fun OfficeMap(
    pin: LatLng?,
    radiusMeters: Int,
    cameraRequest: Int,
    onPinChange: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    if (!BuildConfig.HAS_MAPS_KEY) {
        // No Maps API key in this build: everything else still works.
        Box(
            modifier.fillMaxWidth().height(170.dp).clip(shape).background(DeskColors.Remote).padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (pin == null) {
                    "Map unavailable in this build. Search the address or use your current location."
                } else {
                    "Office set at %.5f, %.5f".format(pin.latitude, pin.longitude)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = DeskColors.TextBody,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val start = pin?.toGoogle() ?: DEFAULT_CENTER
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(start, if (pin == null) 12f else 16.5f)
    }
    val marker = remember { MarkerState(position = start) }
    val currentPin by rememberUpdatedState(pin)
    val currentOnPinChange by rememberUpdatedState(onPinChange)

    // Follow pin changes made outside the map (search, current location).
    LaunchedEffect(pin) {
        if (pin != null && marker.position != pin.toGoogle()) marker.position = pin.toGoogle()
    }
    LaunchedEffect(cameraRequest) {
        if (pin != null) camera.animate(CameraUpdateFactory.newLatLngZoom(pin.toGoogle(), 16.5f))
    }
    // Report the pin's new spot when a drag ends.
    LaunchedEffect(marker) {
        snapshotFlow { marker.isDragging }.collect { dragging ->
            val p = currentPin
            if (!dragging && p != null && marker.position != p.toGoogle()) {
                currentOnPinChange(marker.position.toDesk())
            }
        }
    }

    Box(modifier.fillMaxWidth().height(220.dp).clip(shape)) {
        GoogleMap(
            cameraPositionState = camera,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
            onMapClick = { onPinChange(it.toDesk()) },
        ) {
            if (pin != null) {
                Circle(
                    center = pin.toGoogle(),
                    radius = radiusMeters.toDouble(),
                    fillColor = DeskColors.Accent.copy(alpha = 0.16f),
                    strokeColor = DeskColors.Accent,
                    strokeWidth = 4f,
                )
                Marker(state = marker, draggable = true, title = "Office")
            }
        }
        Text(
            if (pin == null) "Tap the map to place your office" else "Drag pin to adjust",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                .background(DeskColors.Surface, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private fun LatLng.toGoogle() = GLatLng(latitude, longitude)
private fun GLatLng.toDesk() = LatLng(latitude, longitude)
