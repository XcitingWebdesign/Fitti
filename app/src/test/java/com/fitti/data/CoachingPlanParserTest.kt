package com.fitti.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingPlanParserTest {

    private val sampleResponse = """
        ## Deine Vision
        Du willst attraktiv bleiben und mit 70 deine Frau tragen können.

        ## Teilziele
        - Körpergewicht: 65 kg
        - Lat Pulldown 60 kg

        ## Nächste Schritte
        Lat Pulldown auf 57,5 kg steigern.

        <plan>
        {
          "valid_until": "2026-05-03",
          "bottleneck": {"type":"bodyweight","target":65.0,"current":64.1},
          "weekly": {"protein_g":105,"kcal":2500,"sessions":3,"bodyweight_kg":65.0},
          "exercise_targets":[
            {"code":"F3","action":"progress","weight_kg":57.5,"reason":"sauber 2x12"},
            {"code":"D3","action":"hold","weight_kg":27.5,"reason":"10/11 inkonsistent"}
          ]
        }
        </plan>
    """.trimIndent()

    @Test
    fun parse_extractsPlanAndTargets() {
        val result = CoachingPlanParser.parse(sampleResponse)
        assertNotNull(result)
        val (plan, targets) = result!!
        assertEquals("2026-05-03", plan.validUntil)
        assertEquals("bodyweight", plan.bottleneckType)
        assertEquals(65.0, plan.bottleneckTarget, 0.001)
        assertEquals(64.1, plan.bottleneckCurrent, 0.001)
        assertEquals(105, plan.weeklyProteinG)
        assertEquals(2500, plan.weeklyKcal)
        assertEquals(3, plan.weeklySessions)
        assertEquals(65.0, plan.weeklyBodyweightKg, 0.001)

        assertEquals(2, targets.size)
        val pulldown = targets.first { it.exerciseCode == "F3" }
        assertEquals("progress", pulldown.action)
        assertEquals(57.5, pulldown.targetWeightKg, 0.001)

        val shoulder = targets.first { it.exerciseCode == "D3" }
        assertEquals("hold", shoulder.action)
        assertEquals(27.5, shoulder.targetWeightKg, 0.001)
        assertEquals("10/11 inkonsistent", shoulder.reasonText)
    }

    @Test
    fun stripPlanBlock_removesXmlBlock() {
        val stripped = CoachingPlanParser.stripPlanBlock(sampleResponse)
        assertTrue(stripped.contains("Deine Vision"))
        assertTrue(stripped.contains("Lat Pulldown"))
        assertTrue(!stripped.contains("<plan>"))
        assertTrue(!stripped.contains("exercise_targets"))
    }

    @Test
    fun parse_returnsNullForResponseWithoutPlanBlock() {
        val onlyProse = "## Deine Vision\nProse only without plan block."
        assertNull(CoachingPlanParser.parse(onlyProse))
    }

    @Test
    fun parse_returnsNullForMalformedJson() {
        val broken = "Prose. <plan>{ invalid json here</plan>"
        assertNull(CoachingPlanParser.parse(broken))
    }

    @Test
    fun parse_handlesMissingOptionalFields() {
        val minimal = """
            Some prose.
            <plan>
            {
              "exercise_targets":[
                {"code":"B2","action":"progress","weight_kg":42.5,"reason":""}
              ]
            }
            </plan>
        """.trimIndent()
        val result = CoachingPlanParser.parse(minimal)
        assertNotNull(result)
        val (plan, targets) = result!!
        assertEquals("", plan.bottleneckType)
        assertEquals(0, plan.weeklyProteinG)
        assertEquals(1, targets.size)
        assertEquals("B2", targets[0].exerciseCode)
    }
}
