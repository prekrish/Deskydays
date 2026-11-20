package com.deskdays.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdays.app.data.AppContainer
import com.deskdays.core.DayStatus
import com.deskdays.core.DaySummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalendarUi(
    val month: YearMonth,
    val today: LocalDate,
    val zone: ZoneId,
    val thresholdMinutes: Int,
    val days: List<DaySummary>,
    val selected: DaySummary?,
)

class CalendarViewModel(private val container: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(LocalDate.now())

    val ui: StateFlow<CalendarUi?> = combine(
        container.feed.observe { month.map { it.atDay(1) to it.atEndOfMonth() } },
        selectedDate,
    ) { snap, sel ->
        CalendarUi(
            month = YearMonth.from(snap.from),
            today = snap.today,
            zone = snap.zone,
            thresholdMinutes = snap.settings.rules.thresholdMinutes,
            days = snap.days,
            selected = sel?.let { snap.day(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() = changeMonth(month.value.minusMonths(1))
    fun nextMonth() = changeMonth(month.value.plusMonths(1))

    private fun changeMonth(m: YearMonth) {
        month.value = m
        val today = LocalDate.now()
        selectedDate.value = if (YearMonth.from(today) == m) today else null
    }

    fun select(date: LocalDate) {
        selectedDate.value = date
    }

    fun setStatus(date: LocalDate, status: DayStatus?) {
        viewModelScope.launch {
            if (status == null) container.attendance.clearOverride(date) else container.attendance.setOverride(date, status)
        }
    }
}
