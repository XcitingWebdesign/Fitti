package com.fitti.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class FormattingTest {

    @Test
    fun formatDateTime_producesExpectedPattern() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 15, 14, 30)
        }
        val result = formatDateTime(cal.time)
        assertEquals("15.03.2026 14:30", result)
    }

    @Test
    fun formatDate_producesExpectedPattern() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 15, 14, 30)
        }
        val result = formatDate(cal.time)
        assertEquals("15.03.2026", result)
    }

    @Test
    fun parseDateTime_validString() {
        val date = parseDateTime("15.03.2026 14:30")
        assertNotNull(date)
        val cal = Calendar.getInstance().apply { time = date!! }
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun parseDateTime_invalidString_returnsNull() {
        assertNull(parseDateTime("not-a-date"))
        assertNull(parseDateTime(""))
    }

    @Test
    fun daysSince_today_returnsZero() {
        assertEquals(0, daysSince(Date()))
    }

    @Test
    fun daysSince_threeDaysAgo() {
        val threeDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -3)
        }.time
        assertEquals(3, daysSince(threeDaysAgo))
    }

    @Test
    fun cleanWeight_wholeNumber() {
        assertEquals("41", 41.0.cleanWeight())
    }

    @Test
    fun cleanWeight_fractional() {
        assertEquals("41.5", 41.5.cleanWeight())
    }

    @Test
    fun calculateDuration_validTimes() {
        val result = calculateDuration("15.03.2026 14:00", "15.03.2026 14:45")
        assertEquals("45 min", result)
    }

    @Test
    fun calculateDuration_nullCompletedAt() {
        assertNull(calculateDuration("15.03.2026 14:00", null))
    }

    @Test
    fun calculateDuration_invalidDate() {
        assertNull(calculateDuration("invalid", "also invalid"))
    }

    @Test
    fun freshnessConstants_areReasonable() {
        assertTrue(FRESHNESS_FRESH_DAYS < FRESHNESS_STALE_DAYS)
        assertTrue(FRESHNESS_STALE_DAYS > 0)
        assertTrue(WEIGHT_LOG_INTERVAL_DAYS > 0)
    }
}
