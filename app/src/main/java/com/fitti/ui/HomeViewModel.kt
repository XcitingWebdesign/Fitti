package com.fitti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitti.data.BodyMeasurementDao
import com.fitti.data.BodyMeasurementEntity
import com.fitti.data.CoachingPlanDao
import com.fitti.data.CoachingPlanWithTargets
import com.fitti.data.ExerciseRepository
import com.fitti.data.NutritionLogDao
import com.fitti.data.NutritionLogEntity
import com.fitti.data.SettingsRepository
import com.fitti.data.WeightLogDao
import com.fitti.data.WeightLogEntity
import com.fitti.data.WorkoutSessionEntity
import com.fitti.data.WorkoutSessionRepository
import com.fitti.ui.common.parseDateTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.fitti.domain.Achievement
import com.fitti.domain.AchievementService
import com.fitti.domain.Exercise
import com.fitti.domain.StartWorkoutSessionUseCase
import com.fitti.domain.StrengthEstimator
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
    val balanceByGroup: Map<String, Float> = emptyMap(),
    val streakWeeks: Int = 0,
    val newAchievements: List<Achievement> = emptyList(),
    val activePlan: CoachingPlanWithTargets? = null,
    val proteinDaysHitThisWeek: Int = 0,
    val sessionsThisWeek: Int = 0,
    val proteinHitToday: Boolean = false,
    val measurementsDueDays: Int? = null,
    val latestMeasurement: BodyMeasurementEntity? = null,
    val isLoading: Boolean = true
)

enum class MuscleGroupStatus { FRESH, STALE, OVERDUE, NEVER }

