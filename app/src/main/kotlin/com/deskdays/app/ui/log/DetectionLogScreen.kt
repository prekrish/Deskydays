package com.deskdays.app.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdays.app.DeskDaysApp
import com.deskdays.app.ui.components.DeskCard
import com.deskdays.app.ui.components.LinkButton
import com.deskdays.app.ui.theme.DeskColors
import com.deskdays.core.EventSource
import com.deskdays.core.EventType
import com.deskdays.core.PresenceEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")

/** Raw arrival/departure signals, newest first. Shows exactly what's stored. */
@Composable
fun DetectionLogScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val container = (LocalContext.current.applicationContext as DeskDaysApp).container
    val events by remember { container.attendance.observeRecent(200) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val zone = ZoneId.systemDefault()

    LazyColumn(
        Modifier.fillMaxSize().background(DeskColors.Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { LinkButton("‹ Back", onBack) }
        item { Text("Detection log", style = MaterialTheme.typography.headlineMedium) }
        item {
            Text(
                "Every arrival and departure signal Desk Days has saved. Office days are worked out from these.",
                style = MaterialTheme.typography.bodyMedium,
                color = DeskColors.TextBody,
            )
        }
        if (events.isEmpty()) {
            item { DeskCard { Text("Nothing yet. Signals appear here when you arrive at or leave your office.") } }
        }
        items(events) { e -> EventRow(e, zone) }
    }
}

@Composable
private fun EventRow(e: PresenceEvent, zone: ZoneId) {
    val arrived = e.type == EventType.ENTER
    Row(
        Modifier.fillMaxWidth().background(DeskColors.Surface, MaterialTheme.shapes.large).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).background(if (arrived) DeskColors.Accent else DeskColors.Outline, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    e.source == EventSource.WIFI && arrived -> "On office Wi‑Fi"
                    e.source == EventSource.WIFI -> "Left office Wi‑Fi"
                    arrived -> "Arrived"
                    else -> "Left"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(TIME.format(e.timestamp.atZone(zone)), style = MaterialTheme.typography.bodySmall, color = DeskColors.TextSecondary)
        }
        Text(
            when (e.source) {
                EventSource.GEOFENCE -> "Location"
                EventSource.WIFI -> "Wi‑Fi"
                EventSource.MANUAL -> "Manual"
            },
            style = MaterialTheme.typography.bodySmall,
            color = DeskColors.TextSecondary,
        )
    }
}
