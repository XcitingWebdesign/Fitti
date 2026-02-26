package com.fitti.data

class WorkoutSessionRepository(
    private val dao: WorkoutSessionDao
) {
    suspend fun startSession(startedAt: String): Long = dao.startSession(startedAt)

    suspend fun saveSet(
        sessionExerciseId: Long,
        setNumber: Int,
        actualWeightKg: Double,
        actualReps: Int,
        completedFlag: Boolean
    ): Long = dao.saveSet(sessionExerciseId, setNumber, actualWeightKg, actualReps, completedFlag)

    suspend fun completeSession(sessionId: Long, completedAt: String): Boolean =
        dao.completeSession(sessionId, completedAt)

    suspend fun getSessionHistory(sessionId: Long): WorkoutSessionHistory? = dao.getSessionHistory(sessionId)
}
