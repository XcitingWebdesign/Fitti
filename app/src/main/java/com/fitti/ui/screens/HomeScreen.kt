package com.fitti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitti.data.AiAnalysisDao
import com.fitti.data.AiAnalysisEntity
import com.fitti.data.BodyMeasurementDao
import com.fitti.data.ClaudeApiService
import com.fitti.data.CoachingPlanDao
import com.fitti.data.CoachingPlanParser
import com.fitti.data.CoachingPlanWithTargets
import com.fitti.data.ExerciseRepository
import com.fitti.data.NutritionLogDao
import com.fitti.data.SettingsRepository
import com.fitti.data.WeightLogDao
import com.fitti.data.WorkoutSessionEntity
import com.fitti.data.WorkoutSessionRepository
import com.fitti.ui.HomeUiState
import com.fitti.ui.HomeViewModel
import com.fitti.ui.HomeViewModelFactory
import com.fitti.ui.MuscleGroupStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

import com.fitti.domain.Achievement
import com.fitti.ui.common.BalanceRadar
import com.fitti.ui.common.formatDateTime
import com.fitti.ui.common.WeightChart
import com.fitti.ui.common.calculateDuration
import com.fitti.ui.common.MarkdownText
import com.fitti.ui.common.muscleGroupLabels
import com.fitti.ui.common.parseDateTime
import com.fitti.ui.common.stripMarkdown

