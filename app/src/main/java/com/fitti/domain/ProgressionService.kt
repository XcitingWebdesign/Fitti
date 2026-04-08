package com.fitti.domain

import com.fitti.data.SetLogEntity

object ProgressionService {

    /**
     * Calculates the next weight after a progression step.
     * If weightSteps is provided, finds the next available weight in the stack.
     * Otherwise rounds to nearest 0.5 to match gym plate/pin logic.
     */
    fun calculateNextWeight(currentWeight: Double, step: Double, weightSteps: String = ""): Double {
        if (weightSteps.isNotBlank()) {
            val steps = parseWeightSteps(weightSteps)
            if (steps.isNotEmpty()) {
                val next = steps.firstOrNull { it > currentWeight + 0.01 }
                if (next != null) return next
                // Already at max of stack, fall through to step-based
            }
        }
        val next = currentWeight + step
        return roundToHalf(next)
    }

    /**
     * Parses comma-separated weight steps string into sorted list of doubles.
     */
    fun parseWeightSteps(weightSteps: String): List<Double> {
        return weightSteps.split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }
            .sorted()
    }

    /**
     * Rounds to nearest 0.5 (half-up).
     */
    fun roundToHalf(value: Double): Double {
        return Math.round(value * 2.0) / 2.0
    }

    /**
     * Double Progression: increase weight only when ALL sets hit targetReps (max).
     */
    fun isEligibleForProgression(
        setLogs: List<SetLogEntity>,
        targetSets: Int,
        targetReps: Int
    ): Boolean {
        if (setLogs.size < targetSets) return false
        return setLogs.all { it.completedFlag && it.actualReps >= targetReps }
    }
}
