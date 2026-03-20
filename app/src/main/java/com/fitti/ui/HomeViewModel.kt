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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class HomeUiState(
    val exercises: List<Exercise> = emptyList(),
    val recentSessions: List<WorkoutSessionEntity> = emptyList(),
    val activeSessionId: Long? = null,
    val muscleGroupFreshness: Map<String, MuscleGroupStatus> = emptyMap(),
    val showWeightDialog: Boolean = false,
    val lastWeightKg: Double? = null,
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

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    init {
        viewModelScope.launch { exerciseRepo.ensureSeeded() }

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
                    loggedAt = dateFormat.format(Date())
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
            val sessionId = startWorkoutSessionUseCase(dateFormat.format(Date()))
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
                    try {
                        val sessionDate = dateFormat.parse(completedAt) ?: continue
                        val history = workoutRepo.getSessionExercises(session.id)
                        val hasGroup = history.any { it.exerciseMuscleGroup == group }
                        if (hasGroup && (latestDate == null || sessionDate.after(latestDate))) {
                            latestDate = sessionDate
                        }
                    } catch (_: Exception) { }
                }

                freshness[group] = when {
                    latestDate == null -> MuscleGroupStatus.NEVER
                    daysSince(latestDate) <= 4 -> MuscleGroupStatus.FRESH
                    daysSince(latestDate) <= 6 -> MuscleGroupStatus.STALE
                    else -> MuscleGroupStatus.OVERDUE
                }
            }

            _uiState.update { it.copy(muscleGroupFreshness = freshness) }
        }
    }

    private fun daysSince(date: Date): Long {
        val diff = System.currentTimeMillis() - date.time
        return TimeUnit.MILLISECONDS.toDays(diff)
    }

    private fun isOlderThan7Days(dateStr: String): Boolean {
        return try {
            val date = dateFormat.parse(dateStr) ?: return true
            daysSince(date) >= 7
        } catch (_: Exception) {
            true
        }
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
