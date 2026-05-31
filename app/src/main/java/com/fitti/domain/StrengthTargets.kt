package com.fitti.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Von Claude abgeleitete Ziel-Arbeitsgewichte (kg, fuer 8-12 Wdh.) pro
 * Muskelgruppe. Wird als JSON in den Einstellungen zwischengespeichert, damit
 * ein menschlicher Personal Trainer die Werte pruefen/editieren kann.
 *
 * Das Ziel-Radar zeigt aktuelle Kraft ÷ Zielkraft pro Muskelgruppe.
 */
data class StrengthTargets(
    val generatedAt: String,
    val inputs: String,
    val byGroup: Map<String, Double>,
    val rationaleByGroup: Map<String, String>
) {
    fun isEmpty(): Boolean = byGroup.isEmpty()

    fun toJson(): String {
        val groups = JSONArray()
        for ((group, targetKg) in byGroup) {
            groups.put(JSONObject().apply {
                put("group", group)
                put("target_kg", targetKg)
                put("rationale", rationaleByGroup[group] ?: "")
            })
        }
        return JSONObject().apply {
            put("generatedAt", generatedAt)
            put("inputs", inputs)
            put("groups", groups)
        }.toString()
    }

    companion object {
        /** Reihenfolge der Radar-Achsen / unterstuetzte Gruppen. */
        val GROUPS = listOf("CHEST", "BACK", "LEGS", "SHOULDERS", "ARMS", "ABS")

        /** Liest den persistierten Cache. Gibt null bei leer/kaputt zurueck. */
        fun fromJson(raw: String?): StrengthTargets? {
            if (raw.isNullOrBlank()) return null
            return try {
                val json = JSONObject(raw)
                val byGroup = mutableMapOf<String, Double>()
                val rationale = mutableMapOf<String, String>()
                val arr = json.optJSONArray("groups") ?: return null
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val group = obj.optString("group").trim().uppercase()
                    if (group !in GROUPS) continue
                    val targetKg = obj.optDouble("target_kg", 0.0)
                    if (targetKg <= 0.0) continue
                    byGroup[group] = targetKg
                    rationale[group] = obj.optString("rationale", "")
                }
                if (byGroup.isEmpty()) return null
                StrengthTargets(
                    generatedAt = json.optString("generatedAt", ""),
                    inputs = json.optString("inputs", ""),
                    byGroup = byGroup,
                    rationaleByGroup = rationale
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
