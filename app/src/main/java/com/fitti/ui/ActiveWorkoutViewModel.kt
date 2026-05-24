package com.fitti.ui

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.VibratorManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitti.data.CoachingPlanDao
import com.fitti.data.CoachingPlanExerciseTargetEntity
import com.fitti.data.CoachingPlanWithTargets
import com.fitti.data.ExerciseRepository
import com.fitti.data.SessionExerciseEntity
import com.fitti.data.SetLogEntity
import com.fitti.data.WorkoutSessionRepository
import com.fitti.domain.ProgressionService
import com.fitti.notifications.RestTimerService
import com.fitti.ui.common.formatDateTime
import com.fitti.ui.common.parseDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.Date
import kotlin.math.sin

data class ActiveWorkoutUiState(
    val currentExercise: SessionExerciseEntity? = null,
    val completedSets: List<SetLogEntity> = emptyList(),
    val currentSetNumber: Int = 1,
    val totalExercises: Int = 0,
    val completedExerciseCount: Int = 0,
    val skippedCount: Int = 0,
    val timerState: TimerState = TimerState.Idle,
    val isExerciseTransition: Boolean = false,
    val showProgressionDialog: Boolean = false,
    val nextWeight: Double = 0.0,
    val progressionAction: String = "progress",
    val progressionCoachReason: String = "",
    val showCoachIntakeDialog: Boolean = false,
    val coachIntakeWeight: Double = 0.0,
    val coachIntakeAction: String = "progress",
    val coachIntakeReason: String = "",
    val isWorkoutComplete: Boolean = false,
    val isProcessing: Boolean = false,
    val sessionSummary: SessionSummary? = null,
    val isLoading: Boolean = true,
    val nextExerciseName: String = "",
    val nextExerciseSeatPosition: String = "",
    val nextExercisePadPosition: String = "",
    val nextExerciseTargetWeight: Double = 0.0,
    val nextExerciseRepRange: String = ""
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
    val weightUnit: String,
    val coachAction: String = "progress",
    val coachReason: String = ""
)

