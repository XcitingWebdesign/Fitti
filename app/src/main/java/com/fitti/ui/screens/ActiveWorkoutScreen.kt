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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitti.data.ExerciseRepository
import com.fitti.data.SessionExerciseEntity
import com.fitti.data.SetLogEntity
import com.fitti.data.WorkoutSessionRepository
import com.fitti.ui.ActiveWorkoutUiState
import com.fitti.ui.ActiveWorkoutViewModel
import com.fitti.ui.ActiveWorkoutViewModelFactory
import com.fitti.ui.SessionSummary
import com.fitti.ui.TimerState

import com.fitti.ui.common.cleanWeight
import com.fitti.ui.common.muscleGroupLabels

@Composable
fun ActiveWorkoutScreen(
    sessionId: Long,
    workoutRepo: WorkoutSessionRepository,
    exerciseRepo: ExerciseRepository,
    application: Application,
    onWorkoutComplete: () -> Unit
) {
    val vm: ActiveWorkoutViewModel = viewModel(
        key = "workout_$sessionId",
        factory = ActiveWorkoutViewModelFactory(sessionId, workoutRepo, exerciseRepo, application)
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
                onFinish = onWorkoutComplete
            )
        }
        state.showProgressionDialog -> {
            ProgressionDialogContent(
                exercise = state.currentExercise!!,
                nextWeight = state.nextWeight,
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
                onSkipTimer = { vm.onTimerSkipped() }
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
                    text = muscleGroupLabels[exercise.exerciseMuscleGroup] ?: exercise.exerciseMuscleGroup,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    onSkipTimer: () -> Unit
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
                text = "Satz $completedSetNumber von $totalSets geschafft",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            // Big countdown
            Text(
                text = if (isFinished) "Weiter!" else formatTime(remaining),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (isFinished) 48.sp else 72.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (isFinished) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(32.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (isFinished) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            OutlinedButton(
                onClick = onSkipTimer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (isFinished) "Weiter zum n\u00e4chsten Satz" else "Timer \u00fcberspringen",
                    style = MaterialTheme.typography.bodyLarge,
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
    onYes: () -> Unit,
    onNo: () -> Unit
) {
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
                text = "${exercise.targetSets}/${exercise.targetSets} S\u00e4tze geschafft",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF4CAF50)
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Mehr Gewicht n\u00e4chstes Mal?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(32.dp))

            // Yes button
            Button(
                onClick = onYes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text(
                    "Ja (+${exercise.progressionStepKg.cleanWeight()} \u2192 ${nextWeight.cleanWeight()})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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
    onFinish: () -> Unit
) {
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
                text = "Training abgeschlossen!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Dauer: ${summary.durationMinutes} Minuten",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${summary.exercisesCompleted} von ${summary.totalExercises} \u00dcbungen",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (summary.weightChanges.isNotEmpty()) {
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = change.exerciseName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${change.oldWeight.cleanWeight()} \u2192 ${change.newWeight.cleanWeight()} ${change.weightUnit}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

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

