package com.fitti.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionServiceTest {

    @Test
    fun calculateNextWeight_standardStep() {
        val next = ProgressionService.calculateNextWeight(41.0, 2.5)
        assertEquals(43.5, next, 0.001)
    }

    @Test
    fun calculateNextWeight_roundsToHalfKg() {
        // 41.0 + 2.3 = 43.3 -> should round to 43.5
        val next = ProgressionService.calculateNextWeight(41.0, 2.3)
        assertEquals(43.5, next, 0.001)
    }

    @Test
    fun calculateNextWeight_exactHalf() {
        // 40.0 + 2.5 = 42.5 -> stays 42.5
        val next = ProgressionService.calculateNextWeight(40.0, 2.5)
        assertEquals(42.5, next, 0.001)
    }

    @Test
    fun calculateNextWeight_exactWhole() {
        // 40.0 + 2.0 = 42.0 -> stays 42.0
        val next = ProgressionService.calculateNextWeight(40.0, 2.0)
        assertEquals(42.0, next, 0.001)
    }

    @Test
    fun calculateNextWeight_lbValues() {
        // 60.0 + 5.0 = 65.0 -> stays 65.0
        val next = ProgressionService.calculateNextWeight(60.0, 5.0)
        assertEquals(65.0, next, 0.001)
    }

    @Test
    fun roundToHalf_variousValues() {
        assertEquals(0.5, ProgressionService.roundToHalf(0.3), 0.001)
        assertEquals(0.5, ProgressionService.roundToHalf(0.5), 0.001)
        assertEquals(1.0, ProgressionService.roundToHalf(0.8), 0.001)
        assertEquals(1.0, ProgressionService.roundToHalf(1.0), 0.001)
        assertEquals(1.0, ProgressionService.roundToHalf(1.2), 0.001)
        assertEquals(1.5, ProgressionService.roundToHalf(1.3), 0.001)
    }
}
