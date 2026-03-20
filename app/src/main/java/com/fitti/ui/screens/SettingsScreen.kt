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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitti.data.ExerciseRepository
import com.fitti.data.SettingsRepository
import com.fitti.data.WeightLogDao
import com.fitti.data.WeightLogEntity
import com.fitti.domain.Exercise
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    weightLogDao: WeightLogDao,
    exerciseRepo: ExerciseRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var goal by remember { mutableStateOf(settingsRepo.goal) }
    var heightCm by remember { mutableStateOf(settingsRepo.heightCm.let { if (it == 0) "" else it.toString() }) }
    var weightKg by remember { mutableStateOf("") }
    var repsMin by remember { mutableStateOf(settingsRepo.repsMin.toString()) }
    var repsMax by remember { mutableStateOf(settingsRepo.repsMax.toString()) }
    var sets by remember { mutableStateOf(settingsRepo.sets.toString()) }
    var restSeconds by remember { mutableStateOf(settingsRepo.restSeconds.toString()) }
    var progressionStep by remember { mutableStateOf(settingsRepo.progressionStepKg.toString()) }
    var lastWeightInfo by remember { mutableStateOf("") }
    var exercises by remember { mutableStateOf(emptyList<Exercise>()) }
    // Per-exercise progression steps: exerciseId -> step text
    var exerciseSteps by remember { mutableStateOf(emptyMap<Long, String>()) }

    // Load last weight and exercises
    var loaded by remember { mutableStateOf(false) }
    if (!loaded) {
        loaded = true
        scope.launch {
            val latest = weightLogDao.getLatest()
            if (latest != null) {
                weightKg = latest.weightKg.toString()
                lastWeightInfo = "Zuletzt: ${latest.loggedAt}"
            }
        }
        scope.launch {
            exerciseRepo.observeExercises().collect { list ->
                exercises = list.sortedBy { it.sortOrder }
                if (exerciseSteps.isEmpty()) {
                    exerciseSteps = list.associate { it.id to it.progressionStepKg.toString() }
                }
            }
        }
    }

    Scaffold { innerPadding ->
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
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Einstellungen",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Profile section
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Mein Profil",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("Ziel (z.B. Muskelaufbau, Abnehmen)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = heightCm,
                    onValueChange = { heightCm = it },
                    label = { Text("Gr\u00f6\u00dfe (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = weightKg,
                    onValueChange = { weightKg = it },
                    label = { Text("Aktuelles Gewicht (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = if (lastWeightInfo.isNotEmpty()) {
                        { Text(lastWeightInfo) }
                    } else null
                )
            }

            // Training section
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Training",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Text(
                    text = "Wiederholungen (Bereich)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = repsMin,
                        onValueChange = { repsMin = it },
                        label = { Text("Min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Text("-", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = repsMax,
                        onValueChange = { repsMax = it },
                        label = { Text("Max") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = sets,
                    onValueChange = { sets = it },
                    label = { Text("S\u00e4tze pro \u00dcbung") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = restSeconds,
                    onValueChange = { restSeconds = it },
                    label = { Text("Pause zwischen S\u00e4tzen (Sekunden)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = progressionStep,
                    onValueChange = { progressionStep = it },
                    label = { Text("Standard-Gewichtssteigerung (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Exercises section
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "\u00dcbungen (Reihenfolge & Steigerung)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(exercises.size) { index ->
                val exercise = exercises[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reorder buttons
                    Column {
                        TextButton(
                            onClick = {
                                if (index > 0) {
                                    val mutable = exercises.toMutableList()
                                    val prev = mutable[index - 1]
                                    mutable[index - 1] = exercise
                                    mutable[index] = prev
                                    exercises = mutable
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("\u25B2", style = MaterialTheme.typography.bodyLarge)
                        }
                        TextButton(
                            onClick = {
                                if (index < exercises.size - 1) {
                                    val mutable = exercises.toMutableList()
                                    val next = mutable[index + 1]
                                    mutable[index + 1] = exercise
                                    mutable[index] = next
                                    exercises = mutable
                                }
                            },
                            enabled = index < exercises.size - 1,
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("\u25BC", style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    // Exercise name
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exercise.displayName.ifEmpty { exercise.code },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = exercise.code,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Per-exercise progression step
                    OutlinedTextField(
                        value = exerciseSteps[exercise.id] ?: exercise.progressionStepKg.toString(),
                        onValueChange = { exerciseSteps = exerciseSteps + (exercise.id to it) },
                        label = { Text("kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(80.dp),
                        singleLine = true
                    )
                }
            }

            // Save button
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        // Validate training settings
                        val parsedRepsMin = repsMin.toIntOrNull()?.coerceIn(1, 50) ?: 8
                        val parsedRepsMax = repsMax.toIntOrNull()?.coerceIn(1, 50) ?: 12
                        val validRepsMin = minOf(parsedRepsMin, parsedRepsMax)
                        val validRepsMax = maxOf(parsedRepsMin, parsedRepsMax)
                        val validSets = sets.toIntOrNull()?.coerceIn(1, 10) ?: 2
                        val validRest = restSeconds.toIntOrNull()?.coerceIn(10, 600) ?: 90
                        val validStep = progressionStep.replace(",", ".").toDoubleOrNull()?.coerceIn(0.5, 20.0) ?: 2.5

                        // Save profile
                        settingsRepo.goal = goal
                        settingsRepo.heightCm = heightCm.toIntOrNull()?.coerceIn(0, 300) ?: 0

                        // Save weight if changed
                        val weight = weightKg.replace(",", ".").toDoubleOrNull()
                        if (weight != null && weight in 20.0..300.0) {
                            scope.launch {
                                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
                                weightLogDao.insert(
                                    WeightLogEntity(
                                        weightKg = weight,
                                        loggedAt = dateFormat.format(Date())
                                    )
                                )
                            }
                        }

                        // Save validated training settings
                        settingsRepo.repsMin = validRepsMin
                        settingsRepo.repsMax = validRepsMax
                        settingsRepo.sets = validSets
                        settingsRepo.restSeconds = validRest
                        settingsRepo.progressionStepKg = validStep

                        // Save per-exercise settings (sortOrder + progressionStep)
                        scope.launch {
                            exercises.forEachIndexed { idx, ex ->
                                exerciseRepo.updateSortOrder(ex.id, idx)
                                val stepText = exerciseSteps[ex.id] ?: ex.progressionStepKg.toString()
                                val step = stepText.replace(",", ".").toDoubleOrNull()?.coerceIn(0.5, 20.0) ?: ex.progressionStepKg
                                exerciseRepo.updateProgressionStep(ex.id, step)
                            }
                        }

                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Speichern",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
