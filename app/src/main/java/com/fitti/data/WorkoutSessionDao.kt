package com.fitti.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fitti.domain.ExerciseOrderPlanner
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** completedAt ("dd.MM.yyyy HH:mm" oder ISO) als chronologisch sortierbarer SQL-Ausdruck. */
private const val COMPLETED_AT_SORTABLE =
    "CASE WHEN substr(completedAt, 3, 1) = '.' THEN substr(completedAt, 7, 4) || '-' || substr(completedAt, 4, 2) || '-' || substr(completedAt, 1, 2) || 'T' || substr(completedAt, 12, 5) ELSE completedAt END"

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

    /**
     * Alle Geraete, die in einer abgeschlossenen Session vollstaendig (Saetze >= Soll)
     * trainiert wurden. Das juengste Datum pro Geraet bestimmt ExerciseOrderPlanner
     * in Kotlin, weil startedAt als "dd.MM.yyyy HH:mm" nicht lexikalisch sortierbar ist.
     */
    @Query("""
        SELECT se.exerciseId AS exerciseId, ws.startedAt AS startedAt
        FROM session_exercises se
        INNER JOIN workout_sessions ws ON ws.id = se.sessionId
        WHERE ws.status = 'COMPLETED'
          AND (SELECT COUNT(*) FROM set_logs sl WHERE sl.sessionExerciseId = se.id) >= se.targetSets
    """)
    suspend fun getFullyTrainedDates(): List<ExerciseTrainedOn>

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY " + COMPLETED_AT_SORTABLE + " DESC")
    fun observeCompletedSessions(): Flow<List<WorkoutSessionEntity>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSessionHistory(sessionId: Long): WorkoutSessionHistory?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY " + COMPLETED_AT_SORTABLE + " DESC")
    fun observeSessionHistories(): Flow<List<WorkoutSessionHistory>>

    // Full backup queries
    @Query("SELECT * FROM workout_sessions ORDER BY id ASC")
    suspend fun getAllSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM session_exercises ORDER BY id ASC")
    suspend fun getAllSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_logs ORDER BY id ASC")
    suspend fun getAllSetLogs(): List<SetLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetLogs(setLogs: List<SetLogEntity>)

    @Query("DELETE FROM set_logs")
    suspend fun deleteAllSetLogs()

    @Query("DELETE FROM session_exercises")
    suspend fun deleteAllSessionExercises()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("UPDATE session_exercises SET exerciseStartedAt = :startedAt WHERE id = :id AND exerciseStartedAt IS NULL")
    suspend fun setExerciseStartedAt(id: Long, startedAt: String)

    @Query("UPDATE session_exercises SET targetWeight = :targetWeight WHERE id = :id")
    suspend fun updateSessionExerciseTargetWeight(id: Long, targetWeight: Double)

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getSessionExerciseById(id: Long): SessionExerciseEntity?

    @Query("UPDATE session_exercises SET exerciseCompletedAt = :completedAt WHERE id = :id")
    suspend fun setExerciseCompletedAt(id: Long, completedAt: String)

    @Query("UPDATE workout_sessions SET lastActivityAt = :lastActivityAt WHERE id = :sessionId")
    suspend fun updateLastActivity(sessionId: Long, lastActivityAt: String)

    @Query("SELECT sessionId FROM session_exercises WHERE id = :sessionExerciseId")
    suspend fun getSessionIdForExercise(sessionExerciseId: Long): Long?

    @Transaction
    suspend fun startSession(
        startedAt: String,
        repsMin: Int,
        repsMax: Int,
        sets: Int,
        restSeconds: Int,
        focusGroups: Set<String> = emptySet(),
        today: LocalDate = LocalDate.now()
    ): Long {
        val sessionId = insertSession(
            WorkoutSessionEntity(
                startedAt = startedAt,
                status = WorkoutSessionEntity.STATUS_STARTED,
                lastActivityAt = startedAt
            )
        )

        val ordered = ExerciseOrderPlanner.order(
            exercises = getAllActiveExercises(),
            trainedOn = getFullyTrainedDates(),
            focusGroups = focusGroups,
            today = today
        )
        val snapshots = ordered.map { exercise ->
            SessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exercise.id,
                exerciseCode = exercise.code,
                exerciseDisplayName = exercise.displayName,
                exerciseMuscleGroup = exercise.muscleGroup,
                exerciseSeatPosition = exercise.seatPosition,
                exercisePadPosition = exercise.padPosition,
                targetWeight = exercise.currentWeight,
                targetRepsMin = repsMin,
                targetReps = repsMax,
                targetSets = sets,
                plannedRestSeconds = restSeconds,
                progressionStepKg = exercise.progressionStepKg,
                weightSteps = exercise.weightSteps
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
        completedFlag: Boolean,
        now: String
    ): Long {
        val expectedNext = countSetLogsForSessionExercise(sessionExerciseId) + 1
        require(setNumber == expectedNext) {
            "setNumber must be sequential and start at 1. Expected: $expectedNext, but was: $setNumber"
        }

        val logId = insertSetLog(
            SetLogEntity(
                sessionExerciseId = sessionExerciseId,
                setNumber = setNumber,
                actualWeightKg = actualWeightKg,
                actualReps = actualReps,
                completedFlag = completedFlag
            )
        )

        val sessionId = getSessionIdForExercise(sessionExerciseId)
        if (sessionId != null) {
            updateLastActivity(sessionId, now)
        }

        return logId
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
