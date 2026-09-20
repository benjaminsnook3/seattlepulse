package com.example.newworkspace.network.mapper

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimestampParserTest {

    @Test
    fun `parses Z-suffixed instant`() {
        val parsed = TimestampParser.parseOrNull("2026-09-14T18:30:00Z")
        assertEquals(Instant.parse("2026-09-14T18:30:00Z"), parsed)
    }

    @Test
    fun `parses offset date time`() {
        val parsed = TimestampParser.parseOrNull("2026-09-14T11:30:00-07:00")
        assertEquals(Instant.parse("2026-09-14T18:30:00Z"), parsed)
    }

    @Test
    fun `returns null for blank or missing`() {
        assertNull(TimestampParser.parseOrNull(null))
        assertNull(TimestampParser.parseOrNull(""))
        assertNull(TimestampParser.parseOrNull("   "))
    }

    @Test
    fun `returns null for garbage`() {
        assertNull(TimestampParser.parseOrNull("not-a-date"))
    }

    @Test
    fun `parseOr falls back when unparseable`() {
        val fallback = Instant.parse("2000-01-01T00:00:00Z")
        assertEquals(fallback, TimestampParser.parseOr("garbage", fallback))
        assertEquals(fallback, TimestampParser.parseOr(null, fallback))
    }

    @Test
    fun `parseOr returns parsed value when valid`() {
        val fallback = Instant.parse("2000-01-01T00:00:00Z")
        val real = Instant.parse("2026-01-02T03:04:05Z")
        assertEquals(real, TimestampParser.parseOr("2026-01-02T03:04:05Z", fallback))
    }
}
