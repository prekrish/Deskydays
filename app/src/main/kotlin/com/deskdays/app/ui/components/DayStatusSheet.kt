package com.deskdays.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.deskdays.app.ui.theme.DeskColors
import com.deskdays.core.DayStatus
import com.deskdays.core.DaySummary
import java.time.format.DateTimeFormatter

private val TITLE = DateTimeFormatter.ofPattern("EEEE d MMMM")

/**
 * Pick a manual status for a day, or go back to the detected one.
 * [onPick] gets null for "use detected".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayStatusSheet(day: DaySummary, onPick: (DayStatus?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DeskColors.Surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(TITLE.format(day.date), style = MaterialTheme.typography.titleLarge)
            Text(
                "Mark this day yourself. A manual choice always overrides what was detected.",
                style = MaterialTheme.typography.bodyMedium,
                color = DeskColors.TextBody,
            )
            Option("In office", "Counts as an office day", DeskColors.Accent, day.manuallySet && day.status == DayStatus.OFFICE) { onPick(DayStatus.OFFICE) }
            Option("Remote", "Worked away from the office", DeskColors.Remote, day.manuallySet && day.status == DayStatus.REMOTE) { onPick(DayStatus.REMOTE) }
            Option("Leave", "Doesn't count toward the target", DeskColors.LeaveBorder, day.manuallySet && day.status == DayStatus.LEAVE) { onPick(DayStatus.LEAVE) }
            Option("Holiday", "A holiday not on the NSW calendar", DeskColors.HolidayText, day.manuallySet && day.status == DayStatus.HOLIDAY) { onPick(DayStatus.HOLIDAY) }
            if (day.manuallySet) {
                QuietButton("Use detected status", { onPick(null) })
            }
        }
    }
}

@Composable
private fun Option(title: String, detail: String, swatch: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .background(if (selected) DeskColors.AccentContainer else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(14.dp).background(swatch, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = DeskColors.TextSecondary)
        }
        if (selected) Text("Selected", style = MaterialTheme.typography.labelMedium, color = DeskColors.AccentDark)
    }
}