class ActiveWorkoutViewModel(
    private val sessionId: Long,
    private val workoutRepo: WorkoutSessionRepository,
    private val exerciseRepo: ExerciseRepository,
    private val coachingPlanDao: CoachingPlanDao,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveWorkoutUiState())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    private val exerciseQueue = ArrayDeque<SessionExerciseEntity>()
    // exerciseId -> nextWeight (stores the actual target, not just a boolean)
    private val progressionDecisions = mutableMapOf<Long, Double>()
    private val weightChanges = mutableListOf<WeightChange>()
    private var activePlan: CoachingPlanWithTargets? = null
    private var countDownTimer: CountDownTimer? = null
    private var sessionStartTime: Date? = null
    private var advanceToNextExerciseAfterTimer = false
    private var pendingChimeKind: ChimeKind = ChimeKind.SET

    private enum class ChimeKind { SET, EXERCISE }

    private fun peekNextExerciseName(): String {
        val next = exerciseQueue.peek() ?: return ""
        return next.exerciseDisplayName.ifEmpty { next.exerciseCode }
    }

    private fun peekNextExerciseSeatPosition(): String =
        exerciseQueue.peek()?.exerciseSeatPosition ?: ""

    private fun peekNextExercisePadPosition(): String =
        exerciseQueue.peek()?.exercisePadPosition ?: ""

    private fun peekNextExerciseTargetWeight(): Double =
        exerciseQueue.peek()?.targetWeight ?: 0.0

    private fun peekNextExerciseRepRange(): String {
        val next = exerciseQueue.peek() ?: return ""
        return if (next.targetRepsMin == next.targetReps) {
            "${next.targetReps} Wdh"
        } else {
            "${next.targetRepsMin}–${next.targetReps} Wdh"
        }
    }

    /**
     * Maps a coach-suggested weight to the nearest valid stack step (>= suggested),
     * or rounds to 0.5 kg when no stack is configured.
     */
    private fun normalizeCoachWeight(suggested: Double, weightSteps: String): Double {
        if (suggested <= 0.0) return suggested
        val steps = ProgressionService.parseWeightSteps(weightSteps)
        if (steps.isNotEmpty()) {
            val match = steps.firstOrNull { it >= suggested - 0.01 }
            if (match != null) return match
            return steps.last()
        }
        return ProgressionService.roundToHalf(suggested)
    }

    /**
     * Verletzungsschutz: Egal was der Coach vorschlägt – ein Progress-Vorschlag
     * darf höchstens EINE Stack-Stufe (oder progressionStepKg) über dem aktuellen
     * Gewicht liegen. Verhindert wilde +10-kg-Sprünge auf kleinen Geräten.
     */
    private fun capProgressionStep(
        current: Double,
        suggested: Double,
        weightSteps: String,
        progressionStepKg: Double
    ): Double {
        if (suggested <= current + 0.01) return suggested
        val steps = ProgressionService.parseWeightSteps(weightSteps)
        if (steps.isNotEmpty()) {
            val nextStep = steps.firstOrNull { it > current + 0.01 } ?: return current
            return minOf(suggested, nextStep)
        }
        val step = if (progressionStepKg > 0.0) progressionStepKg else 2.5
        val cap = ProgressionService.roundToHalf(current + step)
        return minOf(suggested, cap)
    }

    /**
     * Analog zu capProgressionStep, aber für deload: höchstens EINE Stufe unter
     * dem aktuellen Gewicht.
     */
    private fun capDeloadStep(
        current: Double,
        suggested: Double,
        weightSteps: String,
        progressionStepKg: Double
    ): Double {
        if (suggested >= current - 0.01) return suggested
        val steps = ProgressionService.parseWeightSteps(weightSteps)
        if (steps.isNotEmpty()) {
            val prevStep = steps.lastOrNull { it < current - 0.01 } ?: return current
            return maxOf(suggested, prevStep)
        }
        val step = if (progressionStepKg > 0.0) progressionStepKg else 2.5
        val floor = ProgressionService.roundToHalf(current - step)
        return maxOf(suggested, floor)
    }

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            // Fix 1a: use sessionId, not getActiveSession()
            val session = workoutRepo.getSessionById(sessionId) ?: return@launch
            sessionStartTime = parseDateTime(session.startedAt) ?: Date()
            activePlan = coachingPlanDao.getLatest()

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

        // Vor dem ersten Satz: Wenn ein offener Coach-Vorschlag existiert, der das
        // Ausgangsgewicht aendern wuerde, dem Nutzer die Wahl ueberlassen statt
        // ihn stillschweigend anzuwenden.
        if (logs.isEmpty()) {
            val coachTarget = activePlan?.targetFor(current.exerciseCode)
            if (coachTarget != null && coachTarget.decision == null) {
                val intake = buildCoachIntake(current, coachTarget)
                if (intake != null) {
                    _uiState.update {
                        it.copy(
                            currentExercise = current,
                            completedSets = logs,
                            currentSetNumber = 1,
                            timerState = TimerState.Idle,
                            showProgressionDialog = false,
                            showCoachIntakeDialog = true,
                            coachIntakeWeight = intake.first,
                            coachIntakeAction = intake.second,
                            coachIntakeReason = coachTarget.reasonText,
                            isProcessing = false
                        )
                    }
                    return
                } else {
                    // hold-Vorschlag oder identisches Gewicht: kein Dialog noetig,
                    // aber als entschieden markieren, damit er nicht spaeter wieder auftaucht.
                    coachingPlanDao.updateTargetDecision(coachTarget.id, "auto")
                    refreshActivePlan()
                }
            }
        }

        workoutRepo.setExerciseStartedAt(current.id, formatDateTime(Date()))

        _uiState.update {
            it.copy(
                currentExercise = current,
                completedSets = logs,
                currentSetNumber = logs.size + 1,
                timerState = TimerState.Idle,
                showProgressionDialog = false,
                showCoachIntakeDialog = false,
                isProcessing = false
            )
        }
    }

    /**
     * Liefert (vorgeschlagenes Gewicht, action) wenn der Coach-Vorschlag einen
     * Gewichts-Wechsel bedeutet, sonst null (z.B. action="hold" oder identisches Gewicht).
     */
    private fun buildCoachIntake(
        exercise: SessionExerciseEntity,
        coachTarget: CoachingPlanExerciseTargetEntity
    ): Pair<Double, String>? {
        val action = coachTarget.action
        return when {
            action == "progress" && coachTarget.targetWeightKg > 0.0 -> {
                val raw = normalizeCoachWeight(coachTarget.targetWeightKg, exercise.weightSteps)
                val capped = capProgressionStep(
                    current = exercise.targetWeight,
                    suggested = raw,
                    weightSteps = exercise.weightSteps,
                    progressionStepKg = exercise.progressionStepKg
                )
                if (capped > exercise.targetWeight + 0.01) capped to "progress" else null
            }
            action == "deload" -> {
                val raw = if (coachTarget.targetWeightKg > 0.0) {
                    normalizeCoachWeight(coachTarget.targetWeightKg, exercise.weightSteps)
                } else {
                    val steps = ProgressionService.parseWeightSteps(exercise.weightSteps)
                    if (steps.isNotEmpty()) {
                        steps.lastOrNull { it < exercise.targetWeight - 0.01 } ?: steps.first()
                    } else {
                        ProgressionService.roundToHalf(exercise.targetWeight * 0.95)
                    }
                }
                val capped = capDeloadStep(
                    current = exercise.targetWeight,
                    suggested = raw,
                    weightSteps = exercise.weightSteps,
                    progressionStepKg = exercise.progressionStepKg
                )
                if (capped < exercise.targetWeight - 0.01) capped to "deload" else null
            }
            else -> null
        }
    }

    private suspend fun refreshActivePlan() {
        activePlan = coachingPlanDao.getLatest()
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
                completedFlag = completed,
                now = formatDateTime(Date())
            )

            val logs = workoutRepo.getSetLogs(exercise.id)
            _uiState.update {
                it.copy(
                    completedSets = logs,
                    currentSetNumber = logs.size + 1
                )
            }

            if (logs.size >= exercise.targetSets) {
                workoutRepo.setExerciseCompletedAt(exercise.id, formatDateTime(Date()))

                // Die Coach-Empfehlung wurde bereits VOR dem ersten Satz im
                // CoachIntake-Dialog akzeptiert oder verworfen. Nach dem letzten Satz
                // entscheidet ausschliesslich die Double-Progression-Regel.
                val eligibleByCode = ProgressionService.isEligibleForProgression(
                    logs, exercise.targetSets, exercise.targetReps
                )

                val proposedWeight: Double? = if (eligibleByCode) {
                    ProgressionService.calculateNextWeight(
                        exercise.targetWeight,
                        exercise.progressionStepKg,
                        exercise.weightSteps
                    )
                } else null

                if (proposedWeight != null && proposedWeight != exercise.targetWeight) {
                    _uiState.update {
                        it.copy(
                            showProgressionDialog = true,
                            nextWeight = proposedWeight,
                            progressionAction = "progress",
                            progressionCoachReason = "",
                            timerState = TimerState.Idle,
                            isProcessing = false
                        )
                    }
                } else {
                    // No weight change — move on to the next exercise.
                    exerciseQueue.poll()
                    _uiState.update {
                        it.copy(
                            completedExerciseCount = it.completedExerciseCount + 1,
                            isProcessing = false,
                            isExerciseTransition = true,
                            nextExerciseName = peekNextExerciseName(),
                            nextExerciseSeatPosition = peekNextExerciseSeatPosition(),
                            nextExercisePadPosition = peekNextExercisePadPosition(),
                            nextExerciseTargetWeight = peekNextExerciseTargetWeight(),
                            nextExerciseRepRange = peekNextExerciseRepRange()
                        )
                    }
                    if (exerciseQueue.isEmpty()) {
                        showCurrentExercise() // will call finishWorkout()
                    } else {
                        advanceToNextExerciseAfterTimer = true
                        startRestTimer(exercise.plannedRestSeconds, ChimeKind.EXERCISE)
                    }
                }
            } else {
                _uiState.update { it.copy(isProcessing = false) }
                startRestTimer(exercise.plannedRestSeconds, ChimeKind.SET)
            }
        }
    }

    fun onProgressionDecision(shouldProgress: Boolean) {
        viewModelScope.launch {
            val exercise = _uiState.value.currentExercise ?: return@launch
            val action = _uiState.value.progressionAction
            val coachReason = _uiState.value.progressionCoachReason

            if (shouldProgress) {
                val nextWeight = _uiState.value.nextWeight
                progressionDecisions[exercise.exerciseId] = nextWeight

                val exerciseEntity = exerciseRepo.getById(exercise.exerciseId)
                weightChanges.add(
                    WeightChange(
                        exerciseName = exercise.exerciseDisplayName.ifEmpty { exercise.exerciseCode },
                        oldWeight = exercise.targetWeight,
                        newWeight = nextWeight,
                        weightUnit = exerciseEntity?.weightUnit ?: "kg",
                        coachAction = action,
                        coachReason = coachReason
                    )
                )
            }

            exerciseQueue.poll()
            _uiState.update {
                it.copy(
                    completedExerciseCount = it.completedExerciseCount + 1,
                    showProgressionDialog = false,
                    progressionCoachReason = "",
                    progressionAction = "progress",
                    isExerciseTransition = true,
                    nextExerciseName = peekNextExerciseName(),
                    nextExerciseSeatPosition = peekNextExerciseSeatPosition(),
                    nextExercisePadPosition = peekNextExercisePadPosition(),
                    nextExerciseTargetWeight = peekNextExerciseTargetWeight(),
                    nextExerciseRepRange = peekNextExerciseRepRange()
                )
            }

            if (exerciseQueue.isEmpty()) {
                showCurrentExercise() // will call finishWorkout()
            } else {
                advanceToNextExerciseAfterTimer = true
                startRestTimer(exercise.plannedRestSeconds, ChimeKind.EXERCISE)
            }
        }
    }

    fun onCoachIntakeDecision(accept: Boolean) {
        viewModelScope.launch {
            val exercise = _uiState.value.currentExercise ?: return@launch
            val coachTarget = activePlan?.targetFor(exercise.exerciseCode)
            val newWeight = _uiState.value.coachIntakeWeight

            if (accept && newWeight > 0.0) {
                workoutRepo.updateSessionExerciseTargetWeight(exercise.id, newWeight)
                exerciseRepo.updateWeight(
                    exercise.exerciseId,
                    newWeight,
                    formatDateTime(Date()).substringBefore(" ")
                )
                val exerciseEntity = exerciseRepo.getById(exercise.exerciseId)
                weightChanges.add(
                    WeightChange(
                        exerciseName = exercise.exerciseDisplayName.ifEmpty { exercise.exerciseCode },
                        oldWeight = exercise.targetWeight,
                        newWeight = newWeight,
                        weightUnit = exerciseEntity?.weightUnit ?: "kg",
                        coachAction = _uiState.value.coachIntakeAction,
                        coachReason = _uiState.value.coachIntakeReason
                    )
                )
                // currentExercise ist immer das Queue-Head; aktualisierten Eintrag dort zurueckschreiben.
                val refreshed = workoutRepo.getSessionExerciseById(exercise.id)
                if (refreshed != null) {
                    if (exerciseQueue.peek()?.id == refreshed.id) {
                        exerciseQueue.poll()
                        exerciseQueue.addFirst(refreshed)
                    }
                    _uiState.update { it.copy(currentExercise = refreshed) }
                }
            }

            if (coachTarget != null) {
                coachingPlanDao.updateTargetDecision(
                    coachTarget.id,
                    if (accept) "accepted" else "rejected"
                )
                refreshActivePlan()
            }

            _uiState.update {
                it.copy(
                    showCoachIntakeDialog = false,
                    coachIntakeWeight = 0.0,
                    coachIntakeAction = "progress",
                    coachIntakeReason = ""
                )
            }
            // Jetzt den eigentlichen Uebungsstart durchziehen.
            showCurrentExercise()
        }
    }

    fun onSkipNextExerciseDuringTimer() {
        val skipped = exerciseQueue.poll() ?: return
        exerciseQueue.add(skipped)
        _uiState.update {
            it.copy(
                nextExerciseName = peekNextExerciseName(),
                nextExerciseSeatPosition = peekNextExerciseSeatPosition(),
                nextExercisePadPosition = peekNextExercisePadPosition(),
                nextExerciseTargetWeight = peekNextExerciseTargetWeight(),
                nextExerciseRepRange = peekNextExerciseRepRange(),
                skippedCount = it.skippedCount + 1
            )
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
        try { RestTimerService.stop(application) } catch (_: Exception) { }
        if (advanceToNextExerciseAfterTimer) {
            advanceToNextExerciseAfterTimer = false
            _uiState.update { it.copy(timerState = TimerState.Idle, isExerciseTransition = false) }
            viewModelScope.launch { showCurrentExercise() }
        } else {
            _uiState.update { it.copy(timerState = TimerState.Idle) }
        }
    }

    fun onEndWorkoutEarly() {
        viewModelScope.launch {
            finishWorkout()
        }
    }

    private suspend fun finishWorkout() {
        countDownTimer?.cancel()
        try { RestTimerService.stop(application) } catch (_: Exception) { }
        val now = formatDateTime(Date())

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

    private fun startRestTimer(seconds: Int, kind: ChimeKind) {
        countDownTimer?.cancel()
        pendingChimeKind = kind
        _uiState.update { it.copy(timerState = TimerState.Running(seconds, seconds)) }

        // Start a foreground service so the chime fires reliably even with the
        // screen off or the app backgrounded. The in-app countdown below stays
        // in sync for the on-screen UI.
        val serviceKind = if (kind == ChimeKind.EXERCISE) {
            RestTimerService.KIND_EXERCISE
        } else {
            RestTimerService.KIND_SET
        }
        try {
            RestTimerService.start(application, seconds, serviceKind)
        } catch (_: Exception) { }

        countDownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toInt() + 1
                _uiState.update {
                    it.copy(timerState = TimerState.Running(remaining, seconds))
                }
            }

            override fun onFinish() {
                _uiState.update { it.copy(timerState = TimerState.Finished) }
                playNotificationSound(pendingChimeKind)
                vibrate()
            }
        }.start()
    }

    private fun playNotificationSound(kind: ChimeKind) {
        val sampleRate = 44100
        val notes = when (kind) {
            ChimeKind.SET -> listOf(523.25, 659.25, 783.99)               // C5-E5-G5
            ChimeKind.EXERCISE -> listOf(523.25, 659.25, 783.99, 1046.50) // C5-E5-G5-C6
        }
        val noteMs = if (kind == ChimeKind.EXERCISE) 180 else 110
        playArpeggio(notes, noteMs, sampleRate)
    }

    private fun playArpeggio(freqs: List<Double>, noteMs: Int, sampleRate: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val samplesPerNote = sampleRate * noteMs / 1000
                val totalSamples = samplesPerNote * freqs.size
                val buffer = ShortArray(totalSamples)
                val attack = samplesPerNote / 20
                val releaseStart = samplesPerNote * 9 / 10
                val releaseLen = samplesPerNote - releaseStart
                freqs.forEachIndexed { idx, freq ->
                    val offset = idx * samplesPerNote
                    for (i in 0 until samplesPerNote) {
                        val t = i.toDouble() / sampleRate
                        val envelope = when {
                            i < attack -> i.toDouble() / attack
                            i > releaseStart -> (samplesPerNote - i).toDouble() / releaseLen
                            else -> 1.0
                        }
                        val sample = (sin(2 * Math.PI * freq * t) * envelope * 0.6 * Short.MAX_VALUE).toInt().toShort()
                        buffer[offset + i] = sample
                    }
                }
                val track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                track.write(buffer, 0, buffer.size)
                track.play()
                delay((noteMs.toLong() * freqs.size) + 50L)
                track.release()
            } catch (_: Exception) { }
        }
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
        try { RestTimerService.stop(application) } catch (_: Exception) { }
    }
}

class ActiveWorkoutViewModelFactory(
    private val sessionId: Long,
    private val workoutRepo: WorkoutSessionRepository,
    private val exerciseRepo: ExerciseRepository,
    private val coachingPlanDao: CoachingPlanDao,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ActiveWorkoutViewModel(sessionId, workoutRepo, exerciseRepo, coachingPlanDao, application) as T
    }
}
