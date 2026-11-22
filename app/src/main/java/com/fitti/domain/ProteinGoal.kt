package com.fitti.domain

import kotlin.math.roundToInt

object ProteinGoal {

    /**
     * Manuelles Tagesziel in Gramm. Ist ein Ziel pro kg Koerpergewicht gesetzt
     * und ein Gewicht bekannt, wird es daraus berechnet; sonst gilt das
     * absolute Gramm-Ziel. 0 = kein Ziel.
     */
    fun dailyGrams(goalGrams: Int, goalPerKg: Double, bodyWeightKg: Double?): Int {
        if (goalPerKg > 0 && bodyWeightKg != null && bodyWeightKg > 0) {
            return (goalPerKg * bodyWeightKg).roundToInt()
        }
        return goalGrams
    }

    /** Parst "1,5" und "1.5" gleichermassen. */
    fun parseDecimal(input: String): Double? =
        input.trim().replace(",", ".").toDoubleOrNull()
}
