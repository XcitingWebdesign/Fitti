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
        latestWeightKg: Double?
    ): Result<String> {
        val systemPrompt = "Du bist ein freundlicher Fitness-Coach in einer Trainings-App. " +
                "Antworte immer auf Deutsch. Halte deine Antworten kurz und motivierend (3\u20135 S\u00e4tze). " +
                "Verwende einfache Sprache."

        val userMessage = buildString {
            appendLine("Hier ist mein heutiges Training:")
            appendLine()
            appendLine(formatSessionData(history))
            if (userGoal.isNotBlank()) {
                appendLine("Mein Ziel: $userGoal")
            }
            if (latestWeightKg != null) {
                appendLine("Mein K\u00f6rpergewicht: $latestWeightKg kg")
            }
            appendLine()
            appendLine("Gib mir kurzes Feedback: Was war gut? Was kann ich verbessern?")
        }

        return callClaude(systemPrompt, userMessage)
    }

    suspend fun getWeeklyAnalysis(
        sessions: List<WorkoutSessionHistory>,
        userGoal: String,
        latestWeightKg: Double?
    ): Result<String> {
        val systemPrompt = "Du bist ein freundlicher Fitness-Coach in einer Trainings-App. " +
                "Antworte immer auf Deutsch. Gib eine Wochenanalyse in 5\u20138 S\u00e4tzen. " +
                "Verwende einfache Sprache."

        val userMessage = buildString {
            appendLine("Hier sind meine Trainings der letzten Woche (${sessions.size} Einheiten):")
            appendLine()
            sessions.forEach { history ->
                appendLine(formatSessionData(history))
                appendLine("---")
            }
            if (userGoal.isNotBlank()) {
                appendLine("Mein Ziel: $userGoal")
            }
            if (latestWeightKg != null) {
                appendLine("Mein K\u00f6rpergewicht: $latestWeightKg kg")
            }
            appendLine()
            appendLine("Gib mir eine Wochenanalyse: Trainingsvolumen, Fortschritte, Empfehlungen f\u00fcr die n\u00e4chste Woche.")
        }

        return callClaude(systemPrompt, userMessage)
    }

    private fun formatSessionData(history: WorkoutSessionHistory): String = buildString {
        val session = history.session
        appendLine("Datum: ${session.startedAt}")
        if (session.completedAt != null) {
            appendLine("Abgeschlossen: ${session.completedAt}")
        }

        history.sessionExercises.forEach { exerciseWithLogs ->
            val ex = exerciseWithLogs.sessionExercise
            appendLine("- ${ex.exerciseDisplayName} (${ex.exerciseMuscleGroup}): " +
                    "Soll ${ex.targetWeight} kg x ${ex.targetRepsMin}-${ex.targetReps} Wdh x ${ex.targetSets} S\u00e4tze")

            val logs = exerciseWithLogs.setLogs.sortedBy { it.setNumber }
            if (logs.isEmpty()) {
                appendLine("  \u00dcbersprungen")
            } else {
                logs.forEach { log ->
                    val status = if (log.completedFlag) "\u2713" else "\u2717"
                    appendLine("  Satz ${log.setNumber}: ${log.actualWeightKg} kg x ${log.actualReps} Wdh $status")
                }
            }
        }
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
                    put("model", "claude-sonnet-4-20250514")
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
