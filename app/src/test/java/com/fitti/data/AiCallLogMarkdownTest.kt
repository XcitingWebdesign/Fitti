package com.fitti.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCallLogMarkdownTest {

    private fun log(kind: String, response: String? = "Antwort", error: String? = null) = AiCallLogEntity(
        id = 1,
        createdAt = "11.10.2026 10:00:00",
        kind = kind,
        model = "claude-sonnet",
        systemPrompt = "Du bist Trainer.",
        userMessage = "Mein Training",
        responseText = response,
        errorText = error,
        inputTokens = 100,
        outputTokens = 50,
        durationMs = 1200
    )

    @Test
    fun formatsCallWithPromptAndResponse() {
        val md = AiCallLogMarkdown.format(
            exportedAt = "11.10.2026 12:00",
            profile = listOf("Ziel" to "Muskelaufbau"),
            logs = listOf(log(AiCallLogEntity.KIND_WORKOUT_FEEDBACK)),
            legacyAnalyses = emptyList()
        )
        assertTrue(md.contains("- **Ziel:** Muskelaufbau"))
        assertTrue(md.contains("### 1. Trainings-Feedback – 11.10.2026 10:00:00"))
        assertTrue(md.contains("Du bist Trainer."))
        assertTrue(md.contains("Mein Training"))
        assertTrue(md.contains("#### Antwort"))
        assertFalse(md.contains("#### Fehler"))
        assertFalse(md.contains("Ältere Wochen-Coachings"))
    }

    @Test
    fun formatsErrorAndLegacyAnalyses() {
        val md = AiCallLogMarkdown.format(
            exportedAt = "x",
            profile = emptyList(),
            logs = listOf(log(AiCallLogEntity.KIND_WEEKLY_COACHING, response = null, error = "Timeout")),
            legacyAnalyses = listOf(AiAnalysisEntity(id = 3, analysisText = "Alter Plan", createdAt = "01.10.2026"))
        )
        assertTrue(md.contains("#### Fehler"))
        assertTrue(md.contains("Timeout"))
        assertFalse(md.contains("#### Antwort"))
        assertTrue(md.contains("Alter Plan"))
    }

    @Test
    fun usesLongerFenceWhenContentHasBackticks() {
        val md = AiCallLogMarkdown.format(
            exportedAt = "x",
            profile = emptyList(),
            logs = listOf(log(AiCallLogEntity.KIND_PROTEIN_TEXT, response = "```json\n{}\n```")),
            legacyAnalyses = emptyList()
        )
        assertTrue(md.contains("````\n```json"))
    }

    @Test
    fun describeUserContentReplacesImages() {
        val content = JSONArray()
            .put(JSONObject().put("type", "image").put("source", JSONObject().put("data", "AAAA")))
            .put(JSONObject().put("type", "text").put("text", "Schätze das"))
        assertEquals("[Bild]\nSchätze das", ClaudeApiService.describeUserContent(content))
        assertEquals("nur Text", ClaudeApiService.describeUserContent("nur Text"))
    }
}
