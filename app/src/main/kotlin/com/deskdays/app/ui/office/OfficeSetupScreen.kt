package com.deskdays.app.ui.office

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deskdays.app.DeskDaysApp
import com.deskdays.app.R
import com.deskdays.app.data.TrackingMode
import com.deskdays.app.location.AddressLookup
import com.deskdays.app.location.LocationAccess
import com.deskdays.app.location.LocationPermissions
import com.deskdays.app.tracking.TrackingState
import com.deskdays.app.ui.components.CardDivider
import com.deskdays.app.ui.components.DeskCard
import com.deskdays.app.ui.components.LinkButton
import com.deskdays.app.ui.components.PrimaryButton
import com.deskdays.app.ui.components.QuietButton
import com.deskdays.app.ui.components.ValueRow
import com.deskdays.app.ui.theme.DeskColors
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Where the screen is shown. Onboarding has a finish button; the Office tab saves edits. */
enum class OfficeSetupMode { ONBOARDING, SETTINGS }

@Composable
fun OfficeSetupScreen(
    mode: OfficeSetupMode,
    onDone: () -> Unit,
    onChangeTracking: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenDetectionLog: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as DeskDaysApp
    val vm: OfficeSetupViewModel = viewModel {
        OfficeSetupViewModel(app, app.container.settings, AddressLookup(app))
    }
    val form by vm.form.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    var access by remember { mutableStateOf(LocationPermissions.current(context)) }
    var status by remember { mutableStateOf(app.container.trackingState.status) }
    LifecycleResumeEffect(Unit) {
        access = LocationPermissions.current(context)
        status = app.container.trackingState.status
        onPauseOrDispose { }
    }
    val manual = form.trackingMode == TrackingMode.MANUAL

    Column(Modifier.fillMaxSize().background(DeskColors.Background)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Your office", style = MaterialTheme.typography.headlineMedium)
            if (mode == OfficeSetupMode.ONBOARDING) {
                Text(
                    "Search for the address, use your current location, or tap the map. Drag the pin to fine-tune.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeskColors.TextBody,
                )
            }

            if (form.loaded) {
                Box {
                    OfficeMap(
                        pin = form.pin,
                        radiusMeters = form.radiusMeters,
                        cameraRequest = form.cameraRequest,
                        onPinChange = vm::movePin,
                    )
                    if (access != LocationAccess.NONE) {
                        FilledTonalIconButton(
                            onClick = vm::useCurrentLocation,
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(44.dp)
                                .semantics { contentDescription = "Use my current location" },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = DeskColors.Surface),
                        ) {
                            if (form.busy == OfficeForm.Busy.LOCATING) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = DeskColors.Accent)
                            } else {
                                Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, tint = DeskColors.AccentDark)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = form.address,
                onValueChange = vm::setAddress,
                label = { Text("Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.searchAddress() }),
                trailingIcon = {
                    if (form.busy == OfficeForm.Busy.SEARCHING) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = DeskColors.Accent)
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_search),
                            contentDescription = "Find address",
                            modifier = Modifier.size(44.dp).clickable { focus.clearFocus(); vm.searchAddress() }.padding(10.dp),
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DeskColors.Surface,
                    unfocusedContainerColor = DeskColors.Surface,
                    focusedBorderColor = DeskColors.Accent,
                    unfocusedBorderColor = DeskColors.Outline,
                    focusedLabelColor = DeskColors.AccentDark,
                ),
            )

            form.message?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = DeskColors.BelowTarget)
            }

            DeskCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Geofence radius", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${form.radiusMeters} m", style = MaterialTheme.typography.titleMedium, color = DeskColors.AccentDark)
                }
                Slider(
                    value = form.radiusMeters.toFloat(),
                    onValueChange = { vm.setRadius((it / 10).toInt() * 10) },
                    valueRange = OfficeSetupViewModel.MIN_RADIUS.toFloat()..OfficeSetupViewModel.MAX_RADIUS.toFloat(),
                    modifier = Modifier.semantics { contentDescription = "Geofence radius" },
                    colors = SliderDefaults.colors(thumbColor = DeskColors.Accent, activeTrackColor = DeskColors.Accent),
                )
                if (form.radiusMeters < OfficeSetupViewModel.RELIABLE_RADIUS) {
                    Text(
                        "Under ${OfficeSetupViewModel.RELIABLE_RADIUS} m, arrivals can be missed. Wi‑Fi confirmation helps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeskColors.TextSecondary,
                    )
                }
                CardDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Confirm with office Wi‑Fi", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                form.busy == OfficeForm.Busy.READING_WIFI -> "Reading Wi‑Fi…"
                                form.confirmWithWifi && form.wifiSsid != null -> "Saved network: ${form.wifiSsid}"
                                else -> "Turn on while connected to the office network"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = DeskColors.TextSecondary,
                        )
                    }
                    Switch(
                        checked = form.confirmWithWifi,
                        onCheckedChange = vm::setConfirmWithWifi,
                        enabled = access == LocationAccess.PRECISE_ALWAYS || access == LocationAccess.PRECISE_WHILE_IN_USE,
                        colors = SwitchDefaults.colors(checkedTrackColor = DeskColors.Accent),
                    )
                }
            }

            RulesCard(form, vm)

            if (mode == OfficeSetupMode.SETTINGS) {
                TrackingCard(manual = manual, access = access, status = status, onChange = onChangeTracking)
                NavRow("Detection log", "", enabled = true, onClick = onOpenDetectionLog)
                NavRow("Backup & restore", "Coming soon", enabled = false) {}
                NavRow("Privacy policy", "", enabled = true, onClick = onOpenPrivacyPolicy)
            }
        }

        val saving = form.busy == OfficeForm.Busy.SAVING
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (mode) {
                OfficeSetupMode.ONBOARDING -> {
                    PrimaryButton("Save and finish", { vm.save(allowWithoutOffice = false, onSaved = onDone) }, enabled = !saving)
                    if (manual) QuietButton("Skip for now", { vm.save(allowWithoutOffice = true, onSaved = onDone) })
                }
                OfficeSetupMode.SETTINGS -> if (form.dirty) {
                    PrimaryButton("Save changes", { vm.save(allowWithoutOffice = manual, onSaved = onDone) }, enabled = !saving)
                }
            }
        }
    }
}

