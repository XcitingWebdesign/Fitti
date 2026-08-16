package com.fitti.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProteinEstimateParserTest {

    private val sampleResponse = """
        Ich gehe von gehäuften Esslöffeln aus.

        <protein>
        {
          "items": [
            {"name":"Haferflocken 4 EL","protein_g":5.4},
            {"name":"Mandelmus 1 EL","protein_g":3.2}
          ],
          "total_protein_g": 8.6,
          "note": "Annahme: gehäufte EL"
        }
        </protein>
    """.trimIndent()

    @Test
    fun parse_extractsItemsAndTotal() {
        val result = ProteinEstimateParser.parse(sampleResponse)
        assertNotNull(result)
        assertEquals(2, result!!.items.size)
        assertEquals("Haferflocken 4 EL", result.items[0].name)
        assertEquals(5.4, result.items[0].proteinG, 0.001)
        assertEquals(8.6, result.totalProteinG, 0.001)
        assertEquals("Annahme: gehäufte EL", result.note)
    }

    @Test
    fun parse_toleratesCodeFences() {
        val response = """
            Kurze Einordnung.
            <protein>
            ```json
            {"items":[{"name":"Quark 250 g","protein_g":30.0}],"total_protein_g":30.0}
            ```
            </protein>
        """.trimIndent()
        val result = ProteinEstimateParser.parse(response)
        assertNotNull(result)
        assertEquals(30.0, result!!.totalProteinG, 0.001)
    }

    @Test
    fun parse_toleratesTruncatedResponse() {
        // Abgeschnittene Antwort ohne schliessendes Tag, aber vollstaendigem JSON
        val response = """
            Einordnung.
            <protein>
            {"items":[{"name":"Ei","protein_g":7.0}],"total_protein_g":7.0}
        """.trimIndent()
        val result = ProteinEstimateParser.parse(response)
        assertNotNull(result)
        assertEquals(7.0, result!!.totalProteinG, 0.001)
    }

    @Test
    fun parse_usesItemSumWhenTotalMissing() {
        val response = """
            <protein>
            {"items":[{"name":"A","protein_g":4.0},{"name":"B","protein_g":6.0}]}
            </protein>
        """.trimIndent()
        val result = ProteinEstimateParser.parse(response)
        assertNotNull(result)
        assertEquals(10.0, result!!.totalProteinG, 0.001)
    }

    @Test
    fun parse_skipsInvalidItems() {
        val response = """
            <protein>
            {"items":[{"name":"","protein_g":4.0},{"name":"OK","protein_g":-1.0},
            {"name":"Gut","protein_g":12.0}],"total_protein_g":12.0}
            </protein>
        """.trimIndent()
        val result = ProteinEstimateParser.parse(response)
        assertNotNull(result)
        assertEquals(1, result!!.items.size)
        assertEquals("Gut", result.items[0].name)
    }

    @Test
    fun parse_returnsNullForProseOnly() {
        assertNull(ProteinEstimateParser.parse("Leider kann ich das nicht einschätzen."))
    }

    @Test
    fun parse_returnsNullForEmptyJson() {
        assertNull(ProteinEstimateParser.parse("<protein>{}</protein>"))
    }

    @Test
    fun stripProteinBlock_removesBlockKeepsProse() {
        val stripped = ProteinEstimateParser.stripProteinBlock(sampleResponse)
        assertTrue(stripped.contains("gehäuften Esslöffeln"))
        assertTrue(!stripped.contains("<protein>"))
        assertTrue(!stripped.contains("total_protein_g"))
    }
}
