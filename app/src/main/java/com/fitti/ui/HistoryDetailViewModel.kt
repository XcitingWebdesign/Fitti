package com.fitti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitti.data.WorkoutSessionHistory
import com.fitti.data.WorkoutSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryDetailUiState(
    val history: WorkoutSessionHistory? = null,
    val isLoading: Boolean = true
)

class HistoryDetailViewModel(
    sessionId: Long,
    workoutRepo: WorkoutSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryDetailUiState())
    val uiState: StateFlow<HistoryDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val history = workoutRepo.getSessionHistory(sessionId)
            _uiState.value = HistoryDetailUiState(history = history, isLoading = false)
        }
    }
}

class HistoryDetailViewModelFactory(
    private val sessionId: Long,
    private val workoutRepo: WorkoutSessionRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HistoryDetailViewModel(sessionId, workoutRepo) as T
    }
}
