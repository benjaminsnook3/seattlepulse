package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.ImpactLevel
import com.seattlepulse.backend.domain.model.ImpactScore
import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.persistence.entity.SportsEntity
import com.seattlepulse.backend.persistence.repository.EventRepository
import com.seattlepulse.backend.persistence.repository.SportsRepository
import com.seattlepulse.backend.persistence.repository.TrafficRepository
import com.seattlepulse.backend.persistence.repository.TransitRepository
import com.seattlepulse.backend.persistence.repository.WeatherRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class DashboardTonightTest {

    @Test
    fun `dashboard tonight lists only future Seattle games ordered by start time`() {
        val weatherRepository = mockk<WeatherRepository>()
        val transitRepository = mockk<TransitRepository>()
        val sportsRepository = mockk<SportsRepository>()
        val eventRepository = mockk<EventRepository>()
        val trafficRepository = mockk<TrafficRepository>()
        val impactEngine = mockk<ImpactEngine>()
        val mapper = PersistenceMapper()

        val now = Instant.now()
        // Deterministic in-window times: fractions of the remaining "tonight" window in Seattle.
        val seattleZone = ZoneId.of("America/Los_Angeles")
        val endOfToday = now.atZone(seattleZone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(seattleZone)
            .toInstant()
        val remainingMillis = endOfToday.toEpochMilli() - now.toEpochMilli()
        val soon = Instant.ofEpochMilli(now.toEpochMilli() + remainingMillis / 3)
        val later = Instant.ofEpochMilli(now.toEpochMilli() + remainingMillis * 2 / 3)

        every { weatherRepository.findTop20ByOrderByObservedAtDesc() } returns emptyList()
        every { transitRepository.findActive(any()) } returns emptyList()
        every { eventRepository.findByMajorTrueAndStartAtAfter(any()) } returns emptyList()
        every { trafficRepository.findTop50ByOrderByUpdatedAtDesc() } returns emptyList()
        every { impactEngine.calculateImpact() } returns ImpactScore(
            value = 0, level = ImpactLevel.LOW, weatherContribution = 0, transitContribution = 0,
            sportsContribution = 0, eventsContribution = 0, trafficContribution = 0,
            generatedAt = now, explanation = "none"
        )
        every { sportsRepository.findTop50ByOrderByStartAtDesc() } returns listOf(
            sports("later", "Kraken", later, inSeattle = true),
            sports("tonight", "Mariners", soon, inSeattle = true),
            sports("away", "Seahawks", later, inSeattle = false),
            sports("past", "Storm", now.minus(2, ChronoUnit.HOURS), inSeattle = true)
        )

        val service = DashboardService(
            weatherRepository, transitRepository, sportsRepository,
            eventRepository, trafficRepository, impactEngine, mapper
        )

        val dashboard = service.buildDashboard()

        assertEquals(listOf("tonight", "later"), dashboard.tonight.map { it.id })
    }

    private fun sports(id: String, team: String, startAt: Instant, inSeattle: Boolean) = SportsEntity(
        externalId = id,
        team = team,
        opponent = "Opponent",
        startAt = startAt,
        venue = "Venue",
        homeAway = if (inSeattle) "HOME" else "AWAY",
        eventStatus = "Scheduled",
        inSeattle = inSeattle,
        description = "game",
        severity = Severity.MODERATE,
        updatedAt = Instant.now(),
        expiresAt = startAt.plus(6, ChronoUnit.HOURS),
        sourceProvider = "ESPNSeattle",
        sourceEndpoint = null
    )
}