@Composable
private fun RulesCard(form: OfficeForm, vm: OfficeSetupViewModel) {
    DeskCard {
        Text("Counting rules", style = MaterialTheme.typography.titleMedium)
        StepperRow("Counts as office day after", formatHours(form.thresholdMinutes),
            onMinus = { vm.setThreshold(form.thresholdMinutes - 30) }, onPlus = { vm.setThreshold(form.thresholdMinutes + 30) })
        StepperRow("Ignore short exits under", "${form.mergeGapMinutes} min",
            onMinus = { vm.setMergeGap(form.mergeGapMinutes - 5) }, onPlus = { vm.setMergeGap(form.mergeGapMinutes + 5) })
        StepperRow("Monthly target", "${form.targetPercent}%",
            onMinus = { vm.setTarget(form.targetPercent - 5) }, onPlus = { vm.setTarget(form.targetPercent + 5) })
        Text("Work days", style = MaterialTheme.typography.bodyMedium, color = DeskColors.TextBody)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEach { day ->
                val on = day in form.workingDays
                val name = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                Box(
                    Modifier.size(40.dp).background(if (on) DeskColors.Accent else DeskColors.Background, CircleShape)
                        .clickable { vm.toggleWorkingDay(day) }
                        .semantics { contentDescription = "$name: ${if (on) "work day" else "day off"}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        color = if (on) DeskColors.Surface else DeskColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    ValueRow(label, value) {
        StepButton("−", "Decrease $label", onMinus)
        StepButton("+", "Increase $label", onPlus)
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(32.dp).background(DeskColors.Background, CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleMedium, color = DeskColors.AccentDark)
        }
    }
}

@Composable
private fun TrackingCard(manual: Boolean, access: LocationAccess, status: TrackingState.Status?, onChange: () -> Unit) {
    val (title, detail, action) = when {
        manual -> Triple("Tracking: manual", "You log office days yourself.", "Turn on automatic tracking")
        access == LocationAccess.PRECISE_ALWAYS -> Triple("Tracking: automatic", "Using your location in the background.", "Change")
        else -> Triple("Tracking is paused", "Location access was turned off. Allow \"all the time\" to keep counting.", "Fix location access")
    }
    DeskCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (!manual && access != LocationAccess.PRECISE_ALWAYS) DeskColors.BelowTarget else DeskColors.TextBody)
        if (!manual && status != null) {
            Text(
                "Status: ${status.text}",
                style = MaterialTheme.typography.bodySmall,
                color = if (status.healthy) DeskColors.TextSecondary else DeskColors.BelowTarget,
            )
        }
        LinkButton(action, onChange)
    }
}

@Composable
private fun NavRow(title: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(DeskColors.Surface, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            color = if (enabled) DeskColors.Text else DeskColors.TextSecondary)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = DeskColors.TextSecondary)
    }
}

internal fun formatHours(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h h" else "$h h $m m"
}
