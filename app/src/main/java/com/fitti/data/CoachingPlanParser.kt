package com.fitti.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extracts a structured plan JSON block from Claude's coaching response.
 *
 * Claude returns a German prose response followed by a `<plan>...</plan>` tag
 * containing JSON. This parser extracts that JSON and converts it into
 * persistable entities.
 */
object CoachingPlanParser {

    private val planRegex = Regex("<plan>([\\s\\S]*?)</plan>", RegexOption.IGNORE_CASE)

    /** Returns prose without the `<plan>...</plan>` block. */
    fun stripPlanBlock(response: String): String =
        planRegex.replace(response, "").trim()

    /**
     * Extract the JSON inside `<plan>...</plan>`, parse it, and convert into
     * a (plan, targets) pair ready for persistence. Returns null if extraction
     * or parsing fails — caller should fall back to prose-only display.
     *
     * Wenn [validExerciseCodes] gesetzt ist, werden Targets mit unbekannten
     * Codes verworfen (verhindert verwaiste Einträge bei Claude-Halluzinationen
     * oder veralteten Code-Listen).
     */
    fun parse(
        response: String,
        validExerciseCodes: Set<String>? = null
    ): Pair<CoachingPlanEntity, List<CoachingPlanExerciseTargetEntity>>? {
        val match = planRegex.find(response) ?: return null
        val jsonText = match.groupValues[1].trim()
        return try {
            val json = JSONObject(jsonText)
            val now = nowIso()
            val validUntil = json.optString("valid_until").ifBlank { defaultValidUntil() }

            val bottleneck = json.optJSONObject("bottleneck")
            val bottleneckType = bottleneck?.optString("type", "") ?: ""
            val bottleneckTarget = bottleneck?.optDouble("target", 0.0) ?: 0.0
            val bottleneckCurrent = bottleneck?.optDouble("current", 0.0) ?: 0.0

            val weekly = json.optJSONObject("weekly")
            val weeklyProteinG = weekly?.optInt("protein_g", 0) ?: 0
            val weeklyKcal = weekly?.optInt("kcal", 0) ?: 0
            val weeklySessions = weekly?.optInt("sessions", 0) ?: 0
            val weeklyBodyweightKg = weekly?.optDouble("bodyweight_kg", 0.0) ?: 0.0

            val plan = CoachingPlanEntity(
                createdAt = now,
                validUntil = validUntil,
                rawJson = jsonText,
                bottleneckType = bottleneckType,
                bottleneckTarget = bottleneckTarget,
                bottleneckCurrent = bottleneckCurrent,
                weeklyProteinG = weeklyProteinG,
                weeklyKcal = weeklyKcal,
                weeklySessions = weeklySessions,
                weeklyBodyweightKg = weeklyBodyweightKg
            )

            val targets = mutableListOf<CoachingPlanExerciseTargetEntity>()
            val targetsArr = json.optJSONArray("exercise_targets")
            if (targetsArr != null) {
                for (i in 0 until targetsArr.length()) {
                    val obj = targetsArr.getJSONObject(i)
                    val code = obj.optString("code", "").trim()
                    if (code.isEmpty()) continue
                    if (validExerciseCodes != null && code !in validExerciseCodes) continue
                    targets += CoachingPlanExerciseTargetEntity(
                        planId = 0L,
                        exerciseCode = code,
                        action = obj.optString("action", "progress"),
                        targetWeightKg = obj.optDouble("weight_kg", 0.0),
                        reasonText = obj.optString("reason", "")
                    )
                }
            }

            plan to targets
        } catch (_: Exception) {
            null
        }
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())

    private fun defaultValidUntil(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 7)
        return SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(cal.time)
    }
}
