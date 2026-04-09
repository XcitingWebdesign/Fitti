package com.fitti.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

val muscleGroupLabels = mapOf(
    "CHEST" to "Brust",
    "BACK" to "R\u00fccken",
    "LEGS" to "Beine",
    "SHOULDERS" to "Schultern",
    "ARMS" to "Arme",
    "ABS" to "Bauch"
)

// Freshness thresholds (days since last training of a muscle group)
const val FRESHNESS_FRESH_DAYS = 4L
const val FRESHNESS_STALE_DAYS = 6L
const val WEIGHT_LOG_INTERVAL_DAYS = 7L

fun newDateTimeFormat(): SimpleDateFormat =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

fun newDateFormat(): SimpleDateFormat =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

fun formatDateTime(date: Date): String = newDateTimeFormat().format(date)

fun formatDate(date: Date): String = newDateFormat().format(date)

fun parseDateTime(str: String): Date? = try {
    newDateTimeFormat().parse(str)
} catch (_: Exception) { null }

fun daysSince(date: Date): Long {
    val diff = System.currentTimeMillis() - date.time
    return TimeUnit.MILLISECONDS.toDays(diff)
}

fun Double.cleanWeight(): String {
    return if (this == this.toLong().toDouble()) {
        this.toLong().toString()
    } else {
        this.toString()
    }
}

fun calculateDuration(startedAt: String, completedAt: String?): String? {
    if (completedAt == null) return null
    return try {
        val start = parseDateTime(startedAt) ?: return null
        val end = parseDateTime(completedAt) ?: return null
        val diffMinutes = (end.time - start.time) / 60000
        "$diffMinutes min"
    } catch (_: Exception) {
        null
    }
}
