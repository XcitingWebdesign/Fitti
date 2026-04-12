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
        heightCm: Int = 0,
        allHistories: List<WorkoutSessionHistory> = emptyList(),
        weightLogs: List<WeightLogEntity> = emptyList()
    ): Result<String> {
        val systemPrompt = "Du bist ein erfahrener Personal Trainer, der Zirkeltraining an Maschinen betreut. " +
                "Dein Klient trainiert regelm\u00e4\u00dfig an Kraftmaschinen (Nautilus, etc.) im Zirkel.\n\n" +
                "Du erh\u00e4ltst das heutige Training UND die Trainingshistorie der letzten Wochen/Monate.\n" +
                "Analysiere das Training im Kontext der langfristigen Entwicklung:\n" +
                "- Leistungsqualit\u00e4t: Wurden alle S\u00e4tze im Zielbereich geschafft?\n" +
                "- Progression: Wie entwickeln sich die Gewichte \u00fcber Wochen/Monate? Wo wird gesteigert, wo stagniert es?\n" +
                "- Intensit\u00e4t: Waren die Gewichte angemessen (alle Max-Reps = evtl. zu leicht, Failed Sets = zu schwer)?\n" +
                "- Konsistenz: Wie regelm\u00e4\u00dfig wird trainiert? Gibt es L\u00fccken?\n" +
                "- K\u00f6rpergewicht: Passt die Gewichtsentwicklung zum Ziel?\n\n" +
                "Antworte auf Deutsch in 4\u20138 S\u00e4tzen. Sei direkt, motivierend und konkret. " +
                "Nenne die \u00dcbungen beim Namen. Ordne die heutige Leistung in den Gesamttrend ein."

        val userMessage = buildString {
            appendLine("=== Heutiges Training ===")
            appendLine(formatSessionData(history))
            appendLine()
            if (allHistories.size > 1) {
                appendLine(formatHistoricalSummary(allHistories, weightLogs))
                appendLine()
            }
            appendLine(formatProfile(userGoal, latestWeightKg, heightCm))
        }

        return callClaude(systemPrompt, userMessage)
    }

    suspend fun getWeeklyAnalysis(
        sessions: List<WorkoutSessionHistory>,
        userGoal: String,
        latestWeightKg: Double?,
        heightCm: Int = 0,
        allHistories: List<WorkoutSessionHistory> = emptyList(),
        weightLogs: List<WeightLogEntity> = emptyList()
    ): Result<String> {
        val systemPrompt = "Du bist ein erfahrener Personal Trainer, der Zirkeltraining an Maschinen betreut. " +
                "Dein Klient trainiert regelm\u00e4\u00dfig an Kraftmaschinen (Nautilus, etc.) im Zirkel.\n\n" +
                "Du erh\u00e4ltst die Trainings der letzten Woche UND die langfristige Trainingshistorie.\n" +
                "Erstelle eine Analyse im Kontext der Gesamtentwicklung:\n" +
                "- Trainingsh\u00e4ufigkeit: Genug Einheiten pro Woche? Wie ist der Trend?\n" +
                "- Langzeitprogression: Welche Gewichte wurden \u00fcber Wochen/Monate gesteigert? Wo stagniert es?\n" +
                "- Muskelgruppenbalance: Werden alle Gruppen gleichm\u00e4\u00dfig belastet?\n" +
                "- K\u00f6rpergewicht: Entwicklung passend zum Ziel?\n" +
                "- Erholung: Genug Pause zwischen Trainings der gleichen Muskelgruppe?\n\n" +
                "Antworte auf Deutsch in 5\u201310 S\u00e4tzen. Sei direkt und konkret. " +
                "Gib 1\u20132 spezifische Empfehlungen. Ordne die Woche in den Gesamttrend ein."

        val userMessage = buildString {
            appendLine("=== Trainings der letzten Woche (${sessions.size} Einheiten) ===")
            appendLine()
            sessions.forEachIndexed { index, history ->
                appendLine("--- Training ${index + 1} ---")
                appendLine(formatSessionData(history))
                appendLine()
            }
            val historiesForSummary = if (allHistories.isNotEmpty()) allHistories else sessions
            if (historiesForSummary.size > sessions.size) {
                appendLine(formatHistoricalSummary(historiesForSummary, weightLogs))
                appendLine()
            }
            appendLine(formatProfile(userGoal, latestWeightKg, heightCm))
        }

        return callClaude(systemPrompt, userMessage)
    }

    private fun formatProfile(userGoal: String, latestWeightKg: Double?, heightCm: Int): String = buildString {
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

    private fun formatHistoricalSummary(
        allHistories: List<WorkoutSessionHistory>,
        weightLogs: List<WeightLogEntity>
    ): String = buildString {
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY)
        val dateFmt = java.text.SimpleDateFormat("dd.MM", java.util.Locale.GERMANY)

        appendLine("=== Trainingshistorie ===")

        // Training frequency
        val totalSessions = allHistories.size
        if (totalSessions > 0) {
            val dates = allHistories.mapNotNull { fmt.parse(it.session.completedAt ?: it.session.startedAt) }.sorted()
            if (dates.size >= 2) {
                val spanDays = ((dates.last().time - dates.first().time) / 86400000).toInt().coerceAtLeast(1)
                val spanWeeks = (spanDays / 7.0).coerceAtLeast(1.0)
                appendLine("$totalSessions Einheiten in $spanDays Tagen (\u00d8 ${"%.1f".format(totalSessions / spanWeeks)}x/Woche)")
            } else {
                appendLine("$totalSessions Einheit(en)")
            }
        }

        // Weight progression per exercise (condensed: first seen → last seen)
        appendLine()
        appendLine("Gewichtsverlauf pro \u00dcbung:")
        // exerciseName -> list of (date, weight)
        val exerciseWeights = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        for (history in allHistories.sortedBy { it.session.startedAt }) {
            val dateLabel = dateFmt.format(
                fmt.parse(history.session.completedAt ?: history.session.startedAt) ?: continue
            )
            for (exWithLogs in history.sessionExercises) {
                val ex = exWithLogs.sessionExercise
                val name = ex.exerciseDisplayName.ifEmpty { ex.exerciseCode }
                val logs = exWithLogs.setLogs
                if (logs.isNotEmpty()) {
                    val maxWeight = logs.maxOf { it.actualWeightKg }
                    exerciseWeights.getOrPut(name) { mutableListOf() }.add(dateLabel to maxWeight)
                }
            }
        }

        for ((name, entries) in exerciseWeights) {
            if (entries.size <= 1) {
                val (date, weight) = entries.first()
                appendLine("- $name: ${"%.1f".format(weight)} kg ($date) [nur 1 Datenpunkt]")
                continue
            }
            // Show key points: first, any changes, last
            val milestones = mutableListOf(entries.first())
            var lastWeight = entries.first().second
            for (i in 1 until entries.size) {
                if (entries[i].second != lastWeight) {
                    milestones.add(entries[i])
                    lastWeight = entries[i].second
                }
            }
            if (milestones.last() != entries.last()) {
                milestones.add(entries.last())
            }

            val trajectory = milestones.joinToString(" \u2192 ") {
                "${"%.1f".format(it.second)} kg (${it.first})"
            }
            val delta = entries.last().second - entries.first().second
            val trend = when {
                delta > 0 -> "+${"%.1f".format(delta)} kg"
                delta < 0 -> "${"%.1f".format(delta)} kg"
                else -> "gleichbleibend"
            }
            appendLine("- $name: $trajectory [$trend]")
        }

        // Body weight trend
        if (weightLogs.size >= 2) {
            appendLine()
            appendLine("K\u00f6rpergewicht-Verlauf:")
            val sorted = weightLogs.sortedBy { it.id }
            val wlFmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY)
            val milestones = mutableListOf(sorted.first())
            // Show first, significant changes (>0.5kg), last
            var lastW = sorted.first().weightKg
            for (i in 1 until sorted.size) {
                if (kotlin.math.abs(sorted[i].weightKg - lastW) >= 0.5) {
                    milestones.add(sorted[i])
                    lastW = sorted[i].weightKg
                }
            }
            if (milestones.last() != sorted.last()) {
                milestones.add(sorted.last())
            }
            val trajectory = milestones.joinToString(" \u2192 ") {
                val date = wlFmt.parse(it.loggedAt)
                val label = if (date != null) dateFmt.format(date) else it.loggedAt
                "${"%.1f".format(it.weightKg)} kg ($label)"
            }
            val delta = sorted.last().weightKg - sorted.first().weightKg
            val trend = when {
                delta > 0 -> "+${"%.1f".format(delta)} kg"
                delta < 0 -> "${"%.1f".format(delta)} kg"
                else -> "stabil"
            }
            appendLine("$trajectory [$trend]")
        }
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
