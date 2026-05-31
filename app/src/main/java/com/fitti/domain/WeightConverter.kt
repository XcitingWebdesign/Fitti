package com.fitti.domain

/**
 * Converts machine weights to kilograms. Geraete speichern ihr Gewicht in der
 * jeweils eingestellten Einheit (kg oder lb); fuer Vergleiche über Gruppen
 * hinweg muss alles in kg vorliegen.
 */
object WeightConverter {
    private const val LB_TO_KG = 0.453592

    fun toKg(value: Double, unit: String): Double =
        if (unit.equals("lb", ignoreCase = true)) value * LB_TO_KG else value
}
