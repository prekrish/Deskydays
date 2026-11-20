package com.deskdays.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdays.app.data.AppContainer
import com.deskdays.app.data.TrackingMode
import com.deskdays.core.DayStatus
import com.deskdays.core.DaySummary
import com.deskdays.core.MonthNudge
import com.deskdays.core.MonthStats
import com.deskdays.core.TodayProgress
import com.deskdays.core.monthNudge
import com.deskdays.core.todayProgress
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUi(
    val today: LocalDate,
    val zone: ZoneId,
    val day: DaySummary,
    val progress: TodayProgress,
    val month: MonthStats,
    val nudge: MonthNudge,
    val trackingMode: TrackingMode,
)

class TodayViewModel(private val container: AppContainer) : ViewModel() {
    val ui: StateFlow<TodayUi?> = container.feed
        .observe { s -> container.feed.todayIn(s.rules.zoneId).map { d -> YearMonth.from(d).let { it.atDay(1) to it.atEndOfMonth() } } }
        .map { snap ->
            val day = snap.day(snap.today) ?: return@map null
            val month = snap.calculator.monthFromDays(YearMonth.from(snap.today), snap.days, snap.today)
            TodayUi(
                today = snap.today,
                zone = snap.zone,
                day = day,
                progress = todayProgress(day, snap.settings.rules.thresholdMinutes, snap.zone),
                month = month,
                nudge = monthNudge(month),
                trackingMode = snap.settings.trackingMode,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null = back to the detected status. */
    fun setStatus(date: LocalDate, status: DayStatus?) {
        viewModelScope.launch {
            if (status == null) container.attendance.clearOverride(date) else container.attendance.setOverride(date, status)
        }
    }
}
