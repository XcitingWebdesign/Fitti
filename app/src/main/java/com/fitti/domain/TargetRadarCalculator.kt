package com.fitti.domain

/**
 * Berechnet den Ist-Stand pro Muskelgruppe aus den aktuellen Geraete-Gewichten
 * und stellt ihn dem von Claude abgeleiteten Zielzustand gegenueber.
 *
 * Anders als das alte Balance-Radar (Volumen, auf den Peak normalisiert) bewegt
 * sich dieses Radar mit jedem Kraftzuwachs nach aussen: Achse = aktuelle Kraft
 * ÷ Zielkraft, gekappt bei 100 %.
 *
 * Quelle ist die aktuelle Geraete-Liste (live, mit Einheit und korrigierbarer
 * Muskelgruppe) — nicht die eingefrorenen Session-Snapshots.
 */
object TargetRadarCalculator {

    /**
     * Hoechstes aktuelles Arbeitsgewicht (in kg) je Muskelgruppe. Geraete ohne
     * Muskelgruppe werden ignoriert. Mehrere Geraete einer Gruppe → das schwerste
     * zaehlt (repraesentiert die Kraft der Gruppe).
     */
    fun currentStrengthByGroup(exercises: List<Exercise>): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        for (ex in exercises) {
            val group = ex.muscleGroup.trim().uppercase()
            if (group.isBlank()) continue
            val kg = WeightConverter.toKg(ex.currentWeight, ex.weightUnit)
            if (kg <= 0.0) continue
            if (kg > (result[group] ?: 0.0)) result[group] = kg
        }
        return result
    }

    /**
     * Fortschritt pro Gruppe als 0..1-Wert (aktuell ÷ Ziel, gekappt bei 1.0).
     * Nur Gruppen mit positivem Zielwert erscheinen.
     */
    fun progressByGroup(
        exercises: List<Exercise>,
        targets: StrengthTargets
    ): Map<String, Float> {
        val current = currentStrengthByGroup(exercises)
        val result = mutableMapOf<String, Float>()
        for ((group, targetKg) in targets.byGroup) {
            if (targetKg <= 0.0) continue
            val cur = current[group] ?: 0.0
            result[group] = (cur / targetKg).coerceIn(0.0, 1.0).toFloat()
        }
        return result
    }
}
