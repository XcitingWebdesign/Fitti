package com.fitti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProteinGoalTest {

    @Test
    fun perKgGoalUsesBodyWeight() {
        assertEquals(135, ProteinGoal.dailyGrams(goalGrams = 0, goalPerKg = 1.5, bodyWeightKg = 90.0))
    }

    @Test
    fun perKgGoalWithoutWeightFallsBackToGrams() {
        assertEquals(120, ProteinGoal.dailyGrams(goalGrams = 120, goalPerKg = 1.5, bodyWeightKg = null))
    }

    @Test
    fun gramGoalUsedWhenNoPerKgGoal() {
        assertEquals(140, ProteinGoal.dailyGrams(goalGrams = 140, goalPerKg = 0.0, bodyWeightKg = 80.0))
    }

    @Test
    fun parsesCommaAndDot() {
        assertEquals(1.5, ProteinGoal.parseDecimal("1,5")!!, 0.0001)
        assertEquals(1.5, ProteinGoal.parseDecimal(" 1.5 ")!!, 0.0001)
        assertNull(ProteinGoal.parseDecimal("abc"))
    }
}
