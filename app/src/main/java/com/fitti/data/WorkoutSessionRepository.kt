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
        completedFlag: Boolean,
        now: String
    ): Long = dao.saveSet(sessionExerciseId, setNumber, actualWeightKg, actualReps, completedFlag, now)

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

    suspend fun deleteSession(sessionId: Long) =
        dao.deleteSession(sessionId)

    suspend fun setExerciseStartedAt(id: Long, startedAt: String) =
        dao.setExerciseStartedAt(id, startedAt)

    suspend fun setExerciseCompletedAt(id: Long, completedAt: String) =
        dao.setExerciseCompletedAt(id, completedAt)

    suspend fun updateSessionExerciseTargetWeight(id: Long, targetWeight: Double) =
        dao.updateSessionExerciseTargetWeight(id, targetWeight)

    suspend fun getSessionExerciseById(id: Long): SessionExerciseEntity? =
        dao.getSessionExerciseById(id)
}