@Composable
fun HomeScreen(
    exerciseRepo: ExerciseRepository,
    workoutRepo: WorkoutSessionRepository,
    settingsRepo: SettingsRepository,
    weightLogDao: WeightLogDao,
    aiAnalysisDao: AiAnalysisDao,
    coachingPlanDao: CoachingPlanDao,
    nutritionLogDao: NutritionLogDao,
    bodyMeasurementDao: BodyMeasurementDao,
    onStartWorkout: (Long) -> Unit,
    onOpenHistory: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMeasurements: () -> Unit
) {
    val vm: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            exerciseRepo, workoutRepo, settingsRepo, weightLogDao,
            coachingPlanDao, nutritionLogDao, bodyMeasurementDao
        )
    )
    val state by vm.uiState.collectAsState()

    HomeScreenContent(
        state = state,
        settingsRepo = settingsRepo,
        workoutRepo = workoutRepo,
        weightLogDao = weightLogDao,
        aiAnalysisDao = aiAnalysisDao,
        coachingPlanDao = coachingPlanDao,
        nutritionLogDao = nutritionLogDao,
        bodyMeasurementDao = bodyMeasurementDao,
        onStartTraining = { vm.startOrContinueWorkout(onStartWorkout) },
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
        onOpenMeasurements = onOpenMeasurements,
        onToggleProtein = { vm.toggleProteinHitToday() },
        onLogWeight = { vm.logWeightToday(it) },
        onAfterCoaching = { vm.refresh() },
        onWeightEntered = { weight -> vm.onWeightEntered(weight, onStartWorkout) },
        onWeightSkipped = { vm.dismissWeightDialog(onStartWorkout) },
        onAchievementSeen = { code -> vm.markAchievementSeen(code) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    settingsRepo: SettingsRepository,
    workoutRepo: WorkoutSessionRepository,
    weightLogDao: WeightLogDao,
    aiAnalysisDao: AiAnalysisDao,
    coachingPlanDao: CoachingPlanDao,
    nutritionLogDao: NutritionLogDao,
    bodyMeasurementDao: BodyMeasurementDao,
    onStartTraining: () -> Unit,
    onOpenHistory: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onToggleProtein: () -> Unit,
    onLogWeight: (Double) -> Unit,
    onAfterCoaching: () -> Unit,
    onWeightEntered: (Double) -> Unit,
    onWeightSkipped: () -> Unit,
    onAchievementSeen: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var weeklyAnalysis by remember { mutableStateOf<String?>(null) }
    var weeklyAnalysisTimestamp by remember { mutableStateOf<String?>(null) }
    var isLoadingAnalysis by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var showAnalysisDialog by remember { mutableStateOf(false) }
    val hasApiKey = remember { settingsRepo.claudeApiKey.isNotBlank() }

    LaunchedEffect(Unit) {
        val latest = aiAnalysisDao.getLatest()
        if (latest != null) {
            weeklyAnalysis = CoachingPlanParser.stripPlanBlock(latest.analysisText)
            weeklyAnalysisTimestamp = latest.createdAt
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Fitti",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (state.streakWeeks > 0) {
                            Spacer(Modifier.width(12.dp))
                            StreakChip(weeks = state.streakWeeks)
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Text(
                            text = "\u2699",
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Newly earned achievements
            if (state.newAchievements.isNotEmpty()) {
                items(state.newAchievements, key = { it.code }) { achievement ->
                    AchievementCard(
                        achievement = achievement,
                        onDismiss = { onAchievementSeen(achievement.code) }
                    )
                }
            }

            // Goal
            if (state.goal.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Mein Ziel",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.goal,
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Coach-Plan: Diese Woche
            state.activePlan?.let { plan ->
                item {
                    CoachingPlanCard(
                        plan = plan,
                        latestWeightKg = state.weightLogs.firstOrNull()?.weightKg,
                        proteinDaysHit = state.proteinDaysHitThisWeek,
                        proteinHitToday = state.proteinHitToday,
                        sessionsThisWeek = state.sessionsThisWeek,
                        onToggleProtein = onToggleProtein,
                        onLogWeight = onLogWeight
                    )
                }
            }

            // Tape-Mass-Banner
            val due = state.measurementsDueDays
            if (due == null || due >= 30) {
                item {
                    OutlinedButton(
                        onClick = onOpenMeasurements,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val label = if (due == null) {
                            "Tape-Maße noch nicht erfasst"
                        } else {
                            "Tape-Maße seit $due Tagen fällig"
                        }
                        Text(label)
                    }
                }
            }

            // Muscle Group Freshness
            if (state.muscleGroupFreshness.isNotEmpty()) {
                item {
                    Text(
                        text = "Muskelgruppen",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.muscleGroupFreshness.forEach { (group, status) ->
                            MuscleGroupChip(
                                label = muscleGroupLabels[group] ?: group,
                                status = status
                            )
                        }
                    }
                }
            }

            // Balance Radar
            if (state.balanceByGroup.isNotEmpty()) {
                item {
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Trainingsvolumen letzte 14 Tage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    BalanceRadar(
                        values = state.balanceByGroup,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Weight Chart
            if (state.weightLogs.isNotEmpty()) {
                item {
                    Text(
                        text = "K\u00f6rpergewicht",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    WeightChart(
                        logs = state.weightLogs,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Start Training Button
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onStartTraining,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (state.activeSessionId != null) "Training fortsetzen" else "Training starten",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Weekly AI Coaching
            if (hasApiKey && state.recentSessions.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            if (isLoadingAnalysis) return@OutlinedButton
                            isLoadingAnalysis = true
                            analysisError = null
                            val previousCoaching = weeklyAnalysis
                            weeklyAnalysis = null
                            scope.launch {
                                try {
                                    val allHistories = workoutRepo.observeSessionHistories().first()
                                    val sevenDaysAgo = Calendar.getInstance().apply {
                                        add(Calendar.DAY_OF_YEAR, -7)
                                    }.time
                                    val recentHistories = allHistories.filter { history ->
                                        val date = parseDateTime(history.session.completedAt ?: history.session.startedAt)
                                        date != null && date.after(sevenDaysAgo)
                                    }
                                    if (recentHistories.isEmpty()) {
                                        analysisError = "Keine Trainings in den letzten 7 Tagen."
                                        return@launch
                                    }
                                    val weight = weightLogDao.getLatest()?.weightKg
                                    val allWeightLogs = weightLogDao.getAll()
                                    val measurements = bodyMeasurementDao.getAll()
                                    val nutrition = nutritionLogDao.getAll()
                                    val availableExercises = exerciseRepo.getAllEntities()
                                    val service = ClaudeApiService(
                                        apiKey = settingsRepo.claudeApiKey,
                                        sonnetModel = settingsRepo.claudeSonnetModel,
                                        opusModel = settingsRepo.claudeOpusModel,
                                    )
                                    service.getWeeklyCoaching(
                                        sessions = recentHistories,
                                        availableExercises = availableExercises,
                                        userGoal = settingsRepo.goal,
                                        latestWeightKg = weight,
                                        heightCm = settingsRepo.heightCm,
                                        allHistories = allHistories,
                                        weightLogs = allWeightLogs,
                                        bodyMeasurements = measurements,
                                        nutritionLogs = nutrition,
                                        previousCoaching = previousCoaching
                                    ).onSuccess { analysis ->
                                        weeklyAnalysis = CoachingPlanParser.stripPlanBlock(analysis)
                                        showAnalysisDialog = true
                                        val ts = formatDateTime(Date())
                                        weeklyAnalysisTimestamp = ts
                                        aiAnalysisDao.insert(
                                            AiAnalysisEntity(analysisText = analysis, createdAt = ts)
                                        )
                                        val validCodes = availableExercises.map { it.code }.toSet()
                                        CoachingPlanParser.parse(analysis, validCodes)?.let { (plan, targets) ->
                                            coachingPlanDao.savePlan(plan, targets)
                                        }
                                        onAfterCoaching()
                                    }.onFailure { e ->
                                        analysisError = "Fehler: ${e.message}"
                                    }
                                } catch (e: Exception) {
                                    analysisError = "Fehler: ${e.message}"
                                } finally {
                                    isLoadingAnalysis = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoadingAnalysis) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Coaching l\u00e4uft... (kann bis zu 2 Min dauern)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Text(
                                "W\u00f6chentliches Coaching",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    if (analysisError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = analysisError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (weeklyAnalysis != null && !isLoadingAnalysis) {
                        Spacer(Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAnalysisDialog = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Coaching",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (weeklyAnalysisTimestamp != null) {
                                        Text(
                                            text = weeklyAnalysisTimestamp!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stripMarkdown(weeklyAnalysis!!),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Recent Sessions
            if (state.recentSessions.isNotEmpty()) {
                item {
                    Text(
                        text = "Letzte Trainings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(state.recentSessions.take(10)) { session ->
                    SessionCard(session = session, onClick = { onOpenHistory(session.id) })
                }
            } else if (!state.isLoading) {
                item {
                    Text(
                        text = "Noch keine Trainings abgeschlossen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Weight Dialog
    if (state.showWeightDialog) {
        WeightDialog(
            lastWeight = state.lastWeightKg,
            onConfirm = onWeightEntered,
            onSkip = onWeightSkipped
        )
    }

    // Weekly Coaching Dialog
    if (showAnalysisDialog && weeklyAnalysis != null) {
        AlertDialog(
            onDismissRequest = { showAnalysisDialog = false },
            title = { Text("W\u00f6chentliches Coaching") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MarkdownText(text = weeklyAnalysis!!)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnalysisDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun StreakChip(weeks: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "\uD83D\uDD25 $weeks Wochen",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AchievementCard(achievement: Achievement, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = achievement.emoji,
                fontSize = 32.sp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Neu: ${achievement.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "\u2713",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MuscleGroupChip(label: String, status: MuscleGroupStatus) {
    val statusIcon = when (status) {
        MuscleGroupStatus.FRESH -> "\u2713"
        MuscleGroupStatus.STALE -> "~"
        MuscleGroupStatus.OVERDUE -> "!"
        MuscleGroupStatus.NEVER -> "\u2013"
    }
    val isOverdue = status == MuscleGroupStatus.OVERDUE
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$label $statusIcon",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SessionCard(session: WorkoutSessionEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = session.completedAt?.substringBefore(" ") ?: session.startedAt.substringBefore(" "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val duration = calculateDuration(session.startedAt, session.completedAt)
                if (duration != null) {
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "\u203A",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeightDialog(
    lastWeight: Double?,
    onConfirm: (Double) -> Unit,
    onSkip: () -> Unit
) {
    var weightText by remember { mutableStateOf(lastWeight?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Aktuelles Gewicht") },
        text = {
            Column {
                if (lastWeight != null) {
                    Text(
                        text = "Letztes Gewicht: $lastWeight kg",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Gewicht (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val weight = weightText.replace(",", ".").toDoubleOrNull()
                    if (weight != null && weight > 0) {
                        onConfirm(weight)
                    }
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("\u00dcberspringen")
            }
        }
    )
}

@Composable
private fun CoachingPlanCard(
    plan: CoachingPlanWithTargets,
    latestWeightKg: Double?,
    proteinDaysHit: Int,
    proteinHitToday: Boolean,
    sessionsThisWeek: Int,
    onToggleProtein: () -> Unit,
    onLogWeight: (Double) -> Unit
) {
    var showWeightDialog by remember { mutableStateOf(false) }
    val bottleneck = plan.plan
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Diese Woche",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            val (label, currentDisplay, targetDisplay) = bottleneckDisplay(
                bottleneck.bottleneckType,
                latestWeightKg ?: bottleneck.bottleneckCurrent,
                bottleneck.bottleneckTarget
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$currentDisplay  \u2192  $targetDisplay",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Sessions  ${sessionsThisWeek} / ${bottleneck.weeklySessions}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Protein  ${proteinDaysHit} / 7 Tage  (Ziel ${bottleneck.weeklyProteinG} g/Tag)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onToggleProtein,
                    modifier = Modifier.weight(1f)
                ) {
                    val mark = if (proteinHitToday) "\u2713" else "\u2715"
                    Text("Protein heute $mark")
                }
                OutlinedButton(
                    onClick = { showWeightDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Gewicht")
                }
            }
        }
    }

    if (showWeightDialog) {
        WeightDialog(
            lastWeight = latestWeightKg,
            onConfirm = {
                onLogWeight(it)
                showWeightDialog = false
            },
            onSkip = { showWeightDialog = false }
        )
    }
}

private fun bottleneckDisplay(
    type: String,
    current: Double,
    target: Double
): Triple<String, String, String> {
    return when (type) {
        "bodyweight" -> Triple(
            "Bottleneck: K\u00f6rpergewicht",
            "${"%.1f".format(current)} kg",
            "${"%.1f".format(target)} kg"
        )
        "protein" -> Triple(
            "Bottleneck: Protein",
            "${current.toInt()} g",
            "${target.toInt()} g"
        )
        "sessions" -> Triple(
            "Bottleneck: Trainingsfrequenz",
            "${current.toInt()}",
            "${target.toInt()}/Woche"
        )
        "progression" -> Triple(
            "Bottleneck: Progression",
            "${"%.1f".format(current)} kg",
            "${"%.1f".format(target)} kg"
        )
        else -> Triple(
            "Coach-Fokus",
            "${"%.1f".format(current)}",
            "${"%.1f".format(target)}"
        )
    }
}

