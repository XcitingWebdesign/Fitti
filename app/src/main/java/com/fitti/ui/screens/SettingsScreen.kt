package com.fitti.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fitti.data.ExerciseEntity
import com.fitti.data.ExerciseRepository
import com.fitti.data.SettingsRepository
import com.fitti.data.WeightLogDao
import com.fitti.data.WeightLogEntity
import com.fitti.domain.Exercise
import com.fitti.ui.common.muscleGroupLabels
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val allMuscleGroups = listOf("CHEST", "BACK", "LEGS", "SHOULDERS", "ARMS")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    weightLogDao: WeightLogDao,
    exerciseRepo: ExerciseRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var goal by remember { mutableStateOf(settingsRepo.goal) }
    var apiKey by remember { mutableStateOf(settingsRepo.claudeApiKey) }
    var heightCm by remember { mutableStateOf(settingsRepo.heightCm.let { if (it == 0) "" else it.toString() }) }
    var weightKg by remember { mutableStateOf("") }
    var repsMin by remember { mutableStateOf(settingsRepo.repsMin.toString()) }
    var repsMax by remember { mutableStateOf(settingsRepo.repsMax.toString()) }
    var sets by remember { mutableStateOf(settingsRepo.sets.toString()) }
    var restSeconds by remember { mutableStateOf(settingsRepo.restSeconds.toString()) }
    var progressionStep by remember { mutableStateOf(settingsRepo.progressionStepKg.toString()) }
    var lastWeightInfo by remember { mutableStateOf("") }
    var exercises by remember { mutableStateOf(emptyList<Exercise>()) }
    var exerciseSteps by remember { mutableStateOf(emptyMap<Long, String>()) }

    // Add exercise dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    // Delete exercise confirmation state
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }
    // Import status message
    var importMessage by remember { mutableStateOf("") }

    // File picker for import (JSON or CSV)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@launch
                val imported = if (text.trimStart().startsWith("{")) {
                    parseExercisesJson(text)
                } else {
                    parseExercisesCsv(text)
                }
                if (imported.isNotEmpty()) {
                    exerciseRepo.replaceAll(imported)
                    importMessage = "${imported.size} Ger\u00e4te importiert"
                } else {
                    importMessage = "Keine Ger\u00e4te in Datei gefunden"
                }
            } catch (e: Exception) {
                importMessage = "Import fehlgeschlagen: ${e.message}"
            }
        }
    }

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
                    label = { Text("Mein Ziel (frei formuliert)") },
                    placeholder = { Text("z.B. Mit 70 noch meine Frau tragen k\u00f6nnen") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u00dcbungen (Reihenfolge & Steigerung)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { showAddDialog = true }) {
                        Text(
                            "+ Ger\u00e4t",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(exercises.size) { index ->
                val exercise = exercises[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                            text = "${exercise.code} \u2022 ${muscleGroupLabels[exercise.muscleGroup] ?: exercise.muscleGroup}",
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
                        modifier = Modifier.width(72.dp),
                        singleLine = true
                    )

                    // Delete button
                    TextButton(
                        onClick = { exerciseToDelete = exercise },
                        modifier = Modifier.size(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "\u2715",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Data section (Export/Import)
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Daten",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val entities = exerciseRepo.getAllEntities()
                                val json = exportExercisesJson(entities)
                                shareText(context, json, "fitti_geraete.json")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ger\u00e4te exportieren")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ger\u00e4te importieren")
                    }
                }
                if (importMessage.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = importMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // KI-Feedback section
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "KI-Feedback",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Claude API-Schl\u00fcssel") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Optional. Erm\u00f6glicht KI-Feedback nach dem Training.") }
                )
            }

            // Save button
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val parsedRepsMin = repsMin.toIntOrNull()?.coerceIn(1, 50) ?: 8
                        val parsedRepsMax = repsMax.toIntOrNull()?.coerceIn(1, 50) ?: 12
                        val validRepsMin = minOf(parsedRepsMin, parsedRepsMax)
                        val validRepsMax = maxOf(parsedRepsMin, parsedRepsMax)
                        val validSets = sets.toIntOrNull()?.coerceIn(1, 10) ?: 2
                        val validRest = restSeconds.toIntOrNull()?.coerceIn(10, 600) ?: 90
                        val validStep = progressionStep.replace(",", ".").toDoubleOrNull()?.coerceIn(0.5, 20.0) ?: 2.5

                        settingsRepo.goal = goal
                        settingsRepo.heightCm = heightCm.toIntOrNull()?.coerceIn(0, 300) ?: 0
                        settingsRepo.claudeApiKey = apiKey.trim()

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

                        settingsRepo.repsMin = validRepsMin
                        settingsRepo.repsMax = validRepsMax
                        settingsRepo.sets = validSets
                        settingsRepo.restSeconds = validRest
                        settingsRepo.progressionStepKg = validStep

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

    // Add Exercise Dialog
    if (showAddDialog) {
        AddExerciseDialog(
            defaultProgressionStep = settingsRepo.progressionStepKg,
            onDismiss = { showAddDialog = false },
            onAdd = { entity ->
                scope.launch {
                    val maxSort = exercises.maxOfOrNull { it.sortOrder } ?: -1
                    exerciseRepo.addExercise(entity.copy(sortOrder = maxSort + 1))
                }
                showAddDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (exerciseToDelete != null) {
        val ex = exerciseToDelete!!
        AlertDialog(
            onDismissRequest = { exerciseToDelete = null },
            title = { Text("Ger\u00e4t entfernen") },
            text = {
                Text("\"${ex.displayName.ifEmpty { ex.code }}\" wirklich entfernen? Bestehende Trainingshistorie bleibt erhalten.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { exerciseRepo.deleteExercise(ex.id) }
                        exerciseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Entfernen")
                }
            },
            dismissButton = {
                TextButton(onClick = { exerciseToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseDialog(
    defaultProgressionStep: Double,
    onDismiss: () -> Unit,
    onAdd: (ExerciseEntity) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("CHEST") }
    var weight by remember { mutableStateOf("") }
    var weightUnit by remember { mutableStateOf("kg") }
    var step by remember { mutableStateOf(defaultProgressionStep.toString()) }
    var groupExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ger\u00e4t hinzuf\u00fcgen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Ger\u00e4te-Code (z.B. A1)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Name (z.B. Bicep Curl)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Muscle group dropdown
                ExposedDropdownMenuBox(
                    expanded = groupExpanded,
                    onExpandedChange = { groupExpanded = it }
                ) {
                    OutlinedTextField(
                        value = muscleGroupLabels[selectedGroup] ?: selectedGroup,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Muskelgruppe") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = groupExpanded,
                        onDismissRequest = { groupExpanded = false }
                    ) {
                        allMuscleGroups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(muscleGroupLabels[group] ?: group) },
                                onClick = {
                                    selectedGroup = group
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Gewicht") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    // Unit dropdown
                    ExposedDropdownMenuBox(
                        expanded = unitExpanded,
                        onExpandedChange = { unitExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = weightUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Einheit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                            modifier = Modifier.width(100.dp).menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = unitExpanded,
                            onDismissRequest = { unitExpanded = false }
                        ) {
                            listOf("kg", "lb").forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit) },
                                    onClick = {
                                        weightUnit = unit
                                        unitExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = step,
                    onValueChange = { step = it },
                    label = { Text("Steigerung (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedWeight = weight.replace(",", ".").toDoubleOrNull() ?: return@Button
                    val parsedStep = step.replace(",", ".").toDoubleOrNull()?.coerceIn(0.5, 20.0) ?: defaultProgressionStep
                    if (code.isBlank()) return@Button
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                    onAdd(
                        ExerciseEntity(
                            code = code.trim(),
                            brand = "",
                            displayName = displayName.trim(),
                            muscleGroup = selectedGroup,
                            currentWeight = parsedWeight,
                            weightUnit = weightUnit,
                            recordedOn = dateFormat.format(Date()),
                            progressionStepKg = parsedStep
                        )
                    )
                }
            ) {
                Text("Hinzuf\u00fcgen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

private fun exportExercisesJson(entities: List<ExerciseEntity>): String {
    val root = JSONObject()
    root.put("version", 1)
    val arr = JSONArray()
    for (e in entities) {
        val obj = JSONObject()
        obj.put("code", e.code)
        obj.put("brand", e.brand)
        obj.put("displayName", e.displayName)
        obj.put("muscleGroup", e.muscleGroup)
        obj.put("currentWeight", e.currentWeight)
        obj.put("weightUnit", e.weightUnit)
        obj.put("progressionStepKg", e.progressionStepKg)
        obj.put("sortOrder", e.sortOrder)
        arr.put(obj)
    }
    root.put("exercises", arr)
    return root.toString(2)
}

private fun parseExercisesJson(json: String): List<ExerciseEntity> {
    val root = JSONObject(json)
    val arr = root.getJSONArray("exercises")
    val result = mutableListOf<ExerciseEntity>()
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    val today = dateFormat.format(Date())
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        result.add(
            ExerciseEntity(
                code = obj.getString("code"),
                brand = obj.optString("brand", ""),
                displayName = obj.optString("displayName", ""),
                muscleGroup = obj.optString("muscleGroup", ""),
                currentWeight = obj.getDouble("currentWeight"),
                weightUnit = obj.optString("weightUnit", "kg"),
                recordedOn = today,
                progressionStepKg = obj.optDouble("progressionStepKg", 2.5),
                sortOrder = obj.optInt("sortOrder", i)
            )
        )
    }
    return result
}

private fun parseExercisesCsv(csv: String): List<ExerciseEntity> {
    val lines = csv.trim().lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()
    val header = lines.first().split(",").map { it.trim().lowercase() }
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    val today = dateFormat.format(Date())
    return lines.drop(1).mapIndexed { index, line ->
        val cols = line.split(",").map { it.trim() }
        fun col(name: String): String = header.indexOf(name).let { if (it >= 0 && it < cols.size) cols[it] else "" }
        ExerciseEntity(
            code = col("code"),
            brand = col("brand"),
            displayName = col("displayname"),
            muscleGroup = col("musclegroup"),
            currentWeight = col("currentweight").replace(",", ".").toDoubleOrNull() ?: 0.0,
            weightUnit = col("weightunit").ifEmpty { "kg" },
            recordedOn = today,
            progressionStepKg = col("progressionstepkg").replace(",", ".").toDoubleOrNull() ?: 2.5,
            sortOrder = col("sortorder").toIntOrNull() ?: index
        )
    }
}

private fun shareText(context: Context, text: String, filename: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, filename)
    }
    context.startActivity(
        Intent.createChooser(intent, "Ger\u00e4te exportieren")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
