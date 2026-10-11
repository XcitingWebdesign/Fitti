package com.fitti.data

/**
 * Baut einen Markdown-Export aller KI-Calls, gedacht zum Einfuegen in einen
 * Claude-Chat, um die System-Prompts anhand echter Konversationen zu verbessern.
 * Der API-Schluessel wird bewusst nie uebergeben.
 */
object AiCallLogMarkdown {

    fun format(
        exportedAt: String,
        profile: List<Pair<String, String>>,
        logs: List<AiCallLogEntity>,
        legacyAnalyses: List<AiAnalysisEntity>
    ): String = buildString {
        appendLine("# Fitti KI-Verlauf")
        appendLine()
        appendLine("Exportiert: $exportedAt")
        appendLine()

        appendLine("## Profil & Einstellungen")
        appendLine()
        for ((label, value) in profile) {
            if (value.contains('\n')) {
                appendLine("- **$label:**")
                appendLine()
                appendQuoted(value)
            } else {
                appendLine("- **$label:** ${value.ifBlank { "–" }}")
            }
        }
        appendLine()

        appendLine("## KI-Calls (${logs.size})")
        appendLine()
        if (logs.isEmpty()) {
            appendLine("Noch keine Calls protokolliert.")
            appendLine()
        }
        for ((index, log) in logs.withIndex()) {
            appendLine("### ${index + 1}. ${kindLabel(log.kind)} – ${log.createdAt}")
            appendLine()
            appendLine("Modell: `${log.model}` · Tokens: ${log.inputTokens} in / ${log.outputTokens} out · Dauer: ${log.durationMs} ms")
            appendLine()
            appendLine("#### System-Prompt")
            appendFenced(log.systemPrompt)
            appendLine("#### User-Nachricht")
            appendFenced(log.userMessage)
            if (log.responseText != null) {
                appendLine("#### Antwort")
                appendFenced(log.responseText)
            }
            if (log.errorText != null) {
                appendLine("#### Fehler")
                appendFenced(log.errorText)
            }
        }

        if (legacyAnalyses.isNotEmpty()) {
            appendLine("## Ältere Wochen-Coachings (ohne Prompt, vor Einführung des Logs)")
            appendLine()
            for (analysis in legacyAnalyses.sortedBy { it.id }) {
                appendLine("### ${analysis.createdAt}")
                appendFenced(analysis.analysisText)
            }
        }
    }

    fun kindLabel(kind: String): String = when (kind) {
        AiCallLogEntity.KIND_WORKOUT_FEEDBACK -> "Trainings-Feedback"
        AiCallLogEntity.KIND_WEEKLY_ANALYSIS -> "Wochenanalyse"
        AiCallLogEntity.KIND_WEEKLY_COACHING -> "Wochen-Coaching"
        AiCallLogEntity.KIND_STRENGTH_TARGETS -> "Kraft-Zielwerte"
        AiCallLogEntity.KIND_PROTEIN_TEXT -> "Protein-Schätzung (Text)"
        AiCallLogEntity.KIND_PROTEIN_IMAGE -> "Protein-Schätzung (Foto)"
        else -> kind
    }

    // Fence laenger als jede Backtick-Folge im Inhalt, damit Markdown in
    // Prompts/Antworten den Block nicht vorzeitig schliesst.
    private fun StringBuilder.appendFenced(text: String) {
        val longestRun = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longestRun + 1))
        appendLine(fence)
        appendLine(text.trimEnd())
        appendLine(fence)
        appendLine()
    }

    private fun StringBuilder.appendQuoted(text: String) {
        text.trimEnd().lines().forEach { appendLine("  > $it") }
        appendLine()
    }
}
