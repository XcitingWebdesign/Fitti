package com.fitti.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionExercises(items: List<SessionExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSetLog(setLog: SetLogEntity): Long

    @Query("SELECT * FROM exercises ORDER BY sortOrder ASC")
    suspend fun getAllActiveExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getSessionExercises(sessionId: Long): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_logs WHERE sessionExerciseId = :sessionExerciseId ORDER BY setNumber ASC")
    suspend fun getSetLogs(sessionExerciseId: Long): List<SetLogEntity>

    @Query("SELECT COUNT(*) FROM set_logs WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun countSetLogsForSessionExercise(sessionExerciseId: Long): Int

    @Query("SELECT COUNT(*) FROM set_logs sl INNER JOIN session_exercises se ON se.id = sl.sessionExerciseId WHERE se.sessionId = :sessionId")
    suspend fun countSetLogsForSession(sessionId: Long): Int

    @Query("UPDATE workout_sessions SET status = :newStatus, completedAt = :completedAt WHERE id = :sessionId AND status = :expectedStatus")
    suspend fun updateSessionStatus(sessionId: Long, expectedStatus: String, newStatus: String, completedAt: String): Int

    @Query("SELECT * FROM workout_sessions WHERE status = 'STARTED' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompletedSessions(): Flow<List<WorkoutSessionEntity>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSessionHistory(sessionId: Long): WorkoutSessionHistory?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeSessionHistories(): Flow<List<WorkoutSessionHistory>>

    @Transaction
    suspend fun startSession(
        startedAt: String,
        repsMin: Int,
        repsMax: Int,
        sets: Int,
        restSeconds: Int
    ): Long {
        val sessionId = insertSession(
            WorkoutSessionEntity(
                startedAt = startedAt,
                status = WorkoutSessionEntity.STATUS_STARTED
            )
        )

        val snapshots = getAllActiveExercises().map { exercise ->
            SessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exercise.id,
                exerciseCode = exercise.code,
                exerciseDisplayName = exercise.displayName,
                exerciseMuscleGroup = exercise.muscleGroup,
                targetWeight = exercise.currentWeight,
                targetRepsMin = repsMin,
                targetReps = repsMax,
                targetSets = sets,
                plannedRestSeconds = restSeconds,
                progressionStepKg = exercise.progressionStepKg
            )
        }

        if (snapshots.isNotEmpty()) {
            insertSessionExercises(snapshots)
        }

        return sessionId
    }

    @Transaction
    suspend fun saveSet(
        sessionExerciseId: Long,
        setNumber: Int,
        actualWeightKg: Double,
        actualReps: Int,
        completedFlag: Boolean
    ): Long {
        val expectedNext = countSetLogsForSessionExercise(sessionExerciseId) + 1
        require(setNumber == expectedNext) {
            "setNumber must be sequential and start at 1. Expected: $expectedNext, but was: $setNumber"
        }

        return insertSetLog(
            SetLogEntity(
                sessionExerciseId = sessionExerciseId,
                setNumber = setNumber,
                actualWeightKg = actualWeightKg,
                actualReps = actualReps,
                completedFlag = completedFlag
            )
        )
    }

    @Transaction
    suspend fun completeSession(sessionId: Long, completedAt: String): Boolean {
        val session = getSessionById(sessionId) ?: return false
        if (session.status != WorkoutSessionEntity.STATUS_STARTED) return false
        if (countSetLogsForSession(sessionId) == 0) return false

        return updateSessionStatus(
            sessionId = sessionId,
            expectedStatus = WorkoutSessionEntity.STATUS_STARTED,
            newStatus = WorkoutSessionEntity.STATUS_COMPLETED,
            completedAt = completedAt
        ) == 1
    }
}
