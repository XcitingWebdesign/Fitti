package com.fitti.domain

import com.fitti.data.SetLogEntity

object ProgressionService {

    /**
     * Calculates the next weight after a progression step.
     * Rounds to nearest 0.5 to match gym plate/pin logic.
     */
    fun calculateNextWeight(currentWeight: Double, step: Double): Double {
        val next = currentWeight + step
        return roundToHalf(next)
    }

    /**
     * Rounds to nearest 0.5 (half-up).
     * Examples: 43.3 -> 43.5, 43.7 -> 43.5, 44.0 -> 44.0, 43.25 -> 43.5
     */
    fun roundToHalf(value: Double): Double {
        return Math.round(value * 2.0) / 2.0
    }

    /**
     * Double Progression: increase weight only when ALL sets hit targetReps (max).
     * E.g. with range 8-12: only progress when all sets have 12 reps.
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
