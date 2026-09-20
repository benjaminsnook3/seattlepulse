package com.example.newworkspace.network.mapper

import com.example.newworkspace.domain.model.Severity
import com.example.newworkspace.domain.model.TransitServiceType
import com.example.newworkspace.network.api.GeoPointResponseDto
import com.example.newworkspace.network.api.ImpactResponseDto
import com.example.newworkspace.network.api.SourceResponseDto
import com.example.newworkspace.network.api.SportsResponseDto
import com.example.newworkspace.network.api.TransitResponseDto
import com.example.newworkspace.network.api.WeatherResponseDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeattleApiMappersTest {

    private val source = SourceResponseDto(provider = "TestProvider", endpoint = null, fetchedAt = "2026-09-14T18:00:00Z")

    @Test
    fun `weather dto maps to domain with instant conversion`() {
        val dto = WeatherResponseDto(
            id = "w1",
            observedAt = "2026-09-14T18:00:00Z",
            updatedAt = "2026-09-14T18:05:00Z",
            expiresAt = null,
            location = GeoPointResponseDto(47.6062, -122.3321, "Seattle"),
            temperatureCelsius = 14.5,
            rainProbabilityPercent = 60,
            windSpeedKmh = 22.0,
            condition = "Rain",
            forecastSummary = "Rain likely",
            forecastHighCelsius = 16.0,
            forecastLowCelsius = 11.0,
            forecastPeakRainProbabilityPercent = 75,
            forecast = emptyList(),
            description = "d",
            severity = "HIGH",
            source = source
        )

        val domain = dto.toDomain()
        assertEquals("w1", domain.id)
        assertEquals(Instant.parse("2026-09-14T18:00:00Z"), domain.observedAt)
        assertEquals(14.5, domain.temperatureCelsius, 0.001)
        assertEquals(Severity.HIGH, domain.severity)
        assertEquals("TestProvider", domain.source.provider)
        assertEquals(Instant.parse("2026-09-14T18:00:00Z"), domain.source.fetchedAt)
    }

    @Test
    fun `transit dto maps service type and severity`() {
        val dto = TransitResponseDto(
            id = "t1",
            serviceType = "METRO",
            affectedLine = "Route 8",
            affectedArea = "Downtown",
            status = "Ongoing",
            description = "detour",
            severity = "CRITICAL",
            startTime = "2026-09-14T17:00:00Z",
            endTime = null,
            updatedAt = "2026-09-14T17:00:00Z",
            expiresAt = null,
            source = source
        )

        val domain = dto.toDomain()
        assertEquals(TransitServiceType.METRO, domain.serviceType)
        assertEquals(Severity.SEVERE, domain.severity)
        assertEquals(Instant.parse("2026-09-14T17:00:00Z"), domain.startTime)
        assertNull(domain.endTime)
    }

    @Test
    fun `sports dto maps venue name and inSeattle`() {
        val dto = SportsResponseDto(
            id = "s1", team = "Mariners", opponent = "Astros",
            startAt = "2026-09-14T23:10:00Z", venue = "T-Mobile Park",
            homeAway = "HOME", eventStatus = "Scheduled", inSeattle = true,
            description = "game", severity = "MODERATE",
            updatedAt = "2026-09-14T18:00:00Z", expiresAt = null, source = source
        )

        val domain = dto.toDomain()
        assertEquals("T-Mobile Park", domain.venue.name)
        assertTrue(domain.inSeattle)
        assertEquals(Instant.parse("2026-09-14T23:10:00Z"), domain.startAt)
    }

    @Test
    fun `impact dto maps level and factors`() {
        val dto = ImpactResponseDto(
            value = 62, level = "HIGH", weatherContribution = 12, transitContribution = 20,
            sportsContribution = 20, eventsContribution = 10, trafficContribution = 0,
            factors = listOf("Mariners game", "Link delays"),
            generatedAt = "2026-09-14T18:00:00Z", explanation = "HIGH impact"
        )

        val domain = dto.toDomain()
        assertEquals(62, domain.value)
        assertEquals(Severity.HIGH, domain.level)
        assertEquals(2, domain.factors.size)
    }

    @Test
    fun `unknown severity maps to UNKNOWN not crash`() {
        assertEquals(Severity.UNKNOWN, Severity.fromApi("WEIRD"))
        assertEquals(Severity.UNKNOWN, Severity.fromApi(null))
        assertEquals(Severity.SEVERE, Severity.fromApi("CRITICAL"))
    }
}
