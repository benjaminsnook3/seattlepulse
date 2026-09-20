package com.example.newworkspace.domain.usecase

import com.example.newworkspace.domain.model.FailureReason
import com.example.newworkspace.domain.model.ImpactScore
import com.example.newworkspace.domain.model.Outcome
import com.example.newworkspace.domain.model.PublicEvent
import com.example.newworkspace.domain.model.Severity
import com.example.newworkspace.domain.model.SportsEvent
import com.example.newworkspace.domain.model.TrafficIncident
import com.example.newworkspace.domain.model.TransitAlert
import com.example.newworkspace.domain.model.TransitServiceType
import com.example.newworkspace.domain.model.Venue
import com.example.newworkspace.domain.model.Weather
import com.example.newworkspace.domain.model.SeattleDaySummary
import com.example.newworkspace.domain.repository.SeattleRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetSeattleDaySummaryUseCaseTest {

    private val repository = mockk<SeattleRepository>()
    private val useCase = GetSeattleDaySummaryUseCase(repository)

    private val now = Instant.parse("2026-09-14T18:00:00Z")

    private fun stubAllSuccess() {
        coEvery { repository.getDashboardSummary() } returns Outcome.Success(
            SeattleDaySummary(
                weather = sampleWeather(),
                transitAlerts = listOf(sampleTransit()),
                sportsEvents = listOf(sampleSports()),
                publicEvents = listOf(sampleEvent()),
                trafficIncidents = listOf(sampleTraffic()),
                impact = sampleImpact(),
                generatedAt = now
            )
        )
    }

    @Test
    fun `all sources available builds complete summary with no failures`() = runTest {
        stubAllSuccess()

        val result = useCase()

        assertTrue(result.failures.isEmpty())
        assertEquals("w1", result.summary.weather?.id)
        assertEquals(1, result.summary.transitAlerts.size)
        assertEquals(1, result.summary.sportsEvents.size)
        assertEquals(1, result.summary.publicEvents.size)
        assertEquals(1, result.summary.trafficIncidents.size)
        assertEquals(62, result.summary.impact?.value)
    }

    @Test
    fun `dashboard failure reports all sections without crashing`() = runTest {
        coEvery { repository.getDashboardSummary() } returns Outcome.Failure(FailureReason.NETWORK, "down")

        val result = useCase()

        assertEquals(DataSection.entries.size, result.failures.size)
        assertTrue(result.failures.all { it.reason == FailureReason.NETWORK })
        assertNull(result.summary.weather)
        assertTrue(result.summary.sportsEvents.isEmpty())
    }

    @Test
    fun `dashboard timeout leaves summary empty and reports it`() = runTest {
        coEvery { repository.getDashboardSummary() } returns Outcome.Failure(FailureReason.TIMEOUT, "slow")

        val result = useCase()

        assertNull(result.summary.weather)
        assertTrue(result.failures.all { it.reason == FailureReason.TIMEOUT })
    }

    @Test
    fun `generatedAt is set on the aggregate`() = runTest {
        stubAllSuccess()
        val result = useCase()
        assertTrue(result.summary.generatedAt.isAfter(Instant.EPOCH))
    }

    private fun sampleWeather() = Weather(
        id = "w1", observedAt = now, updatedAt = now, expiresAt = null,
        location = com.example.newworkspace.domain.model.GeoPoint(47.6, -122.3, "Seattle"),
        temperatureCelsius = 15.0, rainProbabilityPercent = 40, windSpeedKmh = 10.0,
        condition = "Cloudy", forecastSummary = "Stable", forecastHighCelsius = 17.0,
        forecastLowCelsius = 12.0, forecastPeakRainProbabilityPercent = 50,
        description = "d", severity = Severity.LOW,
        source = com.example.newworkspace.domain.model.DataSource("Test", null, now)
    )

    private fun sampleTransit() = TransitAlert(
        id = "t1", serviceType = TransitServiceType.LINK, affectedLine = "1 Line",
        affectedArea = null, status = "Ongoing", description = "delays",
        severity = Severity.MODERATE, startTime = now, endTime = null, updatedAt = now,
        expiresAt = null, source = com.example.newworkspace.domain.model.DataSource("SoundTransit", null, now)
    )

    private fun sampleSports() = SportsEvent(
        id = "s1", team = "Mariners", opponent = "Astros", startAt = now,
        venue = Venue(null, "T-Mobile Park"), homeAway = "HOME", eventStatus = "Scheduled",
        inSeattle = true, description = "d", severity = Severity.MODERATE, updatedAt = now,
        expiresAt = null, source = com.example.newworkspace.domain.model.DataSource("ESPN", null, now)
    )

    private fun sampleEvent() = PublicEvent(
        id = "e1", title = "Concert", category = "Music", venue = Venue("v1", "Climate Pledge Arena"),
        startAt = now, endAt = null, attendanceEstimate = null, importanceScore = 70, major = true,
        description = "d", severity = Severity.MODERATE, updatedAt = now, expiresAt = null,
        source = com.example.newworkspace.domain.model.DataSource("Ticketmaster", null, now)
    )

    private fun sampleTraffic() = TrafficIncident(
        id = "tr1", title = "Crash", description = "d", severity = Severity.HIGH,
        location = com.example.newworkspace.domain.model.GeoPoint(47.6, -122.3, "I-5"),
        lanesImpacted = 2, updatedAt = now, expiresAt = null,
        source = com.example.newworkspace.domain.model.DataSource("Mock", null, now)
    )

    private fun sampleImpact() = ImpactScore(
        value = 62, level = Severity.HIGH, weatherContribution = 12, transitContribution = 20,
        sportsContribution = 20, eventsContribution = 10, trafficContribution = 0,
        factors = listOf("Mariners game"), generatedAt = now, explanation = "HIGH impact"
    )
}
