package com.fitti.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeModelResolverTest {

    @Test
    fun picksNewestModelPerFamily() {
        val json = """
            {"data": [
              {"id": "claude-opus-5-5", "created_at": "2026-10-01T00:00:00Z"},
              {"id": "claude-sonnet-5", "created_at": "2026-05-01T00:00:00Z"},
              {"id": "claude-fable-5-2", "created_at": "2026-11-01T00:00:00Z"},
              {"id": "claude-sonnet-5-5", "created_at": "2026-09-15T00:00:00Z"},
              {"id": "claude-opus-4-7", "created_at": "2026-02-01T00:00:00Z"}
            ]}
        """.trimIndent()
        val latest = ClaudeModelResolver.pickLatest(json)
        assertEquals("claude-sonnet-5-5", latest["sonnet"])
        assertEquals("claude-opus-5-5", latest["opus"])
    }

    @Test
    fun emptyListGivesNoResult() {
        assertTrue(ClaudeModelResolver.pickLatest("""{"data": []}""").isEmpty())
    }
}
