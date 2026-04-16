package com.fitti.domain

import com.fitti.data.SetLogEntity
import com.fitti.data.WorkoutSessionHistory

/**
 * Estimates 1RM (one-rep max) per muscle group from completed set logs.
 * Uses the Epley formula: 1RM = weight * (1 + reps/30)
 *
 * Output is normalized so the strongest muscle group = 1.0. This produces
 * relative balance across groups, not absolute strength.
 */
object StrengthEstimator {

    /** Epley 1RM estimate. Returns 0.0 for invalid inputs. */
    fun estimate1RM(weightKg: Double, reps: Int): Double {
        if (weightKg <= 0.0 || reps <= 0) return 0.0
        return weightKg * (1.0 + reps / 30.0)
    }

    /**
     * Compute the max estimated 1RM per muscle group from completed sets.
     * Only considers sets where [SetLogEntity.completedFlag] is true.
     */
    fun groupMax(histories: List<WorkoutSessionHistory>): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        for (history in histories) {
            for (se in history.sessionExercises) {
                val group = se.sessionExercise.exerciseMuscleGroup
                if (group.isBlank()) continue
                for (log in se.setLogs) {
                    if (!log.completedFlag) continue
                    val oneRm = estimate1RM(log.actualWeightKg, log.actualReps)
                    val current = result[group] ?: 0.0
                    if (oneRm > current) result[group] = oneRm
                }
            }
        }
        return result
    }

    /**
     * Normalize raw 1RM values so the largest group = 1.0.
     * Returns an empty map when input is empty or max is 0.
     */
    fun normalize(raw: Map<String, Double>): Map<String, Float> {
        val peak = raw.values.maxOrNull() ?: return emptyMap()
        if (peak <= 0.0) return emptyMap()
        return raw.mapValues { (_, v) -> (v / peak).toFloat() }
    }
}
