package com.fitti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitti.data.ExerciseRepository
import com.fitti.domain.CompleteWorkoutSessionUseCase
import com.fitti.domain.Exercise
import com.fitti.domain.SaveSetLogUseCase
import com.fitti.domain.StartWorkoutSessionUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val exercises: List<Exercise> = emptyList()
)

class MainViewModel(
    private val repository: ExerciseRepository,
    private val startWorkoutSessionUseCase: StartWorkoutSessionUseCase,
    private val saveSetLogUseCase: SaveSetLogUseCase,
    private val completeWorkoutSessionUseCase: CompleteWorkoutSessionUseCase
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = repository.observeExercises()
        .map { MainUiState(exercises = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    suspend fun startSession(startedAt: String): Long = startWorkoutSessionUseCase(startedAt)

    suspend fun logSet(
        sessionExerciseId: Long,
        setNumber: Int,
        actualWeightKg: Double,
        actualReps: Int,
        completedFlag: Boolean
    ): Long = saveSetLogUseCase(
        sessionExerciseId = sessionExerciseId,
        setNumber = setNumber,
        actualWeightKg = actualWeightKg,
        actualReps = actualReps,
        completedFlag = completedFlag
    )

    suspend fun completeSession(sessionId: Long, completedAt: String): Boolean =
        completeWorkoutSessionUseCase(sessionId, completedAt)
}

class MainViewModelFactory(
    private val repository: ExerciseRepository,
    private val startWorkoutSessionUseCase: StartWorkoutSessionUseCase,
    private val saveSetLogUseCase: SaveSetLogUseCase,
    private val completeWorkoutSessionUseCase: CompleteWorkoutSessionUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                repository,
                startWorkoutSessionUseCase,
                saveSetLogUseCase,
                completeWorkoutSessionUseCase
            ) as T
        }
        error("Unknown ViewModel class: $modelClass")
    }
}
