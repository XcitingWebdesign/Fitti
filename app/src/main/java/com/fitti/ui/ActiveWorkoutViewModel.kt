package com.fitti.ui

import android.app.Application
import android.media.RingtoneManager
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.VibratorManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitti.data.ExerciseRepository
import com.fitti.data.SessionExerciseEntity
import com.fitti.data.SetLogEntity
import com.fitti.data.WorkoutSessionRepository
import com.fitti.domain.ProgressionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

data class ActiveWorkoutUiState(
    val currentExercise: SessionExerciseEntity? = null,
    val completedSets: List<SetLogEntity> = emptyList(),
    val currentSetNumber: Int = 1,
    val totalExercises: Int = 0,
    val completedExerciseCount: Int = 0,
    val skippedCount: Int = 0,
    val timerState: TimerState = TimerState.Idle,
    val showProgressionDialog: Boolean = false,
    val nextWeight: Double = 0.0,
    val isWorkoutComplete: Boolean = false,
    val isProcessing: Boolean = false,
    val sessionSummary: SessionSummary? = null,
    val isLoading: Boolean = true
)

sealed class TimerState {
    object Idle : TimerState()
    data class Running(val secondsRemaining: Int, val totalSeconds: Int) : TimerState()
    object Finished : TimerState()
}

data class SessionSummary(
    val durationMinutes: Long,
    val exercisesCompleted: Int,
    val totalExercises: Int,
    val weightChanges: List<WeightChange>
)

data class WeightChange(
    val exerciseName: String,
    val oldWeight: Double,
    val newWeight: Double,
    val weightUnit: String
)

