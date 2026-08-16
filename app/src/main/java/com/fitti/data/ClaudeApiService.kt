package com.fitti.data

import com.fitti.domain.ProgressionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ClaudeApiService(
    private val apiKey: String,
    private val sonnetModel: String = SettingsRepository.DEFAULT_SONNET_MODEL,
    private val opusModel: String = SettingsRepository.DEFAULT_OPUS_MODEL,
    private val coachPersona: String = "",
) {

    // Persona nur als Tonalitaet anhaengen – inhaltliche und Format-Regeln
    // (z.B. der <plan>-Block) behalten explizit Vorrang.
    private fun withPersona(systemPrompt: String): String {
        if (coachPersona.isBlank()) return systemPrompt
        return systemPrompt + "\n\nCHARAKTER/TONALIT\u00c4T DES COACHES (vom Klienten vorgegeben): " +
                "Sprich in folgendem Stil, ohne die inhaltlichen und formalen Regeln oben zu verletzen:\n" +
                coachPersona
    }

    suspend fun getWorkoutFeedback(
        history: WorkoutSessionHistory,
        userGoal: String,
        latestWeightKg: Double?,
        heightCm: Int = 0,
        allHistories: List<WorkoutSessionHistory> = emptyList(),
        weightLogs: List<WeightLogEntity> = emptyList(),
        bodyMeasurements: List<BodyMeasurementEntity> = emptyList(),
        nutritionLogs: List<NutritionLogEntity> = emptyList()
    ): Result<String> {
        val systemPrompt = "Du bist ein erfahrener Personal Trainer, der Zirkeltraining an Kraftmaschinen betreut. " +
                "Dein Klient trainiert regelm\u00e4\u00dfig im Zirkel.\n\n" +
                "Dein Ziel ergibt sich aus dem Ziel des Klienten \u2013 du arbeitest darauf hin, dieses bestm\u00f6glich zu erreichen. " +
                "Dabei gilt als nicht verhandelbare Regel: Slow and steady am Optimum ist besser als \u00dcberlastung und Verletzung.\n\n" +
                "Du erh\u00e4ltst das heutige Training UND die Trainingshistorie der letzten Wochen/Monate.\n" +
                "Analysiere das Training im Kontext der langfristigen Entwicklung:\n" +
                "- Leistungsqualit\u00e4t: Wurden alle S\u00e4tze im Zielbereich geschafft?\n" +
                "- Progression: Wie entwickeln sich die Gewichte \u00fcber Wochen/Monate? Wo wird gesteigert, wo stagniert es?\n" +
                "- Intensit\u00e4t: Waren die Gewichte angemessen (alle Max-Reps = evtl. zu leicht, Failed Sets = zu schwer)?\n" +
                "- Konsistenz: Wie regelm\u00e4\u00dfig wird trainiert? Gibt es L\u00fccken?\n" +
                "- K\u00f6rpergewicht: Passt die Gewichtsentwicklung zum Ziel?\n\n" +
                "Antworte auf Deutsch in 4\u20138 S\u00e4tzen. Sei direkt, motivierend und konkret. " +
                "Nenne die \u00dcbungen beim Namen (gerne mit Code in Klammern). Ordne die heutige Leistung in den Gesamttrend ein. " +
                "Konkrete Gewichtsempfehlungen bleiben bei der w\u00f6chentlichen Coaching-Sitzung \u2013 hier nur einordnen."

        val userMessage = buildString {
            appendLine("=== Heutiges Training ===")
            appendLine(formatSessionData(history))
            appendLine()
            if (allHistories.size > 1) {
                appendLine(formatHistoricalSummary(allHistories, weightLogs))
                appendLine()
            }
            val tape = formatBodyMeasurements(bodyMeasurements)
            if (tape.isNotBlank()) {
                appendLine(tape)
                appendLine()
            }
            val nutrition = formatNutrition(nutritionLogs)
            if (nutrition.isNotBlank()) {
                appendLine(nutrition)
                appendLine()
            }
            appendLine(formatProfile(userGoal, latestWeightKg, heightCm))
        }

        return callClaude(withPersona(systemPrompt), userMessage)
    }

    suspend fun getWeeklyAnalysis(
        sessions: List<WorkoutSessionHistory>,
        userGoal: String,
        latestWeightKg: Double?,
        heightCm: Int = 0,
        allHistories: List<WorkoutSessionHistory> = emptyList(),
        weightLogs: List<WeightLogEntity> = emptyList(),
        bodyMeasurements: List<BodyMeasurementEntity> = emptyList(),
        nutritionLogs: List<NutritionLogEntity> = emptyList()
    ): Result<String> {
        val systemPrompt = "Du bist ein erfahrener Personal Trainer, der Zirkeltraining an Kraftmaschinen betreut. " +
                "Dein Klient trainiert regelm\u00e4\u00dfig im Zirkel.\n\n" +
                "Dein Ziel ergibt sich aus dem Ziel des Klienten \u2013 du arbeitest darauf hin, dieses bestm\u00f6glich zu erreichen. " +
                "Dabei gilt als nicht verhandelbare Regel: Slow and steady am Optimum ist besser als \u00dcberlastung und Verletzung.\n\n" +
                "Du erh\u00e4ltst die Trainings der letzten Woche UND die langfristige Trainingshistorie.\n" +
                "Erstelle eine Analyse im Kontext der Gesamtentwicklung:\n" +
                "- Trainingsh\u00e4ufigkeit: Genug Einheiten pro Woche? Wie ist der Trend?\n" +
                "- Langzeitprogression: Welche Gewichte wurden \u00fcber Wochen/Monate gesteigert? Wo stagniert es?\n" +
                "- Muskelgruppenbalance: Werden alle Gruppen gleichm\u00e4\u00dfig belastet?\n" +
                "- K\u00f6rpergewicht: Entwicklung passend zum Ziel?\n" +
                "- Erholung: Genug Pause zwischen Trainings der gleichen Muskelgruppe?\n\n" +
                "Antworte auf Deutsch in 5\u201310 S\u00e4tzen. Sei direkt und konkret. " +
                "Gib 1\u20132 spezifische Empfehlungen mit \u00dcbungsnamen (Code in Klammern). Ordne die Woche in den Gesamttrend ein. " +
                "Konkrete Gewichtsvorgaben bleiben beim Wochen-Coaching \u2013 hier nur einordnen."

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
            val tape = formatBodyMeasurements(bodyMeasurements)
            if (tape.isNotBlank()) {
                appendLine(tape)
                appendLine()
            }
            val nutrition = formatNutrition(nutritionLogs)
            if (nutrition.isNotBlank()) {
                appendLine(nutrition)
                appendLine()
            }
            appendLine(formatProfile(userGoal, latestWeightKg, heightCm))
        }

        return callClaude(withPersona(systemPrompt), userMessage)
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

    private fun formatBodyMeasurements(measurements: List<BodyMeasurementEntity>): String {
        if (measurements.isEmpty()) return ""
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY)
        val dateFmt = java.text.SimpleDateFormat("dd.MM", java.util.Locale.GERMANY)
        val sorted = measurements.sortedBy { fmt.parse(it.measuredAt)?.time ?: 0L }

        fun trajectoryFor(label: String, selector: (BodyMeasurementEntity) -> Double): String? {
            val entries = sorted.mapNotNull { m ->
                val v = selector(m)
                if (v <= 0.0) return@mapNotNull null
                val date = fmt.parse(m.measuredAt) ?: return@mapNotNull null
                dateFmt.format(date) to v
            }
            if (entries.isEmpty()) return null
            if (entries.size == 1) {
                val (d, v) = entries.first()
                return "$label: ${"%.1f".format(v)} cm ($d)"
            }
            val milestones = mutableListOf(entries.first())
            var lastV = entries.first().second
            for (i in 1 until entries.size) {
                if (kotlin.math.abs(entries[i].second - lastV) >= 1.0) {
                    milestones.add(entries[i])
                    lastV = entries[i].second
                }
            }
            if (milestones.last() != entries.last()) milestones.add(entries.last())
            val trajectory = milestones.joinToString(" → ") {
                "${"%.1f".format(it.second)} cm (${it.first})"
            }
            val delta = entries.last().second - entries.first().second
            val trend = when {
                delta > 0 -> "+${"%.1f".format(delta)} cm"
                delta < 0 -> "${"%.1f".format(delta)} cm"
                else -> "stabil"
            }
            return "$label: $trajectory [$trend]"
        }

        return buildString {
            appendLine("=== Umfangsmessungen ===")
            trajectoryFor("Brust", { it.chestCm })?.let { appendLine("- $it") }
            trajectoryFor("Taille", { it.waistCm })?.let { appendLine("- $it") }
            trajectoryFor("Bizeps", { it.bicepsCm })?.let { appendLine("- $it") }
        }.trimEnd()
    }

    private fun formatNutrition(logs: List<NutritionLogEntity>): String {
        if (logs.isEmpty()) return ""
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 86400000
        val thirtyDaysAgo = now - 30L * 86400000
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY)

        fun summary(thresholdMs: Long, label: String): String? {
            val window = logs.mapNotNull { log ->
                val t = try { dateFmt.parse(log.date)?.time } catch (_: Exception) { null }
                if (t != null && t >= thresholdMs) log else null
            }
            if (window.isEmpty()) return null
            val hits = window.count { it.proteinHit }
            val total = window.size
            val pct = (hits.toDouble() / total * 100).toInt()
            return "$label: $hits/$total Tage ($pct%)"
        }

        return buildString {
            appendLine("=== Protein-Trefferquote ===")
            summary(sevenDaysAgo, "Letzte 7 Tage")?.let { appendLine("- $it") }
            summary(thirtyDaysAgo, "Letzte 30 Tage")?.let { appendLine("- $it") }
        }.trimEnd()
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

            append("- [${ex.exerciseCode}] ${ex.exerciseDisplayName} [${ex.exerciseMuscleGroup}]")
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
                // Wenn Soll und tatsaechlich verwendetes Gewicht abweichen, das Ist explizit
                // hervorheben, damit der Coach sich nicht am Soll-Snapshot orientiert.
                val actualMax = logs.maxOf { it.actualWeightKg }
                if (kotlin.math.abs(actualMax - ex.targetWeight) > 0.01) {
                    appendLine("  Tats\u00e4chlich verwendetes Gewicht: $actualMax kg (Soll-Snapshot war ${ex.targetWeight} kg)")
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

    suspend fun getWeeklyCoaching(
        sessions: List<WorkoutSessionHistory>,
        availableExercises: List<ExerciseEntity>,
        userGoal: String,
        latestWeightKg: Double?,
        heightCm: Int = 0,
        allHistories: List<WorkoutSessionHistory> = emptyList(),
        weightLogs: List<WeightLogEntity> = emptyList(),
        bodyMeasurements: List<BodyMeasurementEntity> = emptyList(),
        nutritionLogs: List<NutritionLogEntity> = emptyList(),
        previousCoaching: String? = null
    ): Result<String> {
        val systemPrompt = "Du bist ein erfahrener Personal Trainer und Ziel-Coach f\u00fcr Zirkeltraining an Kraftmaschinen.\n" +
                "Dein Klient trainiert regelm\u00e4\u00dfig im Zirkel und hat dir seine Vision anvertraut.\n\n" +
                "Dein Ziel ergibt sich aus dem Ziel des Klienten \u2013 du arbeitest darauf hin, dieses bestm\u00f6glich zu erreichen. " +
                "Dabei gilt als nicht verhandelbare Regel: **Slow and steady am Optimum ist besser als \u00dcberlastung und Verletzung.** " +
                "Lieber eine Stufe weniger und konstant progressiv als ein zu gro\u00dfer Sprung mit Verletzungsrisiko.\n\n" +
                "Deine Aufgabe ist es, als pers\u00f6nlicher Coach zu fungieren, der:\n" +
                "1. Die Vision/das Ziel des Klienten ernst nimmt und in erreichbare Teilziele \u00fcbersetzt\n" +
                "2. Den Fortschritt bei jedem Coaching anhand konkreter Messwerte bewertet\n" +
                "3. Kontinuit\u00e4t zwischen Coaching-Sitzungen wahrt\n" +
                "4. Verletzungspr\u00e4vention immer \u00fcber schnelle Progression stellt\n\n" +
                "AUSGABEFORMAT (halte dich strikt daran):\n\n" +
                "## Deine Vision\n" +
                "[Wiederhole kurz die Vision des Klienten und ordne sie ein \u2013 was bedeutet sie konkret f\u00fcr das Training?]\n\n" +
                "## Teilziele\n" +
                "[3\u20135 messbare Teilziele, die zur Vision beitragen. Jedes Teilziel mit aktuellem Status:]\n" +
                "- Teilziel 1: [Beschreibung] \u2192 Status: [Bewertung anhand der Daten]\n" +
                "- Teilziel 2: [Beschreibung] \u2192 Status: [Bewertung anhand der Daten]\n" +
                "- ...\n\n" +
                "## Wochenr\u00fcckblick\n" +
                "[Analyse der letzten Woche: Was lief gut? Was kann besser werden? Konsistenz, Progression, Intensit\u00e4t]\n\n" +
                "## Fortschritt seit letztem Coaching\n" +
                "[Nur wenn vorheriges Coaching vorhanden: Was hat sich seit dem letzten Coaching ver\u00e4ndert? Wurden die Empfehlungen umgesetzt?]\n\n" +
                "## N\u00e4chste Schritte\n" +
                "[2\u20133 konkrete, umsetzbare Aktionen f\u00fcr die n\u00e4chste Woche. Nenne spezifische \u00dcbungen und Gewichte.]\n\n" +
                "Wichtige Regeln:\n" +
                "- Antworte immer auf Deutsch\n" +
                "- Sei direkt, ehrlich und motivierend\n" +
                "- Nenne \u00dcbungen immer beim Namen UND mit ihrem Code (z.B. \"Chest Press [B2]\")\n" +
                "- Verwende die tats\u00e4chlichen Zahlen aus den Trainingsdaten\n" +
                "- Wenn 'Tats\u00e4chlich verwendetes Gewicht' angegeben ist, beziehe dich in deinen Empfehlungen und Begr\u00fcndungen ausschlie\u00dflich auf diesen Wert, NICHT auf den Soll-Snapshot\n" +
                "- Beziehe Umfangsmessungen (Brust/Taille/Bizeps) und Protein-Trefferquote in deine Bewertung ein, sofern Daten vorhanden sind\n" +
                "- Wenn kein Ziel gesetzt ist, empfiehl dem Klienten, eines zu setzen, und arbeite trotzdem mit den verf\u00fcgbaren Daten\n" +
                "- Wenn kein vorheriges Coaching vorhanden ist, \u00fcberspringe den Abschnitt \"Fortschritt seit letztem Coaching\"\n" +
                "- Verwende KEINE Markdown-Tabellen. Nutze stattdessen Aufz\u00e4hlungen (Spiegelstriche) f\u00fcr \u00dcbungsvergleiche und Gewichtsdaten.\n\n" +
                "STRUKTURIERTER PLAN (verpflichtend am Ende deiner Antwort):\n" +
                "H\u00e4nge nach dem deutschen Coaching-Text einen XML-Block <plan>...</plan> an, der ein JSON-Objekt enth\u00e4lt. Die App liest diesen Block, um deine Vorgaben in den Trainingsalltag des Klienten zu integrieren. Format:\n\n" +
                "<plan>\n" +
                "{\n" +
                "  \"valid_until\": \"YYYY-MM-DD\" (Datum 7 Tage in der Zukunft),\n" +
                "  \"bottleneck\": {\n" +
                "    \"type\": \"bodyweight\" oder \"protein\" oder \"sessions\" oder \"progression\",\n" +
                "    \"target\": Zahl (Zielwert),\n" +
                "    \"current\": Zahl (aktueller Wert)\n" +
                "  },\n" +
                "  \"weekly\": {\n" +
                "    \"protein_g\": Zahl (g Protein/Tag),\n" +
                "    \"kcal\": Zahl (kcal/Tag),\n" +
                "    \"sessions\": Zahl (Trainingseinheiten/Woche),\n" +
                "    \"bodyweight_kg\": Zahl (Wochen-Ziel-K\u00f6rpergewicht)\n" +
                "  },\n" +
                "  \"exercise_targets\": [\n" +
                "    {\"code\": \"einer der unten gelisteten Codes\", \"action\": \"progress\" oder \"hold\" oder \"deload\", \"weight_kg\": Zielgewicht, \"reason\": \"kurze Begr\u00fcndung zu GENAU dieser \u00dcbung\"}\n" +
                "  ]\n" +
                "}\n" +
                "</plan>\n\n" +
                "REGELN F\u00dcR DEN <plan>-BLOCK (zwingend einzuhalten):\n" +
                "- Der `code` in jedem Eintrag MUSS exakt einer der oben in \"Verf\u00fcgbare Ger\u00e4te\" gelisteten Codes sein. Keine erfundenen oder anderen Codes.\n" +
                "- Die `reason` darf sich AUSSCHLIESSLICH auf die \u00dcbung mit genau diesem Code beziehen. Nenne in der reason nicht den Namen einer anderen \u00dcbung.\n" +
                "- F\u00fchre f\u00fcr jede der oben aufgef\u00fchrten \u00dcbungen genau einen Eintrag in `exercise_targets` auf.\n" +
                "- Bei `action=\"progress\"`: `weight_kg` darf h\u00f6chstens EINE Stufe \u00fcber dem aktuellen Gewicht aus der Stack-Liste liegen \u2013 und niemals mehr als `progressionStepKg` dar\u00fcber, je nachdem was kleiner ist. Keine 10-kg-Spr\u00fcnge, au\u00dfer der Stack des Ger\u00e4ts ist entsprechend grob (z.B. Leg Press mit 9-kg-Spr\u00fcngen).\n" +
                "- Bei `action=\"deload\"`: `weight_kg` mindestens eine Stufe UNTER dem aktuellen Gewicht.\n" +
                "- Bei `action=\"hold\"`: `weight_kg` = aktuelles Gewicht.\n" +
                "- Setze `action=\"hold\"` wenn die letzte Session inkonsistent war (z.B. 10/12 Wdh statt 12/12) oder wenn du unsicher bist. Im Zweifel IMMER hold statt zu gro\u00dfer Sprung.\n" +
                "- Setze `action=\"progress\"` nur, wenn alle S\u00e4tze sauber bei Max-Reps geschafft wurden UND der Sprung innerhalb einer Stufe bleibt.\n" +
                "- Verwende ausschlie\u00dflich kg, keine lb.\n" +
                "- Der bottleneck ist die EINE Zahl, die diese Woche am wichtigsten ist (z.B. K\u00f6rpergewicht-Ziel, wenn der Klient Untergewicht hat)."

        val userMessage = buildString {
            appendLine(formatAvailableExercises(availableExercises))
            appendLine()
            appendLine("=== Trainings der letzten Woche (${sessions.size} Einheiten) ===")
            appendLine()
            sessions.forEachIndexed { index, history ->
                appendLine("--- Training ${index + 1} ---")
                appendLine(formatSessionData(history))
                appendLine()
            }

            appendLine("=== Trainingsmetriken ===")
            appendLine(formatTrainingMetrics(sessions, allHistories))
            appendLine()

            val historiesForSummary = if (allHistories.isNotEmpty()) allHistories else sessions
            if (historiesForSummary.size > sessions.size) {
                appendLine(formatHistoricalSummary(historiesForSummary, weightLogs))
                appendLine()
            }
            val tape = formatBodyMeasurements(bodyMeasurements)
            if (tape.isNotBlank()) {
                appendLine(tape)
                appendLine()
            }
            val nutrition = formatNutrition(nutritionLogs)
            if (nutrition.isNotBlank()) {
                appendLine(nutrition)
                appendLine()
            }
            appendLine(formatProfile(userGoal, latestWeightKg, heightCm))

            if (previousCoaching != null) {
                appendLine()
                appendLine("=== Letztes Coaching ===")
                appendLine(previousCoaching)
            }
        }

        return callClaudeWithThinking(withPersona(systemPrompt), userMessage)
    }

    /**
     * Baut eine Liste aller verf\u00fcgbaren Ger\u00e4te mit Code, Name, Muskelgruppe,
     * aktuellem Gewicht, Standard-Schrittweite und einem Auszug aus dem Stack
     * (\u00b15 Stufen rund um das aktuelle Gewicht), damit Claude im Prompt eine
     * eindeutige Code\u2194Name-Zuordnung und konkrete Schrittgr\u00f6\u00dfen hat.
     */
    private fun formatAvailableExercises(exercises: List<ExerciseEntity>): String = buildString {
        appendLine("=== Verf\u00fcgbare Ger\u00e4te ===")
        appendLine("Codes, Namen, Muskelgruppe, aktuelles Gewicht, Standard-Schritt, zul\u00e4ssige Stack-Stufen rund um den aktuellen Wert.")
        appendLine("Die Codes in deinem <plan>-Block M\u00dcSSEN aus dieser Liste stammen \u2013 keine anderen!\n")
        for (ex in exercises.sortedBy { it.sortOrder }) {
            val stack = formatStackPreview(ex.weightSteps, ex.currentWeight)
            val name = ex.displayName.ifBlank { ex.code }
            val group = ex.muscleGroup.ifBlank { "?" }
            val step = if (ex.progressionStepKg > 0.0) "${trimNumber(ex.progressionStepKg)} ${ex.weightUnit}" else "?"
            appendLine(
                "- ${ex.code} \u00b7 $name \u00b7 $group \u00b7 aktuell ${trimNumber(ex.currentWeight)} ${ex.weightUnit} " +
                    "\u00b7 Schritt $step \u00b7 Stack: $stack"
            )
        }
    }

    private fun formatStackPreview(weightSteps: String, currentWeight: Double): String {
        if (weightSteps.isBlank()) return "(kein Stack hinterlegt)"
        val steps = ProgressionService.parseWeightSteps(weightSteps)
        if (steps.isEmpty()) return "(kein Stack hinterlegt)"
        var anchor = steps.indexOfFirst { it >= currentWeight - 0.01 }
        if (anchor < 0) anchor = steps.size - 1
        val from = (anchor - 5).coerceAtLeast(0)
        val to = (anchor + 5).coerceAtMost(steps.size - 1)
        val window = steps.subList(from, to + 1)
        val prefix = if (from > 0) "\u2026, " else ""
        val suffix = if (to < steps.size - 1) ", \u2026" else ""
        return prefix + window.joinToString(", ") { trimNumber(it) } + suffix
    }

    private fun trimNumber(d: Double): String {
        return if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d).replace(".", ",")
    }

    private fun formatTrainingMetrics(
        recentSessions: List<WorkoutSessionHistory>,
        allHistories: List<WorkoutSessionHistory>
    ): String = buildString {
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY)

        // Consistency rate (sessions per week over last 4 weeks)
        if (allHistories.size >= 2) {
            val fourWeeksAgo = System.currentTimeMillis() - (28L * 86400000)
            val recent4w = allHistories.filter { h ->
                val d = fmt.parse(h.session.completedAt ?: h.session.startedAt)
                d != null && d.time >= fourWeeksAgo
            }
            val weeks = 4.0
            appendLine("Konsistenz (letzte 4 Wochen): ${recent4w.size} Einheiten (\u00d8 ${"%.1f".format(recent4w.size / weeks)}x/Woche)")
        }

        // Per-exercise progression rate (weight change per month)
        val exerciseFirstLast = mutableMapOf<String, Pair<Pair<Long, Double>, Pair<Long, Double>>>()
        for (history in allHistories.sortedBy { it.session.startedAt }) {
            val date = fmt.parse(history.session.completedAt ?: history.session.startedAt) ?: continue
            for (exWithLogs in history.sessionExercises) {
                val name = exWithLogs.sessionExercise.exerciseDisplayName.ifEmpty { exWithLogs.sessionExercise.exerciseCode }
                val maxWeight = exWithLogs.setLogs.maxOfOrNull { it.actualWeightKg } ?: continue
                val existing = exerciseFirstLast[name]
                if (existing == null) {
                    exerciseFirstLast[name] = (date.time to maxWeight) to (date.time to maxWeight)
                } else {
                    exerciseFirstLast[name] = existing.first to (date.time to maxWeight)
                }
            }
        }

        if (exerciseFirstLast.isNotEmpty()) {
            appendLine("Progressionsrate pro \u00dcbung:")
            for ((name, pair) in exerciseFirstLast) {
                val (first, last) = pair
                val daysDiff = ((last.first - first.first) / 86400000.0).coerceAtLeast(1.0)
                val weightDiff = last.second - first.second
                val monthlyRate = weightDiff / daysDiff * 30.0
                if (daysDiff >= 7) {
                    appendLine("- $name: ${"%.1f".format(monthlyRate)} kg/Monat (${"%.1f".format(first.second)} \u2192 ${"%.1f".format(last.second)} kg in ${daysDiff.toInt()} Tagen)")
                }
            }
        }

        // Rep quality: % of sets completed
        if (recentSessions.isNotEmpty()) {
            var totalSets = 0
            var completedSets = 0
            for (session in recentSessions) {
                for (ex in session.sessionExercises) {
                    for (log in ex.setLogs) {
                        totalSets++
                        if (log.completedFlag) completedSets++
                    }
                }
            }
            if (totalSets > 0) {
                val pct = (completedSets.toDouble() / totalSets * 100).toInt()
                appendLine("Satz-Erfolgsquote (letzte Woche): $completedSets/$totalSets ($pct%)")
            }
        }
    }

    /**
     * Leitet wissenschaftlich fundierte Ziel-Arbeitsgewichte (kg, fuer 8-12 Wdh.)
     * je Muskelgruppe ab, kalibriert auf das Profil und die konkret vorhandenen
     * Geraete. Antwort = deutscher Fliesstext + `<targets>`-JSON-Block, geparst
     * von [StrengthTargetParser].
     */
    suspend fun getStrengthTargets(
        goal: String,
        gender: String,
        ageYears: Int?,
        heightCm: Int,
        bodyweightKg: Double?,
        bodyType: String,
        exercises: List<ExerciseEntity>
    ): Result<String> {
        val systemPrompt =
            "Du bist Sportwissenschaftler und erfahrener Personal Trainer. " +
                "Bestimme nach anerkannten Standards der Kraft- und Trainingslehre einen realistischen, " +
                "gesunden Zielzustand fuer einen Klienten, der ausschliesslich an den unten gelisteten " +
                "Kraftmaschinen im Zirkel trainiert.\n\n" +
                "Gib pro Muskelgruppe ein Ziel-Arbeitsgewicht in KILOGRAMM an – das Gewicht, mit dem der " +
                "Klient als Zielzustand 8–12 saubere Wiederholungen auf der wichtigsten Maschine dieser " +
                "Gruppe schafft. Beruecksichtige Geschlecht, Alter, Koerpergroesse, Koerpergewicht und " +
                "Koerpertyp sowie die konkreten Maschinen (eine Maschinen-Kraft laesst sich nicht 1:1 mit " +
                "Langhantel-Standards vergleichen – kalibriere an den aktuellen Gewichten der Geraete). " +
                "Die Ziele sollen ambitioniert aber sicher erreichbar sein (slow and steady am Optimum, keine Ueberlastung).\n\n" +
                "Unterstuetzte Gruppen: CHEST, BACK, LEGS, SHOULDERS, ARMS, ABS. Verwende ausschliesslich kg.\n\n" +
                "Antworte zuerst in 3–6 deutschen Saetzen (Einordnung, Schwerpunkte). " +
                "Haenge danach GENAU EINEN Block in diesem Format an (nur valides JSON, keine Code-Fences):\n" +
                "<targets>\n" +
                "{ \"groups\": [ {\"group\":\"CHEST\",\"target_kg\":70,\"rationale\":\"kurze Begruendung\"}, ... ] }\n" +
                "</targets>"

        val userMessage = buildString {
            appendLine("=== Mein Profil ===")
            if (goal.isNotBlank()) appendLine("Ziel: $goal")
            if (gender.isNotBlank()) appendLine("Geschlecht: $gender")
            if (ageYears != null) appendLine("Alter: $ageYears Jahre")
            if (heightCm > 0) appendLine("Größe: $heightCm cm")
            if (bodyweightKg != null) appendLine("Körpergewicht: ${trimNumber(bodyweightKg)} kg")
            if (bodyType.isNotBlank()) appendLine("Körpertyp: $bodyType")
            appendLine()
            append(formatAvailableExercises(exercises))
        }

        // Hoeheres Token-Budget: Fliesstext + JSON-Block fuer bis zu 6 Gruppen
        // (je mit Begruendung) passt nicht zuverlaessig in 1024 Tokens – sonst
        // wird die Antwort vor </targets> abgeschnitten und nicht parsebar.
        return callClaude(withPersona(systemPrompt), userMessage, maxTokens = 2048)
    }

    /**
     * Schaetzt den Proteingehalt einer frei beschriebenen Mahlzeit
     * (z.B. "4 EL Haferflocken und 1 EL Mandelmus"). Antwort = kurzer
     * deutscher Text + `<protein>`-JSON-Block, geparst von [ProteinEstimateParser].
     */
    suspend fun estimateProteinFromText(description: String): Result<String> {
        val userMessage = "Meine Mahlzeit: $description"
        // Bewusst ohne withPersona(): nuechterne Schaetzung, kein Coach-Ton.
        return callClaude(PROTEIN_SYSTEM_PROMPT, userMessage)
    }

    /**
     * Schaetzt den Proteingehalt einer fotografierten Mahlzeit.
     * @param base64Jpeg JPEG-Bilddaten als Base64 (ohne Zeilenumbrueche).
     * @param hint optionale Zusatzbeschreibung des Users.
     */
    suspend fun estimateProteinFromImage(base64Jpeg: String, hint: String? = null): Result<String> {
        val textPrompt = buildString {
            append("Schätze den Proteingehalt der abgebildeten Mahlzeit.")
            if (!hint.isNullOrBlank()) append(" Zusatzinfo: $hint")
        }
        val content = JSONArray()
            .put(JSONObject().apply {
                put("type", "image")
                put("source", JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/jpeg")
                    put("data", base64Jpeg)
                })
            })
            .put(JSONObject().apply {
                put("type", "text")
                put("text", textPrompt)
            })
        return callClaude(PROTEIN_SYSTEM_PROMPT, content)
    }

    // userContent ist entweder ein String (reiner Text) oder ein JSONArray aus
    // Content-Blocks (z.B. Bild + Text fuer die Foto-Schaetzung) – org.json
    // serialisiert beides korrekt in das "content"-Feld.
    private suspend fun callClaude(
        systemPrompt: String,
        userContent: Any,
        maxTokens: Int = 1024
    ): Result<String> =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            var phase = "connect"
            try {
                val url = URL("https://api.anthropic.com/v1/messages")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("x-api-key", apiKey)
                    connection.setRequestProperty("anthropic-version", "2023-06-01")
                    connection.doOutput = true
                    connection.connectTimeout = 30_000
                    connection.readTimeout = 60_000

                    val body = JSONObject().apply {
                        put("model", sonnetModel)
                        put("max_tokens", maxTokens)
                        put("system", systemPrompt)
                        put("messages", JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", userContent)
                            }
                        ))
                    }

                    phase = "write"
                    connection.outputStream.use { os ->
                        os.write(body.toString().toByteArray(Charsets.UTF_8))
                    }

                    phase = "read"
                    val responseCode = connection.responseCode
                    if (responseCode != 200) {
                        val errorBody = connection.errorStream?.use { it.bufferedReader().readText() } ?: "Unbekannter Fehler"
                        return@withContext Result.failure(Exception("API-Fehler ($responseCode): $errorBody"))
                    }

                    val responseBody = connection.inputStream.use { it.bufferedReader().readText() }
                    val json = JSONObject(responseBody)
                    val content = json.getJSONArray("content")
                    val text = content.getJSONObject(0).getString("text")

                    Result.success(text)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Result.failure(diagnosticException(e, phase, started))
            }
        }

    private suspend fun callClaudeWithThinking(systemPrompt: String, userMessage: String): Result<String> =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            var phase = "connect"
            try {
                val url = URL("https://api.anthropic.com/v1/messages")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("x-api-key", apiKey)
                    connection.setRequestProperty("anthropic-version", "2023-06-01")
                    connection.doOutput = true
                    connection.connectTimeout = 30_000
                    connection.readTimeout = 120_000

                    val body = JSONObject().apply {
                        put("model", opusModel)
                        put("max_tokens", 12000)
                        put("thinking", JSONObject().apply {
                            put("type", "adaptive")
                        })
                        put("output_config", JSONObject().apply {
                            put("effort", "high")
                        })
                        put("system", systemPrompt)
                        put("messages", JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", userMessage)
                            }
                        ))
                    }

                    phase = "write"
                    connection.outputStream.use { os ->
                        os.write(body.toString().toByteArray(Charsets.UTF_8))
                    }

                    phase = "read"
                    val responseCode = connection.responseCode
                    if (responseCode != 200) {
                        val errorBody = connection.errorStream?.use { it.bufferedReader().readText() } ?: "Unbekannter Fehler"
                        return@withContext Result.failure(Exception("API-Fehler ($responseCode): $errorBody"))
                    }

                    val responseBody = connection.inputStream.use { it.bufferedReader().readText() }
                    val json = JSONObject(responseBody)
                    val content = json.getJSONArray("content")

                    // Extended thinking returns multiple blocks: skip "thinking" blocks, find "text" block
                    var resultText = ""
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.getString("type") == "text") {
                            resultText = block.getString("text")
                            break
                        }
                    }
                    if (resultText.isEmpty()) {
                        return@withContext Result.failure(Exception("Keine Textantwort erhalten"))
                    }

                    Result.success(resultText)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Result.failure(diagnosticException(e, phase, started))
            }
        }

    private fun diagnosticException(e: Exception, phase: String, startedMs: Long): Exception {
        val ms = System.currentTimeMillis() - startedMs
        val cls = e.javaClass.simpleName
        val cause = e.cause?.javaClass?.simpleName?.let { " (cause: $it)" } ?: ""
        return Exception("$cls$cause @$phase nach ${ms}ms: ${e.message}", e)
    }

    companion object {
        private val PROTEIN_SYSTEM_PROMPT =
            "Du bist Ernährungswissenschaftler. Schätze den Proteingehalt (in Gramm) der " +
                "beschriebenen bzw. fotografierten Mahlzeit.\n\n" +
                "Regeln:\n" +
                "- Nutze übliche deutsche Portionsgrößen (EL = gehäufter Esslöffel, TL, " +
                "Scheibe, Handvoll, Glas, Becher).\n" +
                "- Sei realistisch; bei Unsicherheit konservativ schätzen und die Annahme nennen.\n" +
                "- Zerlege die Mahlzeit in einzelne Bestandteile mit jeweils eigener Schätzung.\n\n" +
                "Antworte zuerst in 1–2 kurzen deutschen Sätzen (wichtigste Annahmen). " +
                "Hänge danach GENAU EINEN Block in diesem Format an (nur valides JSON, keine Code-Fences):\n" +
                "<protein>\n" +
                "{ \"items\": [ {\"name\":\"Haferflocken 4 EL\",\"protein_g\":5.4} ], " +
                "\"total_protein_g\": 8.1, \"note\": \"kurze Annahme\" }\n" +
                "</protein>"
    }
}
