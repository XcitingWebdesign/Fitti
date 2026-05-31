package com.fitti.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightConverterTest {

    @Test
    fun kg_passesThrough() {
        assertEquals(50.0, WeightConverter.toKg(50.0, "kg"), 0.0001)
    }

    @Test
    fun lb_convertsToKg() {
        // 100 lb = 45.3592 kg
        assertEquals(45.3592, WeightConverter.toKg(100.0, "lb"), 0.001)
    }

    @Test
    fun unit_isCaseInsensitive() {
        assertEquals(WeightConverter.toKg(20.0, "lb"), WeightConverter.toKg(20.0, "LB"), 0.0001)
    }

    @Test
    fun unknownUnit_treatedAsKg() {
        assertEquals(30.0, WeightConverter.toKg(30.0, ""), 0.0001)
    }
}
