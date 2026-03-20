package com.fitti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitti.data.SessionExerciseWithSetLogs
import com.fitti.data.WorkoutSessionRepository
import com.fitti.ui.HistoryDetailViewModel
import com.fitti.ui.HistoryDetailViewModelFactory

import com.fitti.ui.common.calculateDuration
import com.fitti.ui.common.cleanWeight
import com.fitti.ui.common.muscleGroupLabels

@Composable
fun HistoryDetailScreen(
    sessionId: Long,
    workoutRepo: WorkoutSessionRepository,
    onBack: () -> Unit
) {
    val vm: HistoryDetailViewModel = viewModel(
        key = "history_$sessionId",
        factory = HistoryDetailViewModelFactory(sessionId, workoutRepo)
    )
    val state by vm.uiState.collectAsState()

    Scaffold { innerPadding ->
        if (state.isLoading) {
            Column(
                Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Laden...", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        val history = state.history
        if (history == null) {
            Column(
                Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Training nicht gefunden.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text("Zur\u00fcck") }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text(
                            "\u2190 Zur\u00fcck",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Training vom ${history.session.completedAt?.substringBefore(" ") ?: history.session.startedAt.substringBefore(" ")}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                val duration = calculateDuration(
                    history.session.startedAt,
                    history.session.completedAt
                )
                if (duration != null) {
                    Text(
                        text = "Dauer: $duration",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${history.sessionExercises.size} \u00dcbungen",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Exercise cards
            items(history.sessionExercises) { exerciseWithLogs ->
                ExerciseHistoryCard(exerciseWithLogs)
            }
        }
    }
}

@Composable
private fun ExerciseHistoryCard(data: SessionExerciseWithSetLogs) {
    val exercise = data.sessionExercise
    val logs = data.setLogs.sortedBy { it.setNumber }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = exercise.exerciseDisplayName.ifEmpty { exercise.exerciseCode },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = muscleGroupLabels[exercise.exerciseMuscleGroup] ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Soll: ${exercise.targetWeight.cleanWeight()} x ${exercise.targetRepsMin}-${exercise.targetReps} x ${exercise.targetSets}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            if (logs.isEmpty()) {
                Text(
                    text = "Keine S\u00e4tze geloggt",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                logs.forEach { log ->
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
        }
    }
}

