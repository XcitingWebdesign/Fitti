package com.fitti.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ClaudeApiService(private val apiKey: String) {

    suspend fun getWorkoutFeedback(
        history: WorkoutSessionHistory,
        userGoal: String,
        latestWeightKg: Double?,
        heightCm: Int = 0
    ): Result<String> {
        val systemPrompt = "Du bist ein erfahrener Personal Trainer, der Zirkeltraining an Maschinen betreut. " +
                "Dein Klient trainiert regelm\u00e4\u00dfig an Kraftmaschinen (Nautilus, etc.) im Zirkel.\n\n" +
                "Analysiere das Training wie ein aufmerksamer Coach:\n" +
                "- Leistungsqualit\u00e4t: Wurden alle S\u00e4tze im Zielbereich geschafft? Wo wurde gesteigert?\n" +
                "- Intensit\u00e4t: Waren die Gewichte angemessen (alle Max-Reps = evtl. zu leicht, Failed Sets = zu schwer)?\n" +
                "- Tempo & Dauer: War die Trainingszeit pro \u00dcbung und insgesamt angemessen?\n" +
                "- Muskelgruppenbalance: Wurden alle Gruppen trainiert?\n\n" +
                "Antworte auf Deutsch in 4\u20136 S\u00e4tzen. Sei direkt, motivierend und konkret. " +
                "Nenne die \u00dcbungen beim Namen. Gib einen konkreten Verbesserungstipp."

        val userMessage = buildString {
            appendLine("=== Heutiges Training ===")
            appendLine(formatSessionData(history))
            appendLine()
            appendLine("=== Mein Profil ===")
            if (userGoal.isNotBlank()) {
                appendLine("Ziel: $userGoal")
            }
            if (latestWeightKg != null) {
                appendLine("K\u00f6rpergewicht: $latestWeightKg kg")
            }
            if (heightCm > 0) {
                appendLine("Gr\u00f6\u00dfe: $heightCm cm")
            }
        }

        return callClaude(systemPrompt, userMessage)
    }

    suspend fun getWeeklyAnalysis(
        sessions: List<WorkoutSessionHistory>,
        userGoal: String,
        latestWeightKg: Double?,
        heightCm: Int = 0
    ): Result<String> {
        val systemPrompt = "Du bist ein erfahrener Personal Trainer, der Zirkeltraining an Maschinen betreut. " +
                "Dein Klient trainiert regelm\u00e4\u00dfig an Kraftmaschinen (Nautilus, etc.) im Zirkel.\n\n" +
                "Erstelle eine Wochenanalyse:\n" +
                "- Trainingsh\u00e4ufigkeit: Genug Einheiten pro Woche f\u00fcr das Ziel?\n" +
                "- Progression: Welche Gewichte wurden gesteigert? Wo stagniert es?\n" +
                "- Muskelgruppenbalance: Werden alle Gruppen gleichm\u00e4\u00dfig belastet?\n" +
                "- Erholung: Genug Pause zwischen Trainings der gleichen Muskelgruppe?\n\n" +
                "Antworte auf Deutsch in 5\u20138 S\u00e4tzen. Sei direkt und konkret. " +
                "Gib 1\u20132 spezifische Empfehlungen f\u00fcr die n\u00e4chste Woche."

        val userMessage = buildString {
            appendLine("=== Trainings der letzten Woche (${sessions.size} Einheiten) ===")
            appendLine()
            sessions.forEachIndexed { index, history ->
                appendLine("--- Training ${index + 1} ---")
                appendLine(formatSessionData(history))
                appendLine()
            }
            appendLine("=== Mein Profil ===")
            if (userGoal.isNotBlank()) {
                appendLine("Ziel: $userGoal")
            }
            if (latestWeightKg != null) {
                appendLine("K\u00f6rpergewicht: $latestWeightKg kg")
            }
            if (heightCm > 0) {
                appendLine("Gr\u00f6\u00dfe: $heightCm cm")
            }
        }

        return callClaude(systemPrompt, userMessage)
    }

    private fun formatSessionData(history: WorkoutSessionHistory): String = buildString {
        val session = history.session
        appendLine("Datum: ${session.startedAt}")
        if (session.completedAt != null) {
            appendLine("Abgeschlossen: ${session.completedAt}")
        }

        val muscleGroups = mutableMapOf<String, Int>()
        var totalVolume = 0.0

        history.sessionExercises.forEach { exerciseWithLogs ->
            val ex = exerciseWithLogs.sessionExercise
            val logs = exerciseWithLogs.setLogs.sortedBy { it.setNumber }

            append("- ${ex.exerciseDisplayName} [${ex.exerciseMuscleGroup}]")
            append(": Soll ${ex.targetWeight} kg x ${ex.targetRepsMin}-${ex.targetReps} Wdh x ${ex.targetSets} S\u00e4tze")

            // Exercise duration
            if (ex.exerciseStartedAt != null && ex.exerciseCompletedAt != null) {
                try {
                    val fmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY)
                    val start = fmt.parse(ex.exerciseStartedAt)
                    val end = fmt.parse(ex.exerciseCompletedAt)
                    if (start != null && end != null) {
                        val mins = (end.time - start.time) / 60000
                        append(" ($mins Min)")
                    }
                } catch (_: Exception) {}
            }
            appendLine()

            if (logs.isEmpty()) {
                appendLine("  \u00dcbersprungen")
            } else {
                logs.forEach { log ->
                    val status = if (log.completedFlag) "\u2713" else "\u2717"
                    appendLine("  Satz ${log.setNumber}: ${log.actualWeightKg} kg x ${log.actualReps} Wdh $status")
                    totalVolume += log.actualWeightKg * log.actualReps
                }
            }

            // Track muscle group sets
            if (logs.isNotEmpty()) {
                muscleGroups[ex.exerciseMuscleGroup] =
                    (muscleGroups[ex.exerciseMuscleGroup] ?: 0) + logs.size
            }
        }

        appendLine()
        appendLine("Gesamtvolumen: ${"%.0f".format(totalVolume)} kg")
        appendLine("S\u00e4tze pro Muskelgruppe: ${muscleGroups.entries.joinToString(", ") { "${it.key}: ${it.value}" }}")
    }

    private suspend fun callClaude(systemPrompt: String, userMessage: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.anthropic.com/v1/messages")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
                connection.doOutput = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000

                val body = JSONObject().apply {
                    put("model", "claude-sonnet-4-6")
                    put("max_tokens", 1024)
                    put("system", systemPrompt)
                    put("messages", JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userMessage)
                        }
                    ))
                }

                connection.outputStream.use { os ->
                    os.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unbekannter Fehler"
                    return@withContext Result.failure(Exception("API-Fehler ($responseCode): $errorBody"))
                }

                val responseBody = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseBody)
                val content = json.getJSONArray("content")
                val text = content.getJSONObject(0).getString("text")

                Result.success(text)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
