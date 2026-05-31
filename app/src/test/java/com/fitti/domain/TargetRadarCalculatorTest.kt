package com.fitti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetRadarCalculatorTest {

    private fun ex(
        code: String,
        group: String,
        weight: Double,
        unit: String = "kg"
    ) = Exercise(
        id = 0,
        code = code,
        brand = "",
        displayName = code,
        muscleGroup = group,
        currentWeight = weight,
        weightUnit = unit,
        recordedOn = ""
    )

    @Test
    fun currentStrength_takesMaxPerGroup() {
        val exercises = listOf(
            ex("LEG1", "LEGS", 70.0),
            ex("LEG2", "LEGS", 40.0),
            ex("CHEST1", "CHEST", 50.0)
        )
        val current = TargetRadarCalculator.currentStrengthByGroup(exercises)
        assertEquals(70.0, current["LEGS"]!!, 0.0001)
        assertEquals(50.0, current["CHEST"]!!, 0.0001)
    }

    @Test
    fun currentStrength_convertsLbToKg() {
        val current = TargetRadarCalculator.currentStrengthByGroup(
            listOf(ex("CP", "CHEST", 100.0, unit = "lb"))
        )
        assertEquals(45.3592, current["CHEST"]!!, 0.001)
    }

    @Test
    fun currentStrength_ignoresBlankGroup() {
        val current = TargetRadarCalculator.currentStrengthByGroup(
            listOf(ex("AB", "", 30.0))
        )
        assertTrue(current.isEmpty())
    }

    @Test
    fun progress_isRatioCappedAtOne() {
        val exercises = listOf(
            ex("CHEST1", "CHEST", 35.0),   // 50% von 70
            ex("LEGS1", "LEGS", 200.0)     // > Ziel -> 1.0
        )
        val targets = StrengthTargets(
            generatedAt = "",
            inputs = "",
            byGroup = mapOf("CHEST" to 70.0, "LEGS" to 140.0),
            rationaleByGroup = emptyMap()
        )
        val progress = TargetRadarCalculator.progressByGroup(exercises, targets)
        assertEquals(0.5f, progress["CHEST"]!!, 0.001f)
        assertEquals(1.0f, progress["LEGS"]!!, 0.001f)
    }

    @Test
    fun progress_groupWithoutCurrent_isZero() {
        val targets = StrengthTargets(
            generatedAt = "",
            inputs = "",
            byGroup = mapOf("ABS" to 40.0),
            rationaleByGroup = emptyMap()
        )
        val progress = TargetRadarCalculator.progressByGroup(emptyList(), targets)
        assertEquals(0.0f, progress["ABS"]!!, 0.001f)
    }

    @Test
    fun progress_onlyIncludesGroupsWithTarget() {
        val exercises = listOf(ex("CHEST1", "CHEST", 50.0))
        val targets = StrengthTargets(
            generatedAt = "",
            inputs = "",
            byGroup = mapOf("LEGS" to 140.0),
            rationaleByGroup = emptyMap()
        )
        val progress = TargetRadarCalculator.progressByGroup(exercises, targets)
        assertFalse(progress.containsKey("CHEST"))
        assertTrue(progress.containsKey("LEGS"))
    }
}
