package com.fitti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitti.data.ExerciseRepository
import com.fitti.data.SettingsRepository
import com.fitti.data.WeightLogDao
import com.fitti.data.WeightLogEntity
import com.fitti.data.WorkoutSessionEntity
import com.fitti.data.WorkoutSessionRepository
import com.fitti.domain.Exercise
import com.fitti.domain.StartWorkoutSessionUseCase
import com.fitti.ui.common.FRESHNESS_FRESH_DAYS
import com.fitti.ui.common.FRESHNESS_STALE_DAYS
import com.fitti.ui.common.WEIGHT_LOG_INTERVAL_DAYS
import com.fitti.ui.common.daysSince
import com.fitti.ui.common.formatDateTime
import com.fitti.ui.common.parseDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class HomeUiState(
    val exercises: List<Exercise> = emptyList(),
    val recentSessions: List<WorkoutSessionEntity> = emptyList(),
    val activeSessionId: Long? = null,
    val muscleGroupFreshness: Map<String, MuscleGroupStatus> = emptyMap(),
    val showWeightDialog: Boolean = false,
    val lastWeightKg: Double? = null,
    val goal: String = "",
    val weightLogs: List<WeightLogEntity> = emptyList(),
    val isLoading: Boolean = true
)

enum class MuscleGroupStatus { FRESH, STALE, OVERDUE, NEVER }

class HomeViewModel(
    private val exerciseRepo: ExerciseRepository,
    private val workoutRepo: WorkoutSessionRepository,
    private val settingsRepo: SettingsRepository,
    private val weightLogDao: WeightLogDao,
    private val startWorkoutSessionUseCase: StartWorkoutSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { exerciseRepo.ensureSeeded() }

        _uiState.update { it.copy(goal = settingsRepo.goal) }

        viewModelScope.launch {
            exerciseRepo.observeExercises().collect { exercises ->
                _uiState.update { it.copy(exercises = exercises) }
                updateMuscleGroupFreshness(exercises)
            }
        }

        viewModelScope.launch {
            workoutRepo.observeCompletedSessions().collect { sessions ->
                _uiState.update { it.copy(recentSessions = sessions, isLoading = false) }
                updateMuscleGroupFreshness(_uiState.value.exercises)
            }
        }

        viewModelScope.launch {
            val active = workoutRepo.getActiveSession()
            _uiState.update { it.copy(activeSessionId = active?.id) }
        }

        viewModelScope.launch {
            val logs = weightLogDao.getAll()
            _uiState.update { it.copy(weightLogs = logs) }
        }
    }

    fun startOrContinueWorkout(onSessionReady: (Long) -> Unit) {
        viewModelScope.launch {
            // Check if weight needs to be logged
            val latestWeight = weightLogDao.getLatest()
            val needsWeight = latestWeight == null || isOlderThan7Days(latestWeight.loggedAt)

            if (needsWeight) {
                _uiState.update {
                    it.copy(
                        showWeightDialog = true,
                        lastWeightKg = latestWeight?.weightKg
                    )
                }
                return@launch
            }

            proceedToWorkout(onSessionReady)
        }
    }

    fun onWeightEntered(weightKg: Double, onSessionReady: (Long) -> Unit) {
        viewModelScope.launch {
            weightLogDao.insert(
                WeightLogEntity(
                    weightKg = weightKg,
                    loggedAt = formatDateTime(Date())
                )
            )
            _uiState.update { it.copy(showWeightDialog = false) }
            proceedToWorkout(onSessionReady)
        }
    }

    fun dismissWeightDialog(onSessionReady: (Long) -> Unit) {
        _uiState.update { it.copy(showWeightDialog = false) }
        viewModelScope.launch { proceedToWorkout(onSessionReady) }
    }

    private suspend fun proceedToWorkout(onSessionReady: (Long) -> Unit) {
        val activeId = _uiState.value.activeSessionId
        if (activeId != null) {
            onSessionReady(activeId)
        } else {
            val sessionId = startWorkoutSessionUseCase(formatDateTime(Date()))
            _uiState.update { it.copy(activeSessionId = sessionId) }
            onSessionReady(sessionId)
        }
    }

    private fun updateMuscleGroupFreshness(exercises: List<Exercise>) {
        viewModelScope.launch {
            val sessions = _uiState.value.recentSessions
            val groups = exercises.map { it.muscleGroup }.filter { it.isNotEmpty() }.distinct()
            val freshness = mutableMapOf<String, MuscleGroupStatus>()

            for (group in groups) {
                val exerciseIds = exercises.filter { it.muscleGroup == group }.map { it.id }.toSet()
                var latestDate: Date? = null

                for (session in sessions) {
                    val completedAt = session.completedAt ?: continue
                    val sessionDate = parseDateTime(completedAt) ?: continue
                    val history = workoutRepo.getSessionExercises(session.id)
                    val hasGroup = history.any { it.exerciseMuscleGroup == group }
                    if (hasGroup && (latestDate == null || sessionDate.after(latestDate))) {
                        latestDate = sessionDate
                    }
                }

                freshness[group] = when {
                    latestDate == null -> MuscleGroupStatus.NEVER
                    daysSince(latestDate) <= FRESHNESS_FRESH_DAYS -> MuscleGroupStatus.FRESH
                    daysSince(latestDate) <= FRESHNESS_STALE_DAYS -> MuscleGroupStatus.STALE
                    else -> MuscleGroupStatus.OVERDUE
                }
            }

            _uiState.update { it.copy(muscleGroupFreshness = freshness) }
        }
    }

    private fun isOlderThan7Days(dateStr: String): Boolean {
        val date = parseDateTime(dateStr) ?: return true
        return daysSince(date) >= WEIGHT_LOG_INTERVAL_DAYS
    }
}

class HomeViewModelFactory(
    private val exerciseRepo: ExerciseRepository,
    private val workoutRepo: WorkoutSessionRepository,
    private val settingsRepo: SettingsRepository,
    private val weightLogDao: WeightLogDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(
            exerciseRepo = exerciseRepo,
            workoutRepo = workoutRepo,
            settingsRepo = settingsRepo,
            weightLogDao = weightLogDao,
            startWorkoutSessionUseCase = StartWorkoutSessionUseCase(workoutRepo, settingsRepo)
        ) as T
    }
}
