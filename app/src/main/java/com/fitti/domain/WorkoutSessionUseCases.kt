package com.fitti.domain

import com.fitti.data.SettingsRepository
import com.fitti.data.WorkoutSessionHistory
import com.fitti.data.WorkoutSessionRepository

class StartWorkoutSessionUseCase(
    private val repository: WorkoutSessionRepository,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(startedAt: String): Long = repository.startSession(
        startedAt = startedAt,
        repsMin = settings.repsMin,
        repsMax = settings.repsMax,
        sets = settings.sets,
        restSeconds = settings.restSeconds
    )
}

class SaveSetLogUseCase(
    private val repository: WorkoutSessionRepository
) {
    suspend operator fun invoke(
        sessionExerciseId: Long,
        setNumber: Int,
        actualWeightKg: Double,
        actualReps: Int,
        completedFlag: Boolean
    ): Long = repository.saveSet(sessionExerciseId, setNumber, actualWeightKg, actualReps, completedFlag)
}

class CompleteWorkoutSessionUseCase(
    private val repository: WorkoutSessionRepository
) {
    suspend operator fun invoke(sessionId: Long, completedAt: String): Boolean =
        repository.completeSession(sessionId, completedAt)
}

class GetWorkoutHistoryUseCase(
    private val repository: WorkoutSessionRepository
) {
    suspend operator fun invoke(sessionId: Long): WorkoutSessionHistory? =
        repository.getSessionHistory(sessionId)
}
