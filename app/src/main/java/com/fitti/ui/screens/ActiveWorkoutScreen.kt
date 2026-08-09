package com.fitti.ui.screens

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitti.data.BodyMeasurementDao
import com.fitti.data.ClaudeApiService
import com.fitti.data.ExerciseRepository
import com.fitti.data.NutritionLogDao
import com.fitti.data.SessionExerciseEntity
import com.fitti.data.SettingsRepository
import com.fitti.data.WeightLogDao
import com.fitti.data.WorkoutSessionRepository
import com.fitti.ui.ActiveWorkoutUiState
import com.fitti.ui.ActiveWorkoutViewModel
import com.fitti.ui.ActiveWorkoutViewModelFactory
import com.fitti.ui.SessionSummary
import com.fitti.ui.TimerState

import com.fitti.ui.common.AiFeedbackSection
import com.fitti.ui.common.cleanWeight
import com.fitti.ui.common.formatDurationMinutes
import com.fitti.ui.common.muscleGroupLabels
import kotlinx.coroutines.flow.first

@Composable
fun ActiveWorkoutScreen(
    sessionId: Long,
    workoutRepo: WorkoutSessionRepository,
    exerciseRepo: ExerciseRepository,
    settingsRepo: SettingsRepository,
    weightLogDao: WeightLogDao,
    coachingPlanDao: com.fitti.data.CoachingPlanDao,
    nutritionLogDao: NutritionLogDao,
    bodyMeasurementDao: BodyMeasurementDao,
    application: Application,
    onWorkoutComplete: () -> Unit
) {
    val vm: ActiveWorkoutViewModel = viewModel(
        key = "workout_$sessionId",
        factory = ActiveWorkoutViewModelFactory(sessionId, workoutRepo, exerciseRepo, coachingPlanDao, application)
    )
    val state by vm.uiState.collectAsState()

    // Keep screen on during workout
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    when {
        state.isWorkoutComplete && state.sessionSummary != null -> {
            WorkoutSummaryContent(
                summary = state.sessionSummary!!,
                sessionId = sessionId,
                workoutRepo = workoutRepo,
                settingsRepo = settingsRepo,
                weightLogDao = weightLogDao,
                nutritionLogDao = nutritionLogDao,
                bodyMeasurementDao = bodyMeasurementDao,
                onFinish = onWorkoutComplete
            )
        }
        state.showCoachIntakeDialog -> {
            CoachIntakeDialogContent(
                exercise = state.currentExercise!!,
                proposedWeight = state.coachIntakeWeight,
                action = state.coachIntakeAction,
                coachReason = state.coachIntakeReason,
                onAccept = { vm.onCoachIntakeDecision(true) },
                onReject = { vm.onCoachIntakeDecision(false) }
            )
        }
        state.showProgressionDialog -> {
            ProgressionDialogContent(
                exercise = state.currentExercise!!,
                nextWeight = state.nextWeight,
                action = state.progressionAction,
                coachReason = state.progressionCoachReason,
                onYes = { vm.onProgressionDecision(true) },
                onNo = { vm.onProgressionDecision(false) }
            )
        }
        state.timerState !is TimerState.Idle -> {
            TimerContent(
                exercise = state.currentExercise!!,
                timerState = state.timerState,
                completedSetNumber = state.currentSetNumber - 1,
                totalSets = state.currentExercise!!.targetSets,
                isExerciseTransition = state.isExerciseTransition,
                nextExerciseName = state.nextExerciseName,
                nextExerciseSeatPosition = state.nextExerciseSeatPosition,
                nextExercisePadPosition = state.nextExercisePadPosition,
                nextExerciseTargetWeight = state.nextExerciseTargetWeight,
                nextExerciseRepRange = state.nextExerciseRepRange,
                onSkipTimer = { vm.onTimerSkipped() },
                onSkipNextExercise = { vm.onSkipNextExerciseDuringTimer() }
            )
        }
        state.currentExercise != null -> {
            ExerciseContent(
                state = state,
                onSetLogged = { reps -> vm.onSetLogged(reps) },
                onSkip = { vm.onSkipExercise() },
                onEndWorkout = { vm.onEndWorkoutEarly() }
            )
        }
        state.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Laden...", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ExerciseContent(
    state: ActiveWorkoutUiState,
    onSetLogged: (Int) -> Unit,
    onSkip: () -> Unit,
    onEndWorkout: () -> Unit
) {
    val exercise = state.currentExercise!!

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEndWorkout) {
                    Text(
                        "\u2190 Beenden",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    "${state.completedExerciseCount + 1}/${state.totalExercises}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Exercise info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = exercise.exerciseDisplayName.ifEmpty { exercise.exerciseCode },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = exercise.exerciseCode,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = muscleGroupLabels[exercise.exerciseMuscleGroup] ?: exercise.exerciseMuscleGroup,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Seat & pad positions
                val positionParts = mutableListOf<String>()
                if (exercise.exerciseSeatPosition.isNotBlank()) {
                    positionParts.add("S${exercise.exerciseSeatPosition}")
                }
                if (exercise.exercisePadPosition.isNotBlank()) {
                    positionParts.add("P${exercise.exercisePadPosition}")
                }
                if (positionParts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = positionParts.joinToString(" \u2022 "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Target values
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TargetChip("${exercise.targetWeight.cleanWeight()}")
                    TargetChip("${exercise.targetRepsMin}-${exercise.targetReps} Wdh")
                    TargetChip("${exercise.targetSets} S\u00e4tze")
                }

                Spacer(Modifier.height(32.dp))

                // Current set indicator
                Text(
                    text = "Satz ${state.currentSetNumber} von ${exercise.targetSets}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rep picker label
                Text(
                    text = "Wiederholungen:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                // Rep buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (rep in exercise.targetRepsMin..exercise.targetReps) {
                        FilledTonalButton(
                            onClick = { onSetLogged(rep) },
                            enabled = !state.isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "$rep",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Not completed button
                OutlinedButton(
                    onClick = { onSetLogged(0) },
                    enabled = !state.isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Nicht geschafft",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Completed sets log
                if (state.completedSets.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    state.completedSets.forEach { log ->
                        val icon = if (log.completedFlag) "\u2713" else "\u2717"
                        val weight = if (log.completedFlag) FontWeight.Bold else FontWeight.Normal
                        Text(
                            text = "$icon Satz ${log.setNumber}: ${log.actualWeightKg.cleanWeight()} x ${log.actualReps}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = weight,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Skip button
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "\u00dcberspringen",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerContent(
    exercise: SessionExerciseEntity,
    timerState: TimerState,
    completedSetNumber: Int,
    totalSets: Int,
    isExerciseTransition: Boolean = false,
    nextExerciseName: String = "",
    nextExerciseSeatPosition: String = "",
    nextExercisePadPosition: String = "",
    nextExerciseTargetWeight: Double = 0.0,
    nextExerciseRepRange: String = "",
    onSkipTimer: () -> Unit,
    onSkipNextExercise: () -> Unit = {}
) {
    val (remaining, total) = when (timerState) {
        is TimerState.Running -> timerState.secondsRemaining to timerState.totalSeconds
        is TimerState.Finished -> 0 to 1
        else -> 0 to 1
    }

    val progress by animateFloatAsState(
        targetValue = if (total > 0) remaining.toFloat() / total.toFloat() else 0f,
        label = "timer_progress"
    )

    val isFinished = timerState is TimerState.Finished

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = exercise.exerciseDisplayName.ifEmpty { exercise.exerciseCode },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isExerciseTransition) "\u00dcbung abgeschlossen"
                       else "Satz $completedSetNumber von $totalSets geschafft",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            // Big countdown
            Text(
                text = if (isFinished) "\u2713 Weiter!" else formatTime(remaining),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (isFinished) 48.sp else 72.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(32.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            if (isExerciseTransition && nextExerciseName.isNotEmpty()) {
                Text(
                    text = "N\u00e4chstes Ger\u00e4t",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = nextExerciseName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                val nextPositionParts = mutableListOf<String>()
                if (nextExerciseSeatPosition.isNotBlank()) {
                    nextPositionParts.add("S$nextExerciseSeatPosition")
                }
                if (nextExercisePadPosition.isNotBlank()) {
                    nextPositionParts.add("P$nextExercisePadPosition")
                }
                if (nextPositionParts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = nextPositionParts.joinToString(" \u2022 "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (nextExerciseTargetWeight > 0.0 || nextExerciseRepRange.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    val targetParts = mutableListOf<String>()
                    if (nextExerciseTargetWeight > 0.0) {
                        targetParts.add("${nextExerciseTargetWeight.cleanWeight()} kg")
                    }
                    if (nextExerciseRepRange.isNotBlank()) {
                        targetParts.add(nextExerciseRepRange)
                    }
                    Text(
                        text = targetParts.joinToString(" \u2022 "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSkipNextExercise) {
                    Text(
                        "\u00dcberspringen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = onSkipTimer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (isFinished) {
                        if (isExerciseTransition) "Weiter zur n\u00e4chsten \u00dcbung"
                        else "Weiter zum n\u00e4chsten Satz"
                    } else "Timer \u00fcberspringen",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CoachIntakeDialogContent(
    exercise: SessionExerciseEntity,
    proposedWeight: Double,
    action: String,
    coachReason: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val isDeload = action == "deload"
    val delta = proposedWeight - exercise.targetWeight
    val deltaSign = if (delta >= 0) "+" else ""
    val headline = if (isDeload) "KI schl\u00e4gt vor: Gewicht reduzieren"
                    else "KI schl\u00e4gt vor: Gewicht erh\u00f6hen"

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = exercise.exerciseDisplayName.ifEmpty { exercise.exerciseCode },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Aktuell: ${exercise.targetWeight.cleanWeight()} kg",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            if (coachReason.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Coach: $coachReason",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "\u00dcbernehmen (${deltaSign}${delta.cleanWeight()} \u2192 ${proposedWeight.cleanWeight()})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onReject,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Bei ${exercise.targetWeight.cleanWeight()} kg bleiben",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProgressionDialogContent(
    exercise: SessionExerciseEntity,
    nextWeight: Double,
    action: String = "progress",
    coachReason: String = "",
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val isDeload = action == "deload"
    val delta = nextWeight - exercise.targetWeight
    val deltaSign = if (delta >= 0) "+" else ""
    val headline = if (isDeload) "Gewicht reduzieren?" else "Mehr Gewicht n\u00e4chstes Mal?"
    val yesLabel = if (isDeload) {
        "Ja (${deltaSign}${delta.cleanWeight()} \u2192 ${nextWeight.cleanWeight()})"
    } else {
        "Ja (${deltaSign}${delta.cleanWeight()} \u2192 ${nextWeight.cleanWeight()})"
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = exercise.exerciseDisplayName.ifEmpty { exercise.exerciseCode },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "\u2713 ${exercise.targetSets}/${exercise.targetSets} S\u00e4tze geschafft",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            if (coachReason.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Coach: $coachReason",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(32.dp))

            // Yes button
            Button(
                onClick = onYes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    yesLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.height(16.dp))

            // No button
            OutlinedButton(
                onClick = onNo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Nein (bleibt bei ${exercise.targetWeight.cleanWeight()})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkoutSummaryContent(
    summary: SessionSummary,
    sessionId: Long,
    workoutRepo: WorkoutSessionRepository,
    settingsRepo: SettingsRepository,
    weightLogDao: WeightLogDao,
    nutritionLogDao: NutritionLogDao,
    bodyMeasurementDao: BodyMeasurementDao,
    onFinish: () -> Unit
) {
    val hasApiKey = remember { settingsRepo.claudeApiKey.isNotBlank() }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "\u2713 Training abgeschlossen!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(24.dp))

                    Text(
                        text = "Dauer: ${formatDurationMinutes(summary.durationMinutes)}",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "${summary.exercisesCompleted} von ${summary.totalExercises} \u00dcbungen",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (summary.weightChanges.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "Gewichts\u00e4nderungen:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(8.dp))

                        summary.weightChanges.forEach { change ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = change.exerciseName,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        val display = if (change.coachAction == "hold") {
                                            "${change.oldWeight.cleanWeight()} ${change.weightUnit} (gehalten)"
                                        } else {
                                            "${change.oldWeight.cleanWeight()} \u2192 ${change.newWeight.cleanWeight()} ${change.weightUnit}"
                                        }
                                        Text(
                                            text = display,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (change.coachReason.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Coach: ${change.coachReason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // KI-Feedback section
            if (hasApiKey) {
                item {
                    Spacer(Modifier.height(24.dp))
                    AiFeedbackSection(
                        onRequestFeedback = {
                            val history = workoutRepo.getSessionHistory(sessionId)
                                ?: return@AiFeedbackSection Result.failure(Exception("Training nicht gefunden."))
                            val weight = weightLogDao.getLatest()?.weightKg
                            val allHistories = workoutRepo.observeSessionHistories().first()
                            val allWeightLogs = weightLogDao.getAll()
                            val measurements = bodyMeasurementDao.getAll()
                            val nutrition = nutritionLogDao.getAll()
                            val service = ClaudeApiService(
                                apiKey = settingsRepo.claudeApiKey,
                                sonnetModel = settingsRepo.claudeSonnetModel,
                                opusModel = settingsRepo.claudeOpusModel,
                                coachPersona = settingsRepo.coachPersona,
                            )
                            service.getWorkoutFeedback(
                                history = history,
                                userGoal = settingsRepo.goal,
                                latestWeightKg = weight,
                                heightCm = settingsRepo.heightCm,
                                allHistories = allHistories,
                                weightLogs = allWeightLogs,
                                bodyMeasurements = measurements,
                                nutritionLogs = nutrition
                            )
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Fertig",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetChip(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

