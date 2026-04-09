package com.fitti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitti.data.ClaudeApiService
import com.fitti.data.ExerciseRepository
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

import com.fitti.ui.common.WeightChart
import com.fitti.ui.common.calculateDuration
import com.fitti.ui.common.muscleGroupLabels
import com.fitti.ui.common.parseDateTime

@Composable
fun HomeScreen(
    exerciseRepo: ExerciseRepository,
    workoutRepo: WorkoutSessionRepository,
    settingsRepo: SettingsRepository,
    weightLogDao: WeightLogDao,
    onStartWorkout: (Long) -> Unit,
    onOpenHistory: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(exerciseRepo, workoutRepo, settingsRepo, weightLogDao)
    )
    val state by vm.uiState.collectAsState()

    HomeScreenContent(
        state = state,
        settingsRepo = settingsRepo,
        workoutRepo = workoutRepo,
        weightLogDao = weightLogDao,
        onStartTraining = { vm.startOrContinueWorkout(onStartWorkout) },
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
        onWeightEntered = { weight -> vm.onWeightEntered(weight, onStartWorkout) },
        onWeightSkipped = { vm.dismissWeightDialog(onStartWorkout) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    settingsRepo: SettingsRepository,
    workoutRepo: WorkoutSessionRepository,
    weightLogDao: WeightLogDao,
    onStartTraining: () -> Unit,
    onOpenHistory: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onWeightEntered: (Double) -> Unit,
    onWeightSkipped: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var weeklyAnalysis by remember { mutableStateOf<String?>(null) }
    var isLoadingAnalysis by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var showAnalysisDialog by remember { mutableStateOf(false) }
    val hasApiKey = remember { settingsRepo.claudeApiKey.isNotBlank() }
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
                    Text(
                        text = "Fitti",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onOpenSettings) {
                        Text(
                            text = "\u2699",
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

            // Weekly AI Analysis
            if (hasApiKey && state.recentSessions.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            if (isLoadingAnalysis) return@OutlinedButton
                            isLoadingAnalysis = true
                            analysisError = null
                            weeklyAnalysis = null
                            scope.launch {
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
                                    isLoadingAnalysis = false
                                    return@launch
                                }
                                val weight = weightLogDao.getLatest()?.weightKg
                                val service = ClaudeApiService(settingsRepo.claudeApiKey)
                                service.getWeeklyAnalysis(
                                    sessions = recentHistories,
                                    userGoal = settingsRepo.goal,
                                    latestWeightKg = weight
                                ).onSuccess { analysis ->
                                    weeklyAnalysis = analysis
                                    showAnalysisDialog = true
                                }.onFailure { e ->
                                    analysisError = "Fehler: ${e.message}"
                                }
                                isLoadingAnalysis = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoadingAnalysis) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                "W\u00f6chentliche KI-Analyse",
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

    // Weekly Analysis Dialog
    if (showAnalysisDialog && weeklyAnalysis != null) {
        AlertDialog(
            onDismissRequest = { showAnalysisDialog = false },
            title = { Text("W\u00f6chentliche KI-Analyse") },
            text = {
                Text(
                    text = weeklyAnalysis!!,
                    style = MaterialTheme.typography.bodyLarge
                )
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
private fun MuscleGroupChip(label: String, status: MuscleGroupStatus) {
    val statusIcon = when (status) {
        MuscleGroupStatus.FRESH -> "\u2713"
        MuscleGroupStatus.STALE -> "~"
        MuscleGroupStatus.OVERDUE -> "!"
        MuscleGroupStatus.NEVER -> "\u2013"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$label $statusIcon",
            style = MaterialTheme.typography.bodyMedium,
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

