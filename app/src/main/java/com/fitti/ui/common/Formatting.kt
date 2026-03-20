package com.fitti.ui.common

import java.text.SimpleDateFormat
import java.util.Locale

val muscleGroupLabels = mapOf(
    "CHEST" to "Brust",
    "BACK" to "R\u00fccken",
    "LEGS" to "Beine",
    "SHOULDERS" to "Schultern"
)

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
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        val start = format.parse(startedAt) ?: return null
        val end = format.parse(completedAt) ?: return null
        val diffMinutes = (end.time - start.time) / 60000
        "$diffMinutes min"
    } catch (_: Exception) {
        null
    }
}
