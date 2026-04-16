package com.fitti.domain

import com.fitti.data.WorkoutSessionEntity
import com.fitti.data.WorkoutSessionHistory
import com.fitti.ui.common.parseDateTime
import java.util.Calendar
import java.util.Date

/**
 * Pure-function service: derives streaks and achievement milestones
 * from completed workout sessions and exercise history.
 *
 * No persistence here — callers combine results with SettingsRepository
 * to track which achievements the user has already seen.
 */
object AchievementService {

    /**
     * Consecutive calendar weeks (ending with the current or last completed week)
     * where the user completed at least [minSessionsPerWeek] sessions.
     * Returns 0 if the most recent qualifying week is too old to count.
     */
    fun computeStreak(
        sessions: List<WorkoutSessionEntity>,
        minSessionsPerWeek: Int = 2,
        now: Date = Date()
    ): Int {
        val completed = sessions.filter { it.status == WorkoutSessionEntity.STATUS_COMPLETED }
        if (completed.isEmpty()) return 0

        val countsByYearWeek = mutableMapOf<Pair<Int, Int>, Int>()
        for (s in completed) {
            val date = parseDateTime(s.completedAt ?: s.startedAt) ?: continue
            val key = yearWeek(date)
            countsByYearWeek[key] = (countsByYearWeek[key] ?: 0) + 1
        }

        var cursor = yearWeek(now)
        val current = countsByYearWeek[cursor] ?: 0
        // Current week is a bonus — don't require it to have hit the threshold yet,
        // but don't let an empty current week end a streak from last week either.
        var streak = if (current >= minSessionsPerWeek) 1 else 0
        cursor = previousWeek(cursor)

        while ((countsByYearWeek[cursor] ?: 0) >= minSessionsPerWeek) {
            streak++
            cursor = previousWeek(cursor)
        }
        return streak
    }

    /** All milestones the user has earned so far. */
    fun computeAchievements(
        sessions: List<WorkoutSessionEntity>,
        histories: List<WorkoutSessionHistory>,
        startingWeightByExerciseId: Map<Long, Double> = emptyMap()
    ): List<Achievement> {
        val completed = sessions.filter { it.status == WorkoutSessionEntity.STATUS_COMPLETED }
        val count = completed.size
        val earned = mutableListOf<Achievement>()

        if (count >= 1) earned += Achievement(
            code = Achievement.FIRST_SESSION,
            emoji = "\uD83C\uDF89",
            title = "Erstes Training",
            description = "Du hast dein erstes Training abgeschlossen."
        )
        if (count >= 10) earned += sessionMilestone(Achievement.SESSIONS_10, 10, "\uD83D\uDCAA")
        if (count >= 25) earned += sessionMilestone(Achievement.SESSIONS_25, 25, "\uD83D\uDD25")
        if (count >= 50) earned += sessionMilestone(Achievement.SESSIONS_50, 50, "\u2728")
        if (count >= 100) earned += sessionMilestone(Achievement.SESSIONS_100, 100, "\uD83C\uDFC6")

        val streak = computeStreak(completed)
        if (streak >= 4) earned += Achievement(
            code = Achievement.STREAK_4,
            emoji = "\uD83D\uDD25",
            title = "4-Wochen-Serie",
            description = "4 Wochen in Folge regelm\u00e4\u00dfig trainiert."
        )
        if (streak >= 12) earned += Achievement(
            code = Achievement.STREAK_12,
            emoji = "\u26A1",
            title = "12-Wochen-Serie",
            description = "12 Wochen in Folge \u2014 beeindruckend!"
        )

        if (startingWeightByExerciseId.isNotEmpty() && hasDoubledAnyWeight(histories, startingWeightByExerciseId)) {
            earned += Achievement(
                code = Achievement.STRENGTH_DOUBLED,
                emoji = "\uD83C\uDFCB\uFE0F",
                title = "Gewicht verdoppelt",
                description = "An einem Ger\u00e4t hast du dein Startgewicht verdoppelt."
            )
        }

        if (hasAllGroupsInOneWeek(histories)) {
            earned += Achievement(
                code = Achievement.ALL_GROUPS_WEEK,
                emoji = "\uD83C\uDFAF",
                title = "Ganzk\u00f6rper-Woche",
                description = "Alle Muskelgruppen in einer Woche trainiert."
            )
        }

        return earned
    }

    private fun sessionMilestone(code: String, count: Int, emoji: String) = Achievement(
        code = code,
        emoji = emoji,
        title = "$count Trainings",
        description = "Du hast $count Trainings abgeschlossen."
    )

    private fun hasDoubledAnyWeight(
        histories: List<WorkoutSessionHistory>,
        startingWeightByExerciseId: Map<Long, Double>
    ): Boolean {
        val maxByExercise = mutableMapOf<Long, Double>()
        for (h in histories) {
            for (se in h.sessionExercises) {
                val exId = se.sessionExercise.exerciseId
                for (log in se.setLogs) {
                    if (!log.completedFlag) continue
                    val current = maxByExercise[exId] ?: 0.0
                    if (log.actualWeightKg > current) maxByExercise[exId] = log.actualWeightKg
                }
            }
        }
        for ((exId, start) in startingWeightByExerciseId) {
            if (start <= 0.0) continue
            val max = maxByExercise[exId] ?: continue
            if (max >= start * 2.0) return true
        }
        return false
    }

    private fun hasAllGroupsInOneWeek(histories: List<WorkoutSessionHistory>): Boolean {
        val requiredGroups = setOf("CHEST", "BACK", "LEGS", "SHOULDERS", "ABS")
        val groupsByWeek = mutableMapOf<Pair<Int, Int>, MutableSet<String>>()
        for (h in histories) {
            val completedAt = h.session.completedAt ?: continue
            val date = parseDateTime(completedAt) ?: continue
            val key = yearWeek(date)
            val groups = groupsByWeek.getOrPut(key) { mutableSetOf() }
            for (se in h.sessionExercises) {
                val group = se.sessionExercise.exerciseMuscleGroup
                if (group.isNotBlank()) groups += group
            }
        }
        return groupsByWeek.values.any { it.containsAll(requiredGroups) }
    }

    private fun yearWeek(date: Date): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            time = date
        }
        // WEEK_OF_YEAR can belong to the previous/next year at year boundaries;
        // pair it with the plain YEAR which is sufficient for relative streak math.
        return cal.get(Calendar.YEAR) to cal.get(Calendar.WEEK_OF_YEAR)
    }

    private fun previousWeek(key: Pair<Int, Int>): Pair<Int, Int> {
        val (year, week) = key
        return if (week > 1) year to (week - 1) else (year - 1) to 52
    }
}
