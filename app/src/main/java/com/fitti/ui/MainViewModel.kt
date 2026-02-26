package com.fitti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitti.data.ExerciseRepository
import com.fitti.domain.Exercise
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val exercises: List<Exercise> = emptyList()
)

class MainViewModel(
    private val repository: ExerciseRepository
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = repository.observeExercises()
        .map { MainUiState(exercises = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }
}

class MainViewModelFactory(
    private val repository: ExerciseRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        error("Unknown ViewModel class: $modelClass")
    }
}