class ActiveWorkoutViewModel(
    private val sessionId: Long,
    private val workoutRepo: WorkoutSessionRepository,
    private val exerciseRepo: ExerciseRepository,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveWorkoutUiState())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    private val exerciseQueue = ArrayDeque<SessionExerciseEntity>()
    // exerciseId -> nextWeight (stores the actual target, not just a boolean)
    private val progressionDecisions = mutableMapOf<Long, Double>()
    private val weightChanges = mutableListOf<WeightChange>()
    private var countDownTimer: CountDownTimer? = null
    private var sessionStartTime: Date? = null

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            // Fix 1a: use sessionId, not getActiveSession()
            val session = workoutRepo.getSessionById(sessionId) ?: return@launch
            sessionStartTime = try { dateFormat.parse(session.startedAt) } catch (_: Exception) { Date() }

            val allExercises = workoutRepo.getSessionExercises(sessionId)
            val totalExercises = allExercises.size
            var completedCount = 0

            for (exercise in allExercises) {
                val logs = workoutRepo.getSetLogs(exercise.id)
                if (logs.size >= exercise.targetSets) {
                    completedCount++
                } else {
                    exerciseQueue.add(exercise)
                }
            }

            _uiState.update {
                it.copy(
                    totalExercises = totalExercises,
                    completedExerciseCount = completedCount,
                    isLoading = false
                )
            }

            showCurrentExercise()
        }
    }

    private suspend fun showCurrentExercise() {
        val current = exerciseQueue.peek()
        if (current == null) {
            finishWorkout()
            return
        }

        val logs = workoutRepo.getSetLogs(current.id)
        _uiState.update {
            it.copy(
                currentExercise = current,
                completedSets = logs,
                currentSetNumber = logs.size + 1,
                timerState = TimerState.Idle,
                showProgressionDialog = false,
                isProcessing = false
            )
        }
    }

    fun onSetLogged(reps: Int) {
        if (_uiState.value.isProcessing) return
        _uiState.update { it.copy(isProcessing = true) }

        viewModelScope.launch {
            val exercise = _uiState.value.currentExercise ?: return@launch
            val setNumber = _uiState.value.currentSetNumber
            val completed = reps >= exercise.targetRepsMin

            workoutRepo.saveSet(
                sessionExerciseId = exercise.id,
                setNumber = setNumber,
                actualWeightKg = exercise.targetWeight,
                actualReps = reps,
                completedFlag = completed
            )

            val logs = workoutRepo.getSetLogs(exercise.id)
            _uiState.update {
                it.copy(
                    completedSets = logs,
                    currentSetNumber = logs.size + 1
                )
            }

            if (logs.size >= exercise.targetSets) {
                if (ProgressionService.isEligibleForProgression(logs, exercise.targetSets, exercise.targetReps)) {
                    val nextWeight = ProgressionService.calculateNextWeight(
                        exercise.targetWeight,
                        exercise.progressionStepKg
                    )
                    _uiState.update {
                        it.copy(
                            showProgressionDialog = true,
                            nextWeight = nextWeight,
                            timerState = TimerState.Idle,
                            isProcessing = false
                        )
                    }
                } else {
                    // Not all sets qualified — skip progression, move to next exercise
                    exerciseQueue.poll()
                    _uiState.update {
                        it.copy(
                            completedExerciseCount = it.completedExerciseCount + 1,
                            isProcessing = false
                        )
                    }
                    showCurrentExercise()
                }
            } else {
                _uiState.update { it.copy(isProcessing = false) }
                startRestTimer(exercise.plannedRestSeconds)
            }
        }
    }

    fun onProgressionDecision(shouldProgress: Boolean) {
        viewModelScope.launch {
            val exercise = _uiState.value.currentExercise ?: return@launch

            if (shouldProgress) {
                val nextWeight = _uiState.value.nextWeight
                // Fix 1c: store the actual next weight directly
                progressionDecisions[exercise.exerciseId] = nextWeight

                val exerciseEntity = exerciseRepo.getById(exercise.exerciseId)
                weightChanges.add(
                    WeightChange(
                        exerciseName = exercise.exerciseDisplayName.ifEmpty { exercise.exerciseCode },
                        oldWeight = exercise.targetWeight,
                        newWeight = nextWeight,
                        weightUnit = exerciseEntity?.weightUnit ?: "kg"
                    )
                )
            }

            exerciseQueue.poll()
            _uiState.update {
                it.copy(
                    completedExerciseCount = it.completedExerciseCount + 1,
                    showProgressionDialog = false
                )
            }

            showCurrentExercise()
        }
    }

    fun onSkipExercise() {
        viewModelScope.launch {
            val skipped = exerciseQueue.poll() ?: return@launch
            exerciseQueue.add(skipped)
            _uiState.update { it.copy(skippedCount = it.skippedCount + 1) }
            showCurrentExercise()
        }
    }

    fun onTimerSkipped() {
        countDownTimer?.cancel()
        _uiState.update { it.copy(timerState = TimerState.Idle) }
    }

    fun onEndWorkoutEarly() {
        viewModelScope.launch {
            finishWorkout()
        }
    }

    private suspend fun finishWorkout() {
        countDownTimer?.cancel()
        val now = dateFormat.format(Date())

        // Fix 1b: check completeSession result
        val completed = workoutRepo.completeSession(sessionId, now)
        if (!completed) {
            // Session might already be completed or have no logs - still show summary
        }

        // Fix 1c: apply stored next weights directly
        for ((exerciseId, nextWeight) in progressionDecisions) {
            exerciseRepo.updateWeight(exerciseId, nextWeight, now.substringBefore(" "))
        }

        val durationMinutes = sessionStartTime?.let {
            (System.currentTimeMillis() - it.time) / 60000
        } ?: 0

        _uiState.update {
            it.copy(
                isWorkoutComplete = true,
                currentExercise = null,
                timerState = TimerState.Idle,
                sessionSummary = SessionSummary(
                    durationMinutes = durationMinutes,
                    exercisesCompleted = it.completedExerciseCount,
                    totalExercises = it.totalExercises,
                    weightChanges = weightChanges.toList()
                )
            )
        }
    }

    private fun startRestTimer(seconds: Int) {
        countDownTimer?.cancel()
        _uiState.update { it.copy(timerState = TimerState.Running(seconds, seconds)) }

        countDownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toInt() + 1
                _uiState.update {
                    it.copy(timerState = TimerState.Running(remaining, seconds))
                }
            }

            override fun onFinish() {
                _uiState.update { it.copy(timerState = TimerState.Finished) }
                playNotificationSound()
                vibrate()
            }
        }.start()
    }

    private fun playNotificationSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(application, uri)
            ringtone?.play()
        } catch (_: Exception) { }
    }

    private fun vibrate() {
        try {
            val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { }
    }

    override fun onCleared() {
        super.onCleared()
        countDownTimer?.cancel()
    }
}

class ActiveWorkoutViewModelFactory(
    private val sessionId: Long,
    private val workoutRepo: WorkoutSessionRepository,
    private val exerciseRepo: ExerciseRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ActiveWorkoutViewModel(sessionId, workoutRepo, exerciseRepo, application) as T
    }
}
