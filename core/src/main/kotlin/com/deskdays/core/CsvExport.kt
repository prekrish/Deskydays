package com.deskdays.core

import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * CSV for the Stats "Export CSV" button. One row per day.
 * Columns: date, weekday, status, minutes, hours, visits, manual, needs_review, holiday
 */
fun daysToCsv(days: List<DaySummary>, zone: ZoneId): String = buildString {
    append("date,weekday,status,minutes,hours,visits,manual,needs_review,holiday\r\n")
    for (d in days) {
        val visits = d.visits.joinToString("; ") {
            "${TIME.format(it.start.atZone(zone))}-${TIME.format(it.end.atZone(zone))}"
        }
        val fields = listOf(
            d.date.toString(),
            d.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() },
            d.status.name,
            d.minutes.toString(),
            "%.2f".format(java.util.Locale.ROOT, d.minutes / 60.0),
            visits,
            d.manuallySet.toString(),
            d.needsReview.toString(),
            d.holidayName.orEmpty(),
        )
        append(fields.joinToString(",") { csvEscape(it) })
        append("\r\n")
    }
}

internal fun csvEscape(s: String): String {
    // Leading =, +, -, @ can trigger formula execution in spreadsheet apps.
    val safe = if (s.isNotEmpty() && s[0] in "=+-@" && s.toDoubleOrNull() == null) "'$s" else s
    return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + safe.replace("\"", "\"\"") + "\""
    } else {
        safe
    }
}
