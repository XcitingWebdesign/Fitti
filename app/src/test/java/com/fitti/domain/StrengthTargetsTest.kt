package com.fitti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthTargetsTest {

    @Test
    fun jsonRoundTrip_preservesValues() {
        val original = StrengthTargets(
            generatedAt = "31.05.2026 12:00",
            inputs = "Männlich, 45 J, 183 cm",
            byGroup = mapOf("CHEST" to 70.0, "LEGS" to 140.0),
            rationaleByGroup = mapOf("CHEST" to "Brust-Begründung", "LEGS" to "Bein-Begründung")
        )
        val restored = StrengthTargets.fromJson(original.toJson())
        assertNotNull(restored)
        assertEquals(70.0, restored!!.byGroup["CHEST"]!!, 0.0001)
        assertEquals(140.0, restored.byGroup["LEGS"]!!, 0.0001)
        assertEquals("Brust-Begründung", restored.rationaleByGroup["CHEST"])
        assertEquals("31.05.2026 12:00", restored.generatedAt)
    }

    @Test
    fun fromJson_blankOrGarbage_returnsNull() {
        assertNull(StrengthTargets.fromJson(""))
        assertNull(StrengthTargets.fromJson(null))
        assertNull(StrengthTargets.fromJson("nicht json"))
        assertNull(StrengthTargets.fromJson("{}"))
    }

    @Test
    fun fromJson_ignoresUnknownGroupsAndNonPositiveTargets() {
        val json = """
            { "groups": [
              {"group":"CHEST","target_kg":70},
              {"group":"UNKNOWN","target_kg":50},
              {"group":"LEGS","target_kg":0}
            ] }
        """.trimIndent()
        val targets = StrengthTargets.fromJson(json)
        assertNotNull(targets)
        assertEquals(setOf("CHEST"), targets!!.byGroup.keys)
    }

    @Test
    fun isEmpty_reflectsByGroup() {
        assertTrue(
            StrengthTargets("", "", emptyMap(), emptyMap()).isEmpty()
        )
    }
}
