package com.fitti.domain

import com.fitti.data.SettingsRepository
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