class HomeViewModel(
    private val exerciseRepo: ExerciseRepository,
    private val workoutRepo: WorkoutSessionRepository,
    private val settingsRepo: SettingsRepository,
    private val weightLogDao: WeightLogDao,
    private val coachingPlanDao: CoachingPlanDao,
    private val nutritionLogDao: NutritionLogDao,
    private val bodyMeasurementDao: BodyMeasurementDao,
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
                // Datum ist als "dd.MM.yyyy HH:mm" gespeichert; SQL-Sort vergleicht
                // lexikographisch und wuerde Mai-Termine ("06.05.") nach April-Terminen
                // ("30.04.") einordnen. Hier nochmal numerisch nach Zeit sortieren.
                val sorted = sessions.sortedByDescending {
                    parseDateTime(it.completedAt ?: it.startedAt)?.time ?: 0L
                }
                _uiState.update { it.copy(recentSessions = sorted, isLoading = false) }
                updateMuscleGroupFreshness(_uiState.value.exercises)
            }
        }

        viewModelScope.launch {
            workoutRepo.observeSessionHistories().collect { histories ->
                val sessions = histories.map { it.session }
                val raw = StrengthEstimator.groupMax(histories)
                val normalized = StrengthEstimator.normalize(raw)
                val streak = AchievementService.computeStreak(sessions)
                val earned = AchievementService.computeAchievements(
                    sessions = sessions,
                    histories = histories,
                    startingWeightByExerciseId = emptyMap()
                )
                val seen = settingsRepo.getSeenAchievements()
                val newOnes = earned.filter { it.code !in seen }

                _uiState.update {
                    it.copy(
                        balanceByGroup = normalized,
                        streakWeeks = streak,
                        newAchievements = newOnes
                    )
                }
            }
        }

        viewModelScope.launch {
            val active = workoutRepo.getActiveSession()
            if (active != null && isSessionStale(active)) {
                workoutRepo.completeSession(active.id, formatDateTime(Date()))
                _uiState.update { it.copy(activeSessionId = null) }
            } else {
                _uiState.update { it.copy(activeSessionId = active?.id) }
            }
        }

        viewModelScope.launch {
            val logs = weightLogDao.getAll()
            _uiState.update { it.copy(weightLogs = logs) }
        }

        viewModelScope.launch { refreshPlanAndChecks() }
    }

    fun refresh() {
        viewModelScope.launch { refreshPlanAndChecks() }
    }

    suspend fun refreshPlanAndChecks() {
        val plan = coachingPlanDao.getLatest()
        val today = isoDate(Date())
        val weekStartIso = isoDate(weekStart())
        val recentLogs = nutritionLogDao.getSince(weekStartIso)
        val proteinDays = recentLogs.count { it.proteinHit }
        val proteinHitToday = recentLogs.firstOrNull { it.date == today }?.proteinHit == true

        val sessionsThisWeek = _uiState.value.recentSessions.count { session ->
            val completedAt = session.completedAt ?: return@count false
            val date = parseDateTime(completedAt) ?: return@count false
            date >= weekStart()
        }

        val latestMeasurement = bodyMeasurementDao.getLatest()
        val measurementsDueDays = latestMeasurement?.let {
            val date = parseDateTime(it.measuredAt) ?: return@let null
            daysSince(date).toInt()
        }

        _uiState.update {
            it.copy(
                activePlan = plan,
                proteinDaysHitThisWeek = proteinDays,
                proteinHitToday = proteinHitToday,
                sessionsThisWeek = sessionsThisWeek,
                latestMeasurement = latestMeasurement,
                measurementsDueDays = measurementsDueDays
            )
        }
    }

    fun toggleProteinHitToday() {
        viewModelScope.launch {
            val today = isoDate(Date())
            val existing = nutritionLogDao.getForDate(today)
            val newValue = !(existing?.proteinHit ?: false)
            nutritionLogDao.insert(
                NutritionLogEntity(
                    id = existing?.id ?: 0L,
                    date = today,
                    proteinHit = newValue,
                    weightKg = existing?.weightKg
                )
            )
            refreshPlanAndChecks()
        }
    }

    fun logWeightToday(weightKg: Double) {
        viewModelScope.launch {
            val today = isoDate(Date())
            val existing = nutritionLogDao.getForDate(today)
            nutritionLogDao.insert(
                NutritionLogEntity(
                    id = existing?.id ?: 0L,
                    date = today,
                    proteinHit = existing?.proteinHit ?: false,
                    weightKg = weightKg
                )
            )
            weightLogDao.insert(
                WeightLogEntity(
                    weightKg = weightKg,
                    loggedAt = formatDateTime(Date())
                )
            )
            val logs = weightLogDao.getAll()
            _uiState.update { it.copy(weightLogs = logs) }
            refreshPlanAndChecks()
        }
    }

    private fun isoDate(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(date)

    private fun weekStart(): Date {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return cal.time
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

    fun markAchievementSeen(code: String) {
        settingsRepo.markAchievementSeen(code)
        _uiState.update { state ->
            state.copy(newAchievements = state.newAchievements.filterNot { it.code == code })
        }
    }

    private suspend fun proceedToWorkout(onSessionReady: (Long) -> Unit) {
        val activeId = _uiState.value.activeSessionId
        if (activeId != null) {
            val session = workoutRepo.getSessionById(activeId)
            if (session != null && isSessionStale(session)) {
                // Auto-complete stale session and start fresh
                workoutRepo.completeSession(activeId, formatDateTime(Date()))
                _uiState.update { it.copy(activeSessionId = null) }
            } else {
                onSessionReady(activeId)
                return
            }
        }
        val sessionId = startWorkoutSessionUseCase(formatDateTime(Date()))
        _uiState.update { it.copy(activeSessionId = sessionId) }
        onSessionReady(sessionId)
    }

    private fun isSessionStale(session: WorkoutSessionEntity): Boolean {
        val lastActivity = session.lastActivityAt.ifEmpty { session.startedAt }
        val date = parseDateTime(lastActivity) ?: return true
        val minutesSince = (System.currentTimeMillis() - date.time) / 60000
        return minutesSince > 30
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
    private val weightLogDao: WeightLogDao,
    private val coachingPlanDao: CoachingPlanDao,
    private val nutritionLogDao: NutritionLogDao,
    private val bodyMeasurementDao: BodyMeasurementDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(
            exerciseRepo = exerciseRepo,
            workoutRepo = workoutRepo,
            settingsRepo = settingsRepo,
            weightLogDao = weightLogDao,
            coachingPlanDao = coachingPlanDao,
            nutritionLogDao = nutritionLogDao,
            bodyMeasurementDao = bodyMeasurementDao,
            startWorkoutSessionUseCase = StartWorkoutSessionUseCase(workoutRepo, settingsRepo)
        ) as T
    }
}
