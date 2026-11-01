package com.fitti.domain

import com.fitti.data.ExerciseRepository
import com.fitti.data.SettingsRepository
import com.fitti.data.WorkoutSessionRepository
import kotlinx.coroutines.flow.first

class StartWorkoutSessionUseCase(
    private val repository: WorkoutSessionRepository,
    private val settings: SettingsRepository,
    private val exerciseRepo: ExerciseRepository
) {
    suspend operator fun invoke(startedAt: String): Long = repository.startSession(
        startedAt = startedAt,
        repsMin = settings.repsMin,
        repsMax = settings.repsMax,
        sets = settings.sets,
        restSeconds = settings.restSeconds,
        focusGroups = focusGroups()
    )

    /** Muskelgruppen, die laut Ziel-Radar noch deutlich unter dem Zielwert liegen. */
    private suspend fun focusGroups(): Set<String> {
        val targets = StrengthTargets.fromJson(settings.targetStrengthJson) ?: return emptySet()
        if (targets.isEmpty()) return emptySet()
        val exercises = exerciseRepo.observeExercises().first()
        return TargetRadarCalculator.progressByGroup(exercises, targets)
            .filterValues { it < ExerciseOrderPlanner.FOCUS_PROGRESS_THRESHOLD }
            .keys
    }
}
