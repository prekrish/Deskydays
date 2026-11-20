package com.deskdays.app.ui.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deskdays.app.DeskDaysApp
import com.deskdays.app.ui.components.CardDivider
import com.deskdays.app.ui.components.DeskCard
import com.deskdays.app.ui.components.ValueRow
import com.deskdays.app.ui.theme.DeskColors
import com.deskdays.app.ui.today.ProgressBar
import com.deskdays.core.MonthNudge
import com.deskdays.core.MonthStats
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DeskDaysApp
    val vm: StatsViewModel = viewModel { StatsViewModel(app.container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val message by vm.exportMessage.collectAsStateWithLifecycle()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(context.applicationContext, uri)
    }
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(4_000)
            vm.clearMessage()
        }
    }
    val state = ui ?: return Box(Modifier.fillMaxSize().background(DeskColors.Background))
    val m = state.current
    val monthName = m.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

    Column(
        Modifier.fillMaxSize().background(DeskColors.Background).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Stats", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { exporter.launch(vm.suggestedFileName()) },
                modifier = Modifier.heightIn(min = 44.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, DeskColors.Outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DeskColors.Text),
            ) { Text("Export CSV", style = MaterialTheme.typography.labelLarge) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DeskColors.TextBody) }

        DeskCard(padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)) {
            Text("$monthName so far", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${m.percent}%",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp),
                    color = if (m.targetMet) DeskColors.AccentDark else DeskColors.Text,
                )
                Text("target ${state.targetPercent}%", style = MaterialTheme.typography.bodyMedium, color = DeskColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp))
            }
            ProgressBar((m.fraction / (state.targetPercent / 100.0)).toFloat().coerceIn(0f, 1f))
            NudgePill(state.nudge)
        }

        DeskCard(padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
            Text("How it's counted", style = MaterialTheme.typography.titleMedium)
            ValueRow("Working days in $monthName", "${m.workingDays}")
            ValueRow("Leave", "− ${m.leaveDays}")
            ValueRow("NSW public holidays", "− ${m.holidayDays}")
            if (m.untrackedDays > 0) ValueRow("Before tracking started", "− ${m.untrackedDays}")
            CardDivider()
            ValueRow("Days that count · ${state.targetPercent}% target", "${m.eligibleDays} · ${m.targetDays} days")
            ValueRow("Office days so far", "${m.officeDays}")
        }

        DeskCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last ${state.history.size} months", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("line = ${state.targetPercent}%", style = MaterialTheme.typography.bodySmall, color = DeskColors.TextSecondary)
            }
            HistoryChart(state.history, state.targetPercent)
        }
    }
}

@Composable
private fun NudgePill(n: MonthNudge) {
    val (text, good) = when (n) {
        MonthNudge.TargetMet -> "Target reached. Nice work." to true
        MonthNudge.NoEligibleDays -> "No working days count this month" to true
        is MonthNudge.OnTrack -> "Go in ${n.needed} of the next ${n.remaining} working ${if (n.remaining == 1) "day" else "days"}" to true
        is MonthNudge.OutOfReach ->
            "Target out of reach: ${n.needed} needed, ${n.remaining} working ${if (n.remaining == 1) "day" else "days"} left" to false
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = if (good) DeskColors.AccentDark else DeskColors.BelowTarget,
        modifier = Modifier.fillMaxWidth()
            .background(if (good) DeskColors.AccentContainer else DeskColors.Holiday, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** Bars of monthly %, with a dashed line at the target. Current month is lighter (incomplete). */
@Composable
private fun HistoryChart(history: List<MonthStats>, targetPercent: Int) {
    val chartHeight = 140.dp
    // Scale so the target line sits a bit below the top, and 100% always fits.
    val maxPercent = maxOf(100, history.maxOfOrNull { it.percent } ?: 0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(chartHeight)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                history.forEachIndexed { i, m ->
                    val isCurrent = i == history.lastIndex
                    val color = when {
                        isCurrent -> DeskColors.CurrentMonthBar
                        m.percent >= targetPercent -> DeskColors.Accent
                        else -> DeskColors.BelowTarget
                    }
                    val name = m.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    Box(
                        Modifier.weight(1f).fillMaxHeight((m.percent.toFloat() / maxPercent).coerceIn(0.02f, 1f))
                            .background(color, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .semantics { contentDescription = "$name: ${m.percent}%" },
                    )
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * (1f - targetPercent.toFloat() / maxPercent)
                drawLine(
                    DeskColors.Text, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            history.forEach { m ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(m.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelMedium, color = DeskColors.TextSecondary)
                    Text("${m.percent}%", style = MaterialTheme.typography.labelSmall, color = DeskColors.TextBody)
                }
            }
        }
    }
}
