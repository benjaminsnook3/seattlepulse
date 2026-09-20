package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.ImpactLevel
import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.domain.model.TransitServiceType
import com.seattlepulse.backend.persistence.entity.EventEntity
import com.seattlepulse.backend.persistence.entity.SportsEntity
import com.seattlepulse.backend.persistence.entity.TrafficEntity
import com.seattlepulse.backend.persistence.entity.TransitEntity
import com.seattlepulse.backend.persistence.entity.WeatherEntity
import com.seattlepulse.backend.persistence.repository.EventRepository
import com.seattlepulse.backend.persistence.repository.SportsRepository
import com.seattlepulse.backend.persistence.repository.TrafficRepository
import com.seattlepulse.backend.persistence.repository.TransitRepository
import com.seattlepulse.backend.persistence.repository.WeatherRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ImpactEngineTest {

    private val now = Instant.now()

    private fun engine(
        weather: WeatherEntity? = null,
        transit: List<TransitEntity> = emptyList(),
        sports: List<SportsEntity> = emptyList(),
        events: List<EventEntity> = emptyList(),
        traffic: List<TrafficEntity> = emptyList()
    ): ImpactEngine {
        val weatherRepository = mockk<WeatherRepository>()
        val transitRepository = mockk<TransitRepository>()
        val sportsRepository = mockk<SportsRepository>()
        val eventRepository = mockk<EventRepository>()
        val trafficRepository = mockk<TrafficRepository>()

        every { weatherRepository.findTop20ByOrderByObservedAtDesc() } returns listOfNotNull(weather)
        every { transitRepository.findActive(any()) } returns transit
        every { sportsRepository.findByStartAtBetween(any(), any()) } returns sports
        every { eventRepository.findByMajorTrueAndStartAtAfter(any()) } returns events
        every { trafficRepository.findTop50ByOrderByUpdatedAtDesc() } returns traffic

        return ImpactEngine(
            weatherRepository, transitRepository, sportsRepository,
            eventRepository, trafficRepository, PersistenceMapper()
        )
    }

    @Test
    fun `quiet city scores low with clear explanation`() {
        val impact = engine().calculateImpact()
        assertEquals(ImpactLevel.LOW, impact.level)
        assertTrue(impact.value < 25)
        assertTrue(impact.explanation.contains("clear", ignoreCase = true))
    }

    @Test
    fun `mariners game plus link delays plus concert produces high impact with factors`() {
        val impact = engine(
            transit = listOf(transit("1 Line", Severity.HIGH)),
            sports = listOf(sports("Mariners", Severity.MODERATE)),
            events = listOf(event("Downtown Concert", "Music / Concert", importance = 70))
        ).calculateImpact()

        assertTrue(impact.value >= 50, "Expected HIGH band, got ${impact.value}")
        assertEquals(ImpactLevel.HIGH, impact.level)
        assertTrue(impact.factors.any { it.contains("Link delays", ignoreCase = true) })
        assertTrue(impact.factors.any { it.contains("Mariners game") })
        assertTrue(impact.factors.any { it.contains("concert", ignoreCase = true) })
        assertTrue(impact.explanation.contains("HIGH impact"))
    }

    @Test
    fun `severe band at 75 plus`() {
        val impact = engine(
            weather = weather(Severity.CRITICAL),
            transit = listOf(transit("1 Line", Severity.CRITICAL), transit("2 Line", Severity.HIGH)),
            traffic = listOf(traffic(Severity.CRITICAL, lanes = 3))
        ).calculateImpact()

        assertTrue(impact.value >= 75, "Expected SEVERE, got ${impact.value}")
        assertEquals(ImpactLevel.SEVERE, impact.level)
    }

    @Test
    fun `score is capped at 100`() {
        val impact = engine(
            weather = weather(Severity.CRITICAL),
            transit = (1..10).map { transit("$it Line", Severity.CRITICAL) },
            sports = (1..5).map { sports("Team$it", Severity.HIGH) },
            events = (1..5).map { event("Event$it", "Festival", importance = 100) },
            traffic = (1..5).map { traffic(Severity.CRITICAL, lanes = 4) }
        ).calculateImpact()

        assertEquals(100, impact.value)
    }

    @Test
    fun `away games do not contribute`() {
        val awayOnly = engine(sports = listOf(sports("Seahawks", Severity.HIGH, inSeattle = false)))
            .calculateImpact()
        assertEquals(0, awayOnly.sportsContribution)
    }

    private fun weather(severity: Severity) = WeatherEntity(
        externalId = "w1", observedAt = now, updatedAt = now, expiresAt = now.plus(1, ChronoUnit.HOURS),
        latitude = 47.6, longitude = -122.3, locationLabel = "Seattle",
        temperatureCelsius = 12.0, rainProbabilityPercent = 80, windSpeedKmh = 45.0,
        condition = "Thunderstorm", forecastSummary = "Storms", forecastHighCelsius = 14.0,
        forecastLowCelsius = 10.0, forecastPeakRainProbabilityPercent = 90,
        description = "storm", severity = severity, sourceProvider = "Test", sourceEndpoint = null
    )

    private fun transit(line: String, severity: Severity) = TransitEntity(
        externalId = "t-$line", serviceType = TransitServiceType.LINK, affectedLine = line,
        affectedArea = "Downtown", status = "Ongoing", description = "delays",
        severity = severity, startTime = now.minus(1, ChronoUnit.HOURS), endTime = now.plus(2, ChronoUnit.HOURS),
        updatedAt = now, expiresAt = now.plus(2, ChronoUnit.HOURS),
        sourceProvider = "SoundTransit", sourceEndpoint = null, sourceUrl = null
    )

    private fun sports(team: String, severity: Severity, inSeattle: Boolean = true) = SportsEntity(
        externalId = "s-$team", team = team, opponent = "Opp", startAt = now.plus(2, ChronoUnit.HOURS),
        venue = "T-Mobile Park", homeAway = "HOME", eventStatus = "Scheduled", inSeattle = inSeattle,
        description = "game", severity = severity, updatedAt = now,
        expiresAt = now.plus(6, ChronoUnit.HOURS), sourceProvider = "ESPNSeattle", sourceEndpoint = null
    )

    private fun event(title: String, category: String, importance: Int) = EventEntity(
        externalId = "e-$title", title = title, category = category, venue = "Downtown Seattle",
        venueId = null, latitude = null, longitude = null,
        startAt = now.plus(3, ChronoUnit.HOURS), endAt = now.plus(6, ChronoUnit.HOURS),
        attendanceEstimate = null, importanceScore = importance, major = true,
        description = "event", severity = Severity.MODERATE, updatedAt = now,
        expiresAt = now.plus(12, ChronoUnit.HOURS), sourceProvider = "Ticketmaster", sourceEndpoint = null
    )

    private fun traffic(severity: Severity, lanes: Int) = TrafficEntity(
        externalId = "tr-$severity-$lanes", title = "Collision", description = "lanes closed",
        severity = severity, latitude = 47.6, longitude = -122.3, locationLabel = "I-5",
        lanesImpacted = lanes, updatedAt = now, expiresAt = now.plus(2, ChronoUnit.HOURS),
        sourceProvider = "Test", sourceEndpoint = null
    )
}
