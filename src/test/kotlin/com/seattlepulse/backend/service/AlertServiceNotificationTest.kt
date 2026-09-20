package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.ImpactLevel
import com.seattlepulse.backend.domain.model.ImpactScore
import com.seattlepulse.backend.persistence.entity.AlertEntity
import com.seattlepulse.backend.persistence.entity.DeviceTokenEntity
import com.seattlepulse.backend.persistence.repository.AlertRepository
import com.seattlepulse.backend.persistence.repository.DeviceTokenRepository
import com.seattlepulse.backend.persistence.repository.EventRepository
import com.seattlepulse.backend.persistence.repository.SportsRepository
import com.seattlepulse.backend.persistence.repository.TrafficRepository
import com.seattlepulse.backend.persistence.repository.TransitRepository
import com.seattlepulse.backend.persistence.repository.WeatherRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class AlertServiceNotificationTest {

    private fun service(
        impactEngine: ImpactEngine,
        gateway: PushNotificationGateway
    ): AlertService {
        val trafficRepository = mockk<TrafficRepository>()
        val transitRepository = mockk<TransitRepository>()
        val sportsRepository = mockk<SportsRepository>()
        val eventRepository = mockk<EventRepository>()
        val weatherRepository = mockk<WeatherRepository>()
        val alertRepository = mockk<AlertRepository>()
        val deviceTokenRepository = mockk<DeviceTokenRepository>()
        val persistenceMapper = mockk<PersistenceMapper>()

        every { trafficRepository.findTop50ByOrderByUpdatedAtDesc() } returns emptyList()
        every { transitRepository.findActive(any()) } returns emptyList()
        every { sportsRepository.findByStartAtBetween(any(), any()) } returns emptyList()
        every { eventRepository.findByMajorTrueAndStartAtAfter(any()) } returns emptyList()
        every { weatherRepository.findTop20ByOrderByObservedAtDesc() } returns emptyList()
        every { deviceTokenRepository.findAllByEnabledTrue() } returns listOf(
            DeviceTokenEntity(token = "tok-1", platform = "android", createdAt = Instant.now(), enabled = true)
        )
        every { alertRepository.existsByExternalId(any()) } returns false
        val entitySlot = slot<AlertEntity>()
        every { alertRepository.save(capture(entitySlot)) } answers { entitySlot.captured }
        every { persistenceMapper.toEntity(any<com.seattlepulse.backend.domain.model.AlertRecord>()) } answers {
            AlertEntity(
                externalId = firstArg<com.seattlepulse.backend.domain.model.AlertRecord>().id,
                category = "IMPACT",
                title = "t",
                message = "m",
                severity = com.seattlepulse.backend.domain.model.Severity.HIGH,
                createdAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(3600)
            )
        }

        return AlertService(
            impactEngine = impactEngine,
            trafficRepository = trafficRepository,
            transitRepository = transitRepository,
            sportsRepository = sportsRepository,
            eventRepository = eventRepository,
            weatherRepository = weatherRepository,
            alertRepository = alertRepository,
            deviceTokenRepository = deviceTokenRepository,
            persistenceMapper = persistenceMapper,
            pushNotificationGateway = gateway,
            notificationPolicy = NotificationPolicy(),
            notificationsEnabled = true
        )
    }

    private fun impact(level: ImpactLevel) = ImpactScore(
        value = 60, level = level, weatherContribution = 0, transitContribution = 30,
        sportsContribution = 20, eventsContribution = 10, trafficContribution = 0,
        factors = listOf("Link delays", "Mariners game"), generatedAt = Instant.now(), explanation = "HIGH impact"
    )

    @Test
    fun `high impact sends one push then is suppressed on repeat evaluation`() {
        val impactEngine = mockk<ImpactEngine>()
        every { impactEngine.calculateImpact() } returns impact(ImpactLevel.HIGH)
        val gateway = mockk<PushNotificationGateway>(relaxed = true)

        val alertService = service(impactEngine, gateway)

        alertService.evaluateAndEmitAlerts()
        verify(exactly = 1) { gateway.send("tok-1", any(), any()) }

        // Same level, same cycle: cooldown + unchanged level must suppress a second push.
        alertService.evaluateAndEmitAlerts()
        verify(exactly = 1) { gateway.send("tok-1", any(), any()) }
    }

    @Test
    fun `per-cycle cap limits pushes to three`() {
        val impactEngine = mockk<ImpactEngine>()
        every { impactEngine.calculateImpact() } returns impact(ImpactLevel.SEVERE)
        val gateway = mockk<PushNotificationGateway>(relaxed = true)

        val trafficRepository = mockk<TrafficRepository>()
        val transitRepository = mockk<TransitRepository>()
        val sportsRepository = mockk<SportsRepository>()
        val eventRepository = mockk<EventRepository>()
        val weatherRepository = mockk<WeatherRepository>()
        val alertRepository = mockk<AlertRepository>()
        val deviceTokenRepository = mockk<DeviceTokenRepository>()
        val persistenceMapper = mockk<PersistenceMapper>()

        every { trafficRepository.findTop50ByOrderByUpdatedAtDesc() } returns emptyList()
        // Five separate major disruptions -> far more candidates than the cap.
        every { transitRepository.findActive(any()) } returns (1..5).map { line ->
            com.seattlepulse.backend.persistence.entity.TransitEntity(
                externalId = "t$line", serviceType = com.seattlepulse.backend.domain.model.TransitServiceType.LINK,
                affectedLine = "$line Line", affectedArea = null, status = "Ongoing", description = "down",
                severity = com.seattlepulse.backend.domain.model.Severity.CRITICAL,
                startTime = Instant.now(), endTime = Instant.now().plusSeconds(3600),
                updatedAt = Instant.now(), expiresAt = Instant.now().plusSeconds(3600),
                sourceProvider = "SoundTransit", sourceEndpoint = null, sourceUrl = null
            )
        }
        every { sportsRepository.findByStartAtBetween(any(), any()) } returns emptyList()
        every { eventRepository.findByMajorTrueAndStartAtAfter(any()) } returns emptyList()
        every { weatherRepository.findTop20ByOrderByObservedAtDesc() } returns emptyList()
        every { deviceTokenRepository.findAllByEnabledTrue() } returns listOf(
            DeviceTokenEntity(token = "tok-1", platform = "android", createdAt = Instant.now(), enabled = true)
        )
        every { alertRepository.existsByExternalId(any()) } returns false
        val entitySlot = slot<AlertEntity>()
        every { alertRepository.save(capture(entitySlot)) } answers { entitySlot.captured }
        every { persistenceMapper.toEntity(any<com.seattlepulse.backend.domain.model.AlertRecord>()) } answers {
            AlertEntity(
                externalId = firstArg<com.seattlepulse.backend.domain.model.AlertRecord>().id,
                category = "TRANSIT", title = "t", message = "m",
                severity = com.seattlepulse.backend.domain.model.Severity.CRITICAL,
                createdAt = Instant.now(), expiresAt = Instant.now().plusSeconds(3600)
            )
        }

        val alertService = AlertService(
            impactEngine, trafficRepository, transitRepository, sportsRepository, eventRepository,
            weatherRepository, alertRepository, deviceTokenRepository, persistenceMapper, gateway,
            NotificationPolicy(), true
        )

        alertService.evaluateAndEmitAlerts()

        verify(exactly = NotificationPolicy.MAX_PER_CYCLE) { gateway.send(any(), any(), any()) }
    }
}
