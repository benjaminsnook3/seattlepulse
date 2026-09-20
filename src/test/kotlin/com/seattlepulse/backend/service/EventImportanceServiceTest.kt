package com.seattlepulse.backend.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventImportanceServiceTest {

    private val service = EventImportanceService()

    @Test
    fun `professional game at climate pledge arena is major`() {
        val result = service.score(
            title = "Kraken vs Oilers",
            category = "Hockey",
            venueName = "Climate Pledge Arena",
            venueId = "KovZ917Ahkk",
            attendanceEstimate = null
        )
        assertTrue(result.major)
        assertTrue(result.score >= EventImportanceService.MAJOR_THRESHOLD)
    }

    @Test
    fun `large concert at major venue is major`() {
        val result = service.score(
            title = "Taylor Swift | The Eras Tour",
            category = "Concerts / Pop",
            venueName = "Lumen Field",
            venueId = "KovZpZAEknnA",
            attendanceEstimate = 65000
        )
        assertTrue(result.major)
    }

    @Test
    fun `small theatre show is not major`() {
        val result = service.score(
            title = "Local improv night",
            category = "Comedy",
            venueName = "Some Tiny Club",
            venueId = null,
            attendanceEstimate = 120
        )
        assertFalse(result.major)
        assertTrue(result.score < EventImportanceService.MAJOR_THRESHOLD)
    }

    @Test
    fun `festival keyword pushes event to major`() {
        val result = service.score(
            title = "Bumbershoot Festival",
            category = "Festival",
            venueName = "Seattle Center",
            venueId = "KovZpZAFkktA",
            attendanceEstimate = null
        )
        assertTrue(result.major)
    }

    @Test
    fun `venue id matching works even when name is missing`() {
        val result = service.score(
            title = "Championship Final",
            category = "Sports",
            venueName = null,
            venueId = "KovZpZAEevAA",
            attendanceEstimate = null
        )
        assertTrue(result.reasons.any { it.contains("T-Mobile Park") })
        assertTrue(result.major)
    }
}
