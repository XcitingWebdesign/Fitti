package com.fitti.data

import org.json.JSONObject

/** Ein Bestandteil der Mahlzeit mit geschaetztem Proteingehalt. */
data class ProteinEstimateItem(
    val name: String,
    val proteinG: Double
)

/** Geparste Protein-Schaetzung aus Claudes Antwort. */
data class ProteinEstimate(
    val items: List<ProteinEstimateItem>,
    val totalProteinG: Double,
    val note: String
)

/**
 * Extrahiert die Protein-Schaetzung aus Claudes Antwort.
 *
 * Claude liefert 1-2 deutsche Saetze gefolgt von einem `<protein>...</protein>`
 * Block mit JSON. Toleranz-Verhalten gespiegelt an [StrengthTargetParser]:
 * Markdown-Code-Fences, fehlendes schliessendes Tag (abgeschnittene Antwort)
 * und nacktes JSON ohne Tags werden akzeptiert.
 *
 * Erwartetes JSON:
 * ```
 * { "items": [ {"name":"Haferflocken 4 EL","protein_g":5.4} ],
 *   "total_protein_g": 8.1, "note": "kurze Annahme" }
 * ```
 */
object ProteinEstimateParser {

    private val proteinRegex = Regex("<protein>([\\s\\S]*?)</protein>", RegexOption.IGNORE_CASE)
    private val openTagRegex = Regex("<protein>", RegexOption.IGNORE_CASE)
    private val codeFenceRegex = Regex("```[a-zA-Z]*")

    /** Antworttext ohne den `<protein>`-Block (fuer die Anzeige der Annahmen). */
    fun stripProteinBlock(response: String): String =
        proteinRegex.replace(response, "").trim()

    /**
     * Parst die Schaetzung. Gibt null zurueck, wenn kein verwertbares JSON
     * gefunden wird. Fehlt `total_protein_g`, wird die Summe der Items genutzt.
     */
    fun parse(response: String): ProteinEstimate? {
        val jsonText = extractJsonObject(response) ?: return null
        return try {
            val json = JSONObject(jsonText)
            val items = mutableListOf<ProteinEstimateItem>()
            val arr = json.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    val proteinG = obj.optDouble("protein_g", Double.NaN)
                    if (name.isEmpty() || proteinG.isNaN() || proteinG < 0.0) continue
                    items.add(ProteinEstimateItem(name = name, proteinG = proteinG))
                }
            }
            val itemSum = items.sumOf { it.proteinG }
            val total = json.optDouble("total_protein_g", Double.NaN)
                .takeIf { !it.isNaN() && it >= 0.0 }
                ?: itemSum.takeIf { items.isNotEmpty() }
                ?: return null
            ProteinEstimate(
                items = items,
                totalProteinG = total,
                note = json.optString("note", "").trim()
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Findet den JSON-Body in Claudes Antwort. Probiert in dieser Reihenfolge:
     * 1. vollstaendiger `<protein>...</protein>`-Block,
     * 2. offenes `<protein>` ohne Abschluss (abgeschnittene Antwort),
     * 3. nacktes JSON irgendwo im Text.
     */
    private fun extractJsonObject(response: String): String? {
        val full = proteinRegex.find(response)?.groupValues?.get(1)
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
        return text.substring(start)
    }
}
