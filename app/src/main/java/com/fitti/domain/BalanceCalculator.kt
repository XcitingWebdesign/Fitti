package com.fitti.domain

import com.fitti.data.WorkoutSessionHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Aggregates training volume per muscle group over a rolling time window
 * and normalizes the result so the group with the highest volume = 1.0.
 *
 * Volume = sum of `actualWeightKg * actualReps` across all completed sets
 * of sessions whose `completedAt` lies within [windowDays] before `now`.
 * The output therefore reflects how evenly the user trained across muscle
 * groups in the recent past, not absolute strength.
 */
object BalanceCalculator {

    const val DEFAULT_WINDOW_DAYS = 14L

    fun volumeByGroup(
        histories: List<WorkoutSessionHistory>,
        now: Date = Date(),
        windowDays: Long = DEFAULT_WINDOW_DAYS
    ): Map<String, Double> {
        val cutoff = now.time - TimeUnit.DAYS.toMillis(windowDays)
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        val result = mutableMapOf<String, Double>()

        for (history in histories) {
            val completedAt = history.session.completedAt ?: continue
            val completedDate = try {
                fmt.parse(completedAt)
            } catch (_: Exception) {
                null
            } ?: continue
            if (completedDate.time < cutoff) continue

            for (se in history.sessionExercises) {
                val group = se.sessionExercise.exerciseMuscleGroup
                if (group.isBlank()) continue
                for (log in se.setLogs) {
                    if (!log.completedFlag) continue
                    if (log.actualWeightKg <= 0.0 || log.actualReps <= 0) continue
                    val volume = log.actualWeightKg * log.actualReps
                    result[group] = (result[group] ?: 0.0) + volume
                }
            }
        }
        return result
    }

    fun normalize(raw: Map<String, Double>): Map<String, Float> {
        val peak = raw.values.maxOrNull() ?: return emptyMap()
        if (peak <= 0.0) return emptyMap()
        return raw.mapValues { (_, v) -> (v / peak).toFloat() }
    }
}
