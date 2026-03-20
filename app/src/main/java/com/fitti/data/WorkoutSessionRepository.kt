package com.fitti.data

import kotlinx.coroutines.flow.Flow

class WorkoutSessionRepository(
    private val dao: WorkoutSessionDao
) {
    suspend fun startSession(
        startedAt: String,
        repsMin: Int,
        repsMax: Int,
        sets: Int,
        restSeconds: Int
    ): Long = dao.startSession(startedAt, repsMin, repsMax, sets, restSeconds)

    suspend fun saveSet(
        sessionExerciseId: Long,
        setNumber: Int,
        actualWeightKg: Double,
        actualReps: Int,
        completedFlag: Boolean
    ): Long = dao.saveSet(sessionExerciseId, setNumber, actualWeightKg, actualReps, completedFlag)

    suspend fun completeSession(sessionId: Long, completedAt: String): Boolean =
        dao.completeSession(sessionId, completedAt)

    suspend fun getSessionHistory(sessionId: Long): WorkoutSessionHistory? =
        dao.getSessionHistory(sessionId)

    suspend fun getActiveSession(): WorkoutSessionEntity? =
        dao.getActiveSession()

    suspend fun getSessionById(sessionId: Long): WorkoutSessionEntity? =
        dao.getSessionById(sessionId)

    suspend fun getSessionExercises(sessionId: Long): List<SessionExerciseEntity> =
        dao.getSessionExercises(sessionId)

    suspend fun getSetLogs(sessionExerciseId: Long): List<SetLogEntity> =
        dao.getSetLogs(sessionExerciseId)

    fun observeCompletedSessions(): Flow<List<WorkoutSessionEntity>> =
        dao.observeCompletedSessions()

    fun observeSessionHistories(): Flow<List<WorkoutSessionHistory>> =
        dao.observeSessionHistories()
}
