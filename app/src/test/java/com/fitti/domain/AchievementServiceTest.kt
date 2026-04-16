package com.fitti.domain

import com.fitti.data.SessionExerciseEntity
import com.fitti.data.SessionExerciseWithSetLogs
import com.fitti.data.SetLogEntity
import com.fitti.data.WorkoutSessionEntity
import com.fitti.data.WorkoutSessionHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AchievementServiceTest {

    private val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    @Test
    fun computeStreak_zeroWhenEmpty() {
        assertEquals(0, AchievementService.computeStreak(emptyList()))
    }

    @Test
    fun computeStreak_countsConsecutiveWeeksWithTwoSessions() {
        // now = Wednesday 15.04.2026 (ISO week 16)
        val now = fmt.parse("15.04.2026 10:00")!!
        val sessions = listOf(
            // Week 16 (Apr 13-19): 2 sessions
            completedSession("14.04.2026 10:00"),
            completedSession("15.04.2026 09:00"),
            // Week 15 (Apr 6-12): 2 sessions
            completedSession("07.04.2026 10:00"),
            completedSession("09.04.2026 10:00"),
            // Week 14 (Mar 30-Apr 5): 2 sessions
            completedSession("31.03.2026 10:00"),
            completedSession("02.04.2026 10:00")
        )
        assertEquals(3, AchievementService.computeStreak(sessions, now = now))
    }

    @Test
    fun computeStreak_breaksWhenWeekHasOnlyOneSession() {
        val now = fmt.parse("15.04.2026 10:00")!!
        val sessions = listOf(
            // Week 16: 2 sessions -> current week qualifies
            completedSession("14.04.2026 10:00"),
            completedSession("15.04.2026 09:00"),
            // Week 15: only 1 session -> streak breaks here
            completedSession("09.04.2026 10:00")
        )
        assertEquals(1, AchievementService.computeStreak(sessions, now = now))
    }

    @Test
    fun computeAchievements_firstSession() {
        val sessions = listOf(completedSession("01.04.2026 10:00"))
        val earned = AchievementService.computeAchievements(sessions, emptyList())
        assertTrue(earned.any { it.code == Achievement.FIRST_SESSION })
    }

    @Test
    fun computeAchievements_sessionMilestones() {
        val sessions = (1..25).map { completedSession(dayOfMarch(it)) }
        val earned = AchievementService.computeAchievements(sessions, emptyList())
        val codes = earned.map { it.code }.toSet()
        assertTrue(Achievement.FIRST_SESSION in codes)
        assertTrue(Achievement.SESSIONS_10 in codes)
        assertTrue(Achievement.SESSIONS_25 in codes)
        assertFalse(Achievement.SESSIONS_50 in codes)
    }

    private fun dayOfMarch(day: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        return fmt.format(cal.time)
    }

    @Test
    fun computeAchievements_strengthDoubled() {
        val history = historyWith(
            exerciseId = 42,
            group = "CHEST",
            setLogs = listOf(SetLogEntity(0, 1, 1, actualWeightKg = 80.0, actualReps = 10, completedFlag = true))
        )
        val earned = AchievementService.computeAchievements(
            sessions = listOf(completedSession("01.04.2026 10:00")),
            histories = listOf(history),
            startingWeightByExerciseId = mapOf(42L to 40.0)
        )
        assertTrue(earned.any { it.code == Achievement.STRENGTH_DOUBLED })
    }

    @Test
    fun computeAchievements_strengthDoubled_skipsWhenNotDoubled() {
        val history = historyWith(
            exerciseId = 42,
            group = "CHEST",
            setLogs = listOf(SetLogEntity(0, 1, 1, actualWeightKg = 60.0, actualReps = 10, completedFlag = true))
        )
        val earned = AchievementService.computeAchievements(
            sessions = listOf(completedSession("01.04.2026 10:00")),
            histories = listOf(history),
            startingWeightByExerciseId = mapOf(42L to 40.0)
        )
        assertFalse(earned.any { it.code == Achievement.STRENGTH_DOUBLED })
    }

    @Test
    fun computeAchievements_allGroupsInOneWeek() {
        val groups = listOf("CHEST", "BACK", "LEGS", "SHOULDERS", "ABS")
        val histories = groups.mapIndexed { i, g ->
            historyWith(
                exerciseId = i.toLong(),
                group = g,
                setLogs = emptyList(),
                completedAt = "0${i + 1}.04.2026 10:00"
            )
        }
        val earned = AchievementService.computeAchievements(
            sessions = groups.mapIndexed { i, _ -> completedSession("0${i + 1}.04.2026 10:00") },
            histories = histories
        )
        assertTrue(earned.any { it.code == Achievement.ALL_GROUPS_WEEK })
    }

    private fun completedSession(at: String) = WorkoutSessionEntity(
        id = 0,
        startedAt = at,
        completedAt = at,
        status = WorkoutSessionEntity.STATUS_COMPLETED
    )

    private fun historyWith(
        exerciseId: Long,
        group: String,
        setLogs: List<SetLogEntity>,
        completedAt: String = "01.04.2026 10:00"
    ): WorkoutSessionHistory {
        val session = WorkoutSessionEntity(
            id = 1,
            startedAt = completedAt,
            completedAt = completedAt,
            status = WorkoutSessionEntity.STATUS_COMPLETED
        )
        val se = SessionExerciseEntity(
            id = 1,
            sessionId = 1,
            exerciseId = exerciseId,
            exerciseCode = "X",
            exerciseMuscleGroup = group,
            targetWeight = 0.0,
            targetReps = 10,
            targetSets = 2,
            plannedRestSeconds = 60,
            progressionStepKg = 2.5
        )
        return WorkoutSessionHistory(
            session = session,
            sessionExercises = listOf(SessionExerciseWithSetLogs(se, setLogs))
        )
    }

}
