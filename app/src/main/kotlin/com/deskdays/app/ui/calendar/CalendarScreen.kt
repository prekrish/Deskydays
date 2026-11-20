package com.deskdays.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deskdays.app.DeskDaysApp
import com.deskdays.app.ui.components.DayStatusSheet
import com.deskdays.app.ui.components.DeskCard
import com.deskdays.app.ui.components.LinkButton
import com.deskdays.app.ui.components.formatTime
import com.deskdays.app.ui.components.sourcesLabel
import com.deskdays.app.ui.components.statusLabel
import com.deskdays.app.ui.theme.DeskColors
import com.deskdays.core.DayStatus
import com.deskdays.core.DaySummary
import com.deskdays.core.breakMinutes
import com.deskdays.core.formatMinutes
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun CalendarScreen() {
    val app = LocalContext.current.applicationContext as DeskDaysApp
    val vm: CalendarViewModel = viewModel { CalendarViewModel(app.container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<DaySummary?>(null) }
    val state = ui ?: return Box(Modifier.fillMaxSize().background(DeskColors.Background))

    Column(
        Modifier.fillMaxSize().background(DeskColors.Background).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(MONTH.format(state.month), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            NavArrow("‹", "Previous month", vm::previousMonth)
            NavArrow("›", "Next month", vm::nextMonth)
        }

        DeskCard(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach {
                    Text(
                        it.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = DeskColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            val leading = state.month.atDay(1).dayOfWeek.value - 1
            val cells: List<DaySummary?> = List(leading) { null } + state.days
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f)) {
                            if (day != null) {
                                DayCell(day, isToday = day.date == state.today, isSelected = day.date == state.selected?.date) {
                                    vm.select(day.date)
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                }
            }
        }

        Legend(state.thresholdMinutes)

        state.selected?.let { day ->
            DayDetail(day, state, onChange = { editing = day })
        }
    }

    editing?.let { day ->
        DayStatusSheet(
            day = day,
            onPick = { vm.setStatus(day.date, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun NavArrow(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineMedium, color = DeskColors.AccentDark)
    }
}

private data class CellStyle(val background: Color, val text: Color, val border: Color?, val bold: Boolean)

private fun cellStyle(day: DaySummary, isToday: Boolean): CellStyle = when {
    day.status == DayStatus.OFFICE -> CellStyle(DeskColors.Accent, Color.White, null, true)
    day.status == DayStatus.LEAVE -> CellStyle(DeskColors.Leave, DeskColors.LeaveText, DeskColors.LeaveBorder, true)
    day.status == DayStatus.HOLIDAY -> CellStyle(DeskColors.Holiday, DeskColors.HolidayText, null, true)
    isToday -> CellStyle(Color.Transparent, DeskColors.AccentDark, DeskColors.Accent, true)
    day.status == DayStatus.REMOTE -> CellStyle(DeskColors.Remote, DeskColors.Text, null, false)
    day.status == DayStatus.NON_WORKING -> CellStyle(Color.Transparent, DeskColors.Muted, null, false)
    day.status == DayStatus.UNTRACKED -> CellStyle(Color.Transparent, DeskColors.Outline, null, false)
    else -> CellStyle(Color.Transparent, DeskColors.Text, null, false)
}

@Composable
private fun DayCell(day: DaySummary, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val s = cellStyle(day, isToday)
    val shape = RoundedCornerShape(12.dp)
    var m = Modifier.fillMaxWidth().height(42.dp).background(s.background, shape)
    if (s.border != null) m = m.border(if (isToday) 2.dp else 1.5.dp, s.border, shape)
    if (isSelected) m = m.border(2.dp, DeskColors.Text, shape)
    Box(
        m.clickable(onClick = onClick).semantics {
            contentDescription = "${DAY.format(day.date)}: ${statusLabel(day, isToday)}" +
                if (day.needsReview) ", needs review" else ""
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            color = s.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (s.bold) FontWeight.SemiBold else FontWeight.Normal),
        )
        if (day.needsReview) {
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(6.dp).background(DeskColors.BelowTarget, CircleShape))
        }
    }
}

@Composable
private fun Legend(thresholdMinutes: Int) {
    val items = listOf(
        "In office (${formatMinutes(thresholdMinutes.toLong())}+)" to DeskColors.Accent,
        "Remote" to DeskColors.Remote,
        "Leave" to DeskColors.LeaveBorder,
        "Holiday" to DeskColors.Holiday,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (label, color) -> LegendItem(label, color, Modifier.weight(1f)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(14.dp).border(2.dp, DeskColors.Accent, RoundedCornerShape(4.dp)))
            Text("Today (in progress)", style = MaterialTheme.typography.bodySmall, color = DeskColors.TextBody)
            Box(Modifier.padding(start = 8.dp).size(6.dp).background(DeskColors.BelowTarget, CircleShape))
            Text("Needs review", style = MaterialTheme.typography.bodySmall, color = DeskColors.TextBody)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(14.dp).background(color, RoundedCornerShape(4.dp)))
        Text(label, style = MaterialTheme.typography.bodySmall, color = DeskColors.TextBody)
    }
}

@Composable
private fun DayDetail(day: DaySummary, state: CalendarUi, onChange: () -> Unit) {
    val isToday = day.date == state.today
    DeskCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(DAY.format(day.date), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            LinkButton("Change", onChange)
        }
        val summary = buildString {
            append(statusLabel(day, isToday))
            if (day.minutes > 0) append(" · ${formatMinutes(day.minutes)}")
            if (day.manuallySet) append(" · set manually")
        }
        Text(summary, style = MaterialTheme.typography.titleMedium)
        day.holidayName?.takeIf { day.status != DayStatus.HOLIDAY }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = DeskColors.HolidayText)
        }
        day.visits.forEach { v ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val end = if (v.ongoing) "now" else formatTime(v.end, state.zone)
                Text("${formatTime(v.start, state.zone)} – $end", style = MaterialTheme.typography.bodyLarge, color = DeskColors.TextBody)
                val breaks = v.breakMinutes()
                val detail = buildList {
                    add(sourcesLabel(v.sources))
                    if (breaks.isNotEmpty()) {
                        val label = if (breaks.size == 1) "break ${breaks[0]} min" else "breaks ${breaks.joinToString(", ")} min"
                        add("${v.segments.size} visits merged ($label)")
                    }
                }.joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodySmall, color = DeskColors.TextSecondary)
            }
        }
        if (day.needsReview) {
            Text(
                "No departure was recorded, so the end time is the last signal from the office. Tap Change if this day is wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = DeskColors.BelowTarget,
            )
        }
        if (day.visits.isEmpty() && day.status in setOf(DayStatus.REMOTE, DayStatus.PENDING) && !day.date.isAfter(state.today)) {
            Text("No office visits recorded.", style = MaterialTheme.typography.bodySmall, color = DeskColors.TextSecondary)
        }
    }
}
