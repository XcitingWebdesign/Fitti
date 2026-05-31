package com.fitti.data

import com.fitti.domain.StrengthTargets
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extrahiert die Ziel-Kraftwerte aus Claudes Antwort.
 *
 * Claude liefert deutschen Fliesstext gefolgt von einem `<targets>...</targets>`
 * Block mit JSON. Aufbau gespiegelt an [CoachingPlanParser].
 *
 * Erwartetes JSON:
 * ```
 * { "groups": [ {"group":"CHEST","target_kg":70,"rationale":"..."}, ... ] }
 * ```
 */
object StrengthTargetParser {

    private val targetsRegex = Regex("<targets>([\\s\\S]*?)</targets>", RegexOption.IGNORE_CASE)

    /** Antworttext ohne den `<targets>`-Block. */
    fun stripTargetsBlock(response: String): String =
        targetsRegex.replace(response, "").trim()

    /**
     * Extrahiert und parst den `<targets>`-Block in ein [StrengthTargets].
     * Gibt null zurueck, wenn kein Block/JSON gefunden oder ungueltig ist.
     *
     * @param inputs kurze Zusammenfassung der genutzten Profil-Inputs (fuer QA).
     */
    fun parse(response: String, inputs: String): StrengthTargets? {
        val match = targetsRegex.find(response) ?: return null
        val jsonText = match.groupValues[1].trim()
        return try {
            val json = JSONObject(jsonText)
            val arr = json.optJSONArray("groups") ?: return null
            val byGroup = mutableMapOf<String, Double>()
            val rationale = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val group = obj.optString("group").trim().uppercase()
                if (group !in StrengthTargets.GROUPS) continue
                val targetKg = obj.optDouble("target_kg", 0.0)
                if (targetKg <= 0.0) continue
                byGroup[group] = targetKg
                rationale[group] = obj.optString("rationale", "")
            }
            if (byGroup.isEmpty()) return null
            StrengthTargets(
                generatedAt = nowDateTime(),
                inputs = inputs,
                byGroup = byGroup,
                rationaleByGroup = rationale
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun nowDateTime(): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
}
