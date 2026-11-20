package com.deskdays.app.ui.stats

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdays.app.data.AppContainer
import com.deskdays.core.MonthNudge
import com.deskdays.core.MonthStats
import com.deskdays.core.daysToCsv
import com.deskdays.core.monthNudge
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatsUi(
    val current: MonthStats,
    val history: List<MonthStats>,
    val nudge: MonthNudge,
    val targetPercent: Int,
)

class StatsViewModel(private val container: AppContainer) : ViewModel() {
    val ui: StateFlow<StatsUi?> = container.feed
        .observe { s ->
            container.feed.todayIn(s.rules.zoneId).map { d ->
                val m = YearMonth.from(d)
                m.minusMonths(HISTORY_MONTHS - 1L).atDay(1) to m.atEndOfMonth()
            }
        }
        .map { snap ->
            val history = snap.days.groupBy { YearMonth.from(it.date) }
                .map { (m, days) -> snap.calculator.monthFromDays(m, days, snap.today) }
                .sortedBy { it.month }
            val current = history.last()
            StatsUi(current, history, monthNudge(current), snap.settings.rules.targetPercent)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    fun suggestedFileName(): String = "desk-days-${LocalDate.now()}.csv"

    /** Writes every day from the start of tracking (or the last 12 months) up to today. */
    fun exportCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val settings = container.settings.settings.first()
                val today = LocalDate.now(settings.rules.zoneId)
                val from = settings.trackedFrom?.takeIf { it.isBefore(today) } ?: today.minusMonths(12).withDayOfMonth(1)
                val snap = container.feed.compute(from, today)
                val csv = daysToCsv(snap.days, snap.zone)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: error("Couldn't open the file")
                }
                snap.days.size
            }
            _exportMessage.value = result.fold({ "Exported $it days" }, { "Export failed: ${it.message}" })
        }
    }

    fun clearMessage() {
        _exportMessage.value = null
    }

    private companion object {
        const val HISTORY_MONTHS = 6
    }
}
