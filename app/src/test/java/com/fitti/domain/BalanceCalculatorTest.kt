package com.fitti.domain

import com.fitti.data.SessionExerciseEntity
import com.fitti.data.SessionExerciseWithSetLogs
import com.fitti.data.SetLogEntity
import com.fitti.data.WorkoutSessionEntity
import com.fitti.data.WorkoutSessionHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class BalanceCalculatorTest {

    private val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
    private val now = fmt.parse("24.05.2026 12:00")!!

    @Test
    fun volumeByGroup_sumsCompletedSetsPerGroup() {
        val histories = listOf(
            historyWith(
                completedAt = "20.05.2026 10:00",
                group = "CHEST",
                setLogs = listOf(
                    setLog(weight = 40.0, reps = 12, completed = true), // 480
                    setLog(weight = 45.0, reps = 8, completed = true)   // 360
                )
            ),
            historyWith(
                completedAt = "21.05.2026 10:00",
                group = "LEGS",
                setLogs = listOf(
                    setLog(weight = 100.0, reps = 10, completed = true) // 1000
                )
            )
        )
        val result = BalanceCalculator.volumeByGroup(histories, now = now)
        assertEquals(2, result.size)
        assertEquals(840.0, result["CHEST"]!!, 0.001)
        assertEquals(1000.0, result["LEGS"]!!, 0.001)
    }

    @Test
    fun volumeByGroup_ignoresIncompleteSets() {
        val histories = listOf(
            historyWith(
                completedAt = "20.05.2026 10:00",
                group = "BACK",
                setLogs = listOf(
                    setLog(weight = 200.0, reps = 10, completed = false), // ignored
                    setLog(weight = 50.0, reps = 10, completed = true)    // 500
                )
            )
        )
        val result = BalanceCalculator.volumeByGroup(histories, now = now)
        assertEquals(500.0, result["BACK"]!!, 0.001)
    }

    @Test
    fun volumeByGroup_aggregatesAcrossSessions() {
        val histories = listOf(
            historyWith(
                completedAt = "15.05.2026 10:00",
                group = "LEGS",
                setLogs = listOf(setLog(weight = 80.0, reps = 10, completed = true)) // 800
            ),
            historyWith(
                completedAt = "22.05.2026 10:00",
                group = "LEGS",
                setLogs = listOf(setLog(weight = 80.0, reps = 12, completed = true)) // 960
            )
        )
        val result = BalanceCalculator.volumeByGroup(histories, now = now)
        assertEquals(1760.0, result["LEGS"]!!, 0.001)
    }

    @Test
    fun volumeByGroup_excludesSessionsOlderThanWindow() {
        val histories = listOf(
            historyWith(
                completedAt = "01.05.2026 10:00", // 23 Tage alt, ausserhalb 14-Tage-Fenster
                group = "ARMS",
                setLogs = listOf(setLog(weight = 30.0, reps = 10, completed = true))
            ),
            historyWith(
                completedAt = "22.05.2026 10:00", // 2 Tage alt, im Fenster
                group = "ARMS",
                setLogs = listOf(setLog(weight = 30.0, reps = 12, completed = true)) // 360
            )
        )
        val result = BalanceCalculator.volumeByGroup(histories, now = now)
        assertEquals(360.0, result["ARMS"]!!, 0.001)
    }

    @Test
    fun volumeByGroup_skipsSessionsWithoutCompletedAt() {
        val histories = listOf(
            historyWith(
                completedAt = null,
                group = "ABS",
                setLogs = listOf(setLog(weight = 30.0, reps = 12, completed = true))
            )
        )
        val result = BalanceCalculator.volumeByGroup(histories, now = now)
        assertNull(result["ABS"])
        assertTrue(result.isEmpty())
    }

    @Test
    fun volumeByGroup_ignoresZeroOrNegativeInputs() {
        val histories = listOf(
            historyWith(
                completedAt = "22.05.2026 10:00",
                group = "SHOULDERS",
                setLogs = listOf(
                    setLog(weight = 0.0, reps = 10, completed = true),
                    setLog(weight = 25.0, reps = 0, completed = true),
                    setLog(weight = 25.0, reps = 10, completed = true) // 250
                )
            )
        )
        val result = BalanceCalculator.volumeByGroup(histories, now = now)
        assertEquals(250.0, result["SHOULDERS"]!!, 0.001)
    }

    @Test
    fun normalize_scalesMaxToOne() {
        val raw = mapOf("CHEST" to 500.0, "LEGS" to 1000.0, "BACK" to 750.0)
        val normalized = BalanceCalculator.normalize(raw)
        assertEquals(0.5f, normalized["CHEST"]!!, 0.001f)
        assertEquals(1.0f, normalized["LEGS"]!!, 0.001f)
        assertEquals(0.75f, normalized["BACK"]!!, 0.001f)
    }

    @Test
    fun normalize_emptyReturnsEmpty() {
        assertTrue(BalanceCalculator.normalize(emptyMap()).isEmpty())
    }

    @Test
    fun normalize_allZeroReturnsEmpty() {
        assertTrue(BalanceCalculator.normalize(mapOf("CHEST" to 0.0)).isEmpty())
    }

    private fun setLog(weight: Double, reps: Int, completed: Boolean) = SetLogEntity(
        id = 0,
        sessionExerciseId = 1,
        setNumber = 1,
        actualWeightKg = weight,
        actualReps = reps,
        completedFlag = completed
    )

    private fun historyWith(
        completedAt: String?,
        group: String,
        setLogs: List<SetLogEntity>
    ): WorkoutSessionHistory {
        val session = WorkoutSessionEntity(
            id = 1,
            startedAt = completedAt ?: "01.01.2026 10:00",
            completedAt = completedAt,
            status = WorkoutSessionEntity.STATUS_COMPLETED
        )
        val se = SessionExerciseEntity(
            id = 1,
            sessionId = 1,
            exerciseId = 1,
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
