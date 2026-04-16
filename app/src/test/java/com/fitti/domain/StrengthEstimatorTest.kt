package com.fitti.domain

import com.fitti.data.SessionExerciseEntity
import com.fitti.data.SessionExerciseWithSetLogs
import com.fitti.data.SetLogEntity
import com.fitti.data.WorkoutSessionEntity
import com.fitti.data.WorkoutSessionHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthEstimatorTest {

    @Test
    fun estimate1RM_epley() {
        // 100kg x 10 reps -> 100 * (1 + 10/30) = 133.33...
        assertEquals(133.333, StrengthEstimator.estimate1RM(100.0, 10), 0.01)
    }

    @Test
    fun estimate1RM_zeroInputs() {
        assertEquals(0.0, StrengthEstimator.estimate1RM(0.0, 10), 0.0001)
        assertEquals(0.0, StrengthEstimator.estimate1RM(50.0, 0), 0.0001)
        assertEquals(0.0, StrengthEstimator.estimate1RM(-10.0, 5), 0.0001)
    }

    @Test
    fun groupMax_takesHighestAcrossCompletedSets() {
        val histories = listOf(
            historyWith(
                group = "CHEST",
                setLogs = listOf(
                    setLog(weight = 40.0, reps = 12, completed = true),   // 1RM ~= 56
                    setLog(weight = 45.0, reps = 8, completed = true)     // 1RM = 57
                )
            ),
            historyWith(
                group = "LEGS",
                setLogs = listOf(
                    setLog(weight = 100.0, reps = 10, completed = true)   // 1RM ~= 133
                )
            )
        )
        val result = StrengthEstimator.groupMax(histories)
        assertEquals(2, result.size)
        assertEquals(57.0, result["CHEST"]!!, 0.1)
        assertEquals(133.33, result["LEGS"]!!, 0.1)
    }

    @Test
    fun groupMax_ignoresIncompleteSets() {
        val histories = listOf(
            historyWith(
                group = "BACK",
                setLogs = listOf(
                    setLog(weight = 200.0, reps = 10, completed = false), // ignored
                    setLog(weight = 50.0, reps = 10, completed = true)
                )
            )
        )
        val result = StrengthEstimator.groupMax(histories)
        assertEquals(66.67, result["BACK"]!!, 0.1)
    }

    @Test
    fun normalize_scalesMaxToOne() {
        val raw = mapOf("CHEST" to 50.0, "LEGS" to 100.0, "BACK" to 75.0)
        val normalized = StrengthEstimator.normalize(raw)
        assertEquals(0.5f, normalized["CHEST"]!!, 0.001f)
        assertEquals(1.0f, normalized["LEGS"]!!, 0.001f)
        assertEquals(0.75f, normalized["BACK"]!!, 0.001f)
    }

    @Test
    fun normalize_emptyReturnsEmpty() {
        assertTrue(StrengthEstimator.normalize(emptyMap()).isEmpty())
    }

    private fun setLog(weight: Double, reps: Int, completed: Boolean) = SetLogEntity(
        id = 0,
        sessionExerciseId = 1,
        setNumber = 1,
        actualWeightKg = weight,
        actualReps = reps,
        completedFlag = completed
    )

    private fun historyWith(group: String, setLogs: List<SetLogEntity>): WorkoutSessionHistory {
        val session = WorkoutSessionEntity(
            id = 1,
            startedAt = "01.01.2026 10:00",
            completedAt = "01.01.2026 11:00",
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
