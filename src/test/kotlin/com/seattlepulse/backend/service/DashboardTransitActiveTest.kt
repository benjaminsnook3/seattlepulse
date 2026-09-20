package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.persistence.entity.TransitEntity
import com.seattlepulse.backend.persistence.repository.EventRepository
import com.seattlepulse.backend.persistence.repository.SportsRepository
import com.seattlepulse.backend.persistence.repository.TrafficRepository
import com.seattlepulse.backend.persistence.repository.TransitRepository
import com.seattlepulse.backend.persistence.repository.WeatherRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class DashboardTransitActiveTest {

    @Test
    fun `dashboard uses active transit alerts only`() {
        val weatherRepository = mockk<WeatherRepository>()
        val transitRepository = mockk<TransitRepository>()
        val sportsRepository = mockk<SportsRepository>()
        val eventRepository = mockk<EventRepository>()
        val trafficRepository = mockk<TrafficRepository>()
        val impactEngine = mockk<ImpactEngine>()
        val mapper = PersistenceMapper()

        every { weatherRepository.findTop20ByOrderByObservedAtDesc() } returns emptyList()
        every { transitRepository.findActive(any()) } returns listOf(
            TransitEntity(
                externalId = "t1",
                affectedLine = "1 Line",
                affectedArea = "Downtown Seattle",
                status = "Ongoing",
                description = "Minor delay",
                severity = Severity.MODERATE,
                startTime = Instant.now().minusSeconds(1800),
                endTime = Instant.now().plusSeconds(1800),
                updatedAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(1800),
                sourceProvider = "SoundTransit",
                sourceEndpoint = "https://www.soundtransit.org/ride-with-us/service-alerts",
                sourceUrl = "https://www.soundtransit.org/ride-with-us/service-alerts/example"
            )
        )
        every { sportsRepository.findTop50ByOrderByStartAtDesc() } returns emptyList()
        every { eventRepository.findByMajorTrueAndStartAtAfter(any()) } returns emptyList()
        every { trafficRepository.findTop50ByOrderByUpdatedAtDesc() } returns emptyList()
        every { impactEngine.calculateImpact() } returns com.seattlepulse.backend.domain.model.ImpactScore(
            value = 0,
            level = com.seattlepulse.backend.domain.model.ImpactLevel.LOW,
            weatherContribution = 0,
            transitContribution = 0,
            sportsContribution = 0,
            eventsContribution = 0,
            trafficContribution = 0,
            generatedAt = Instant.now(),
            explanation = "none"
        )

        val service = DashboardService(
            weatherRepository,
            transitRepository,
            sportsRepository,
            eventRepository,
            trafficRepository,
            impactEngine,
            mapper
        )

        val dashboard = service.buildDashboard()

        verify(exactly = 1) { transitRepository.findActive(any()) }
        assertEquals(1, dashboard.transit.size)
        assertEquals("1 Line", dashboard.transit.first().affectedLine)
    }
}
