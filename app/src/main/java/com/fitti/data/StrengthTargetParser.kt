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
    private val openTagRegex = Regex("<targets>", RegexOption.IGNORE_CASE)
    private val codeFenceRegex = Regex("```[a-zA-Z]*")

    /** Antworttext ohne den `<targets>`-Block. */
    fun stripTargetsBlock(response: String): String =
        targetsRegex.replace(response, "").trim()

    /**
     * Extrahiert und parst die Ziel-Kraftwerte in ein [StrengthTargets].
     * Gibt null zurueck, wenn kein verwertbares JSON gefunden oder ungueltig ist.
     *
     * Tolerant gegenueber typischen Modell-Abweichungen: Markdown-Code-Fences,
     * fehlendem schliessenden `</targets>` (abgeschnittene Antwort) und einem
     * nackten JSON-Objekt ganz ohne Tags.
     *
     * @param inputs kurze Zusammenfassung der genutzten Profil-Inputs (fuer QA).
     */
    fun parse(response: String, inputs: String): StrengthTargets? {
        val jsonText = extractJsonObject(response) ?: return null
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

    /**
     * Findet den JSON-Body in Claudes Antwort. Probiert in dieser Reihenfolge:
     * 1. vollstaendiger `<targets>...</targets>`-Block,
     * 2. offenes `<targets>` ohne Abschluss (abgeschnittene Antwort),
     * 3. nacktes JSON irgendwo im Text.
     * Markdown-Code-Fences werden in allen Faellen entfernt. Aus dem Kandidaten
     * wird per Klammer-Matching ein balanciertes `{...}`-Objekt herausgeschnitten.
     */
    private fun extractJsonObject(response: String): String? {
        val full = targetsRegex.find(response)?.groupValues?.get(1)
        val candidate = full
            ?: openTagRegex.find(response)?.let { response.substring(it.range.last + 1) }
            ?: response
        val cleaned = candidate.replace(codeFenceRegex, "")
        return braceMatchedObject(cleaned)
    }

    /** Schneidet das erste balancierte `{...}` heraus (toleriert fehlende `}`). */
    private fun braceMatchedObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        // Abgeschnitten: kein schliessendes '}'. Rest zurueckgeben – JSONObject
        // wirft dann ggf., der Aufrufer faengt das ab.
        return text.substring(start)
    }

    private fun nowDateTime(): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
}
