package com.fitti.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StrengthTargetParserTest {

    @Test
    fun parse_extractsTargetsBlock() {
        val response = """
            Hier deine Einordnung in zwei Sätzen.
            <targets>
            { "groups": [
              {"group":"CHEST","target_kg":70,"rationale":"Begründung Brust"},
              {"group":"BACK","target_kg":80,"rationale":"Begründung Rücken"}
            ] }
            </targets>
        """.trimIndent()

        val targets = StrengthTargetParser.parse(response, "Männlich, 45 J")
        assertNotNull(targets)
        assertEquals(70.0, targets!!.byGroup["CHEST"]!!, 0.0001)
        assertEquals(80.0, targets.byGroup["BACK"]!!, 0.0001)
        assertEquals("Begründung Brust", targets.rationaleByGroup["CHEST"])
        assertEquals("Männlich, 45 J", targets.inputs)
    }

    @Test
    fun parse_noBlock_returnsNull() {
        assertNull(StrengthTargetParser.parse("Nur Fließtext ohne Block.", ""))
    }

    @Test
    fun parse_invalidJson_returnsNull() {
        assertNull(StrengthTargetParser.parse("<targets>kein json</targets>", ""))
    }

    @Test
    fun parse_filtersUnknownGroups() {
        val response = "<targets>{ \"groups\": [ {\"group\":\"FOO\",\"target_kg\":50} ] }</targets>"
        assertNull(StrengthTargetParser.parse(response, ""))
    }

    @Test
    fun stripTargetsBlock_removesBlock() {
        val response = "Prosa.\n<targets>{}</targets>"
        assertEquals("Prosa.", StrengthTargetParser.stripTargetsBlock(response))
    }
}
