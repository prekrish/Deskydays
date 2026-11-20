package com.deskdays.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deskdays.app.DeskDaysApp
import com.deskdays.app.R
import com.deskdays.app.data.TrackingMode
import com.deskdays.app.ui.components.DayStatusSheet
import com.deskdays.app.ui.components.DeskCard
import com.deskdays.app.ui.components.SecondaryButton
import com.deskdays.app.ui.components.formatTime
import com.deskdays.app.ui.components.sourcesLabel
import com.deskdays.app.ui.theme.DeskColors
import com.deskdays.core.DayStatus
import com.deskdays.core.MonthNudge
import com.deskdays.core.formatMinutes
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
fun TodayScreen() {
    val app = LocalContext.current.applicationContext as DeskDaysApp
    val vm: TodayViewModel = viewModel { TodayViewModel(app.container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val state = ui ?: return Box(Modifier.fillMaxSize().background(DeskColors.Background))

    Column(
        Modifier.fillMaxSize().background(DeskColors.Background).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(DATE.format(state.today), style = MaterialTheme.typography.bodySmall, color = DeskColors.TextSecondary)
                Text("Today", style = MaterialTheme.typography.headlineMedium)
            }
            StatusBadge(state)
        }

        DeskCard(padding = PaddingValues(24.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressRing(state)
                Text(
                    todayMessage(state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = DeskColors.TextBody,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("Arrived", state.progress.arrivedAt?.let { formatTime(it, state.zone) } ?: "—", Modifier.weight(1f))
                    Tile("Detected by", if (state.day.manuallySet) "Manual" else sourcesLabel(state.progress.sources), Modifier.weight(1f))
                }
            }
        }

        MonthCard(state)

        SecondaryButton("Fix today manually", { editing = true })
    }

    if (editing) {
        DayStatusSheet(
            day = state.day,
            onPick = { vm.setStatus(state.today, it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun StatusBadge(state: TodayUi) {
    val (label, active) = when {
        state.progress.inOfficeNow -> "In office" to true
        state.progress.counts -> "Office day" to true
        state.trackingMode == TrackingMode.MANUAL -> "Manual logging" to false
        else -> "Not in office" to false
    }
    Row(
        Modifier.background(if (active) DeskColors.AccentContainer else DeskColors.Track, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(if (active) DeskColors.Accent else DeskColors.Muted, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (active) DeskColors.AccentDark else DeskColors.TextBody,
        )
    }
}

@Composable
private fun ProgressRing(state: TodayUi) {
    val p = state.progress
    val goal = formatMinutes(p.thresholdMinutes.toLong())
    Box(
        Modifier.size(200.dp).semantics { contentDescription = "${formatMinutes(p.minutes)} of $goal goal" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(DeskColors.Track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (p.fraction > 0f) {
                drawArc(
                    DeskColors.Accent, -90f, 360f * p.fraction, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatMinutes(p.minutes), style = MaterialTheme.typography.displayMedium)
            Text("of $goal goal", style = MaterialTheme.typography.bodyMedium, color = DeskColors.TextSecondary)
        }
    }
}

private fun todayMessage(state: TodayUi): String {
    val p = state.progress
    val day = state.day
    return when {
        day.manuallySet && day.status != DayStatus.OFFICE -> when (day.status) {
            DayStatus.LEAVE -> "Marked as leave. Today doesn't count toward the target."
            DayStatus.HOLIDAY -> "Marked as a holiday."
            else -> "Marked as remote."
        }
        p.counts -> if (day.manuallySet) "Marked as an office day." else "Today counts as an office day"
        day.status == DayStatus.HOLIDAY -> "${day.holidayName ?: "Public holiday"}. Enjoy the day off."
        !day.isWorkingDay && p.minutes == 0L -> "Not a work day."
        p.inOfficeNow -> "${formatMinutes(p.remainingMinutes)} more and today counts as an office day"
        p.minutes > 0 -> "${formatMinutes(p.minutes)} in the office today. ${formatMinutes(p.remainingMinutes)} more needed for it to count."
        state.trackingMode == TrackingMode.MANUAL -> "Went in today? Log it with \"Fix today manually\"."
        else -> "You haven't been to the office today."
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(DeskColors.Background, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = DeskColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.13f))
    }
}

@Composable
private fun MonthCard(state: TodayUi) {
    val m = state.month
    DeskCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                state.today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text("${m.officeDays} of ${m.targetDays} office days", style = MaterialTheme.typography.bodySmall, color = DeskColors.TextSecondary)
        }
        ProgressBar(if (m.targetDays == 0) 1f else (m.officeDays.toFloat() / m.targetDays).coerceIn(0f, 1f))
        Text(nudgeText(state.nudge), style = MaterialTheme.typography.bodySmall, color = DeskColors.TextBody)
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color = DeskColors.Accent) {
    Box(Modifier.fillMaxWidth().height(10.dp).background(DeskColors.Track, RoundedCornerShape(999.dp))) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(10.dp).background(color, RoundedCornerShape(999.dp)))
        }
    }
}

fun nudgeText(n: MonthNudge): String = when (n) {
    MonthNudge.TargetMet -> "Target reached for this month"
    MonthNudge.NoEligibleDays -> "No working days count this month"
    is MonthNudge.OnTrack -> "${n.needed} more ${days(n.needed)} needed · ${n.remaining} working ${days(n.remaining)} left"
    is MonthNudge.OutOfReach -> "${n.needed} more ${days(n.needed)} needed, but only ${n.remaining} working ${days(n.remaining)} left"
}

private fun days(n: Int) = if (n == 1) "day" else "days"
