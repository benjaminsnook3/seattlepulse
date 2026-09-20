package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.TransitServiceType
import com.seattlepulse.backend.integration.dto.ExternalTransitDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class TransitNormalizationServiceTypeTest {

    private val service = NormalizationService()

    @Test
    fun `link dto normalizes with LINK service type`() {
        val dto = externalDto(serviceType = "LINK")
        val alert = service.toTransit(dto, "SoundTransit", null)
        assertEquals(TransitServiceType.LINK, alert.serviceType)
    }

    @Test
    fun `metro dto normalizes with METRO service type`() {
        val dto = externalDto(serviceType = "METRO")
        val alert = service.toTransit(dto, "KingCountyMetro", null)
        assertEquals(TransitServiceType.METRO, alert.serviceType)
    }

    @Test
    fun `unknown service type falls back to OTHER`() {
        val dto = externalDto(serviceType = "STREETCAR")
        val alert = service.toTransit(dto, "SomeProvider", null)
        assertEquals(TransitServiceType.OTHER, alert.serviceType)
    }

    private fun externalDto(serviceType: String) = ExternalTransitDto(
        id = "t-$serviceType",
        serviceType = serviceType,
        affectedLine = "Test Line",
        affectedArea = "Downtown",
        status = "Ongoing",
        description = "Test alert",
        severity = "MODERATE",
        startTime = Instant.now().minusSeconds(60),
        endTime = Instant.now().plusSeconds(3600),
        sourceUrl = "https://example.test",
        expiresAt = Instant.now().plusSeconds(3600)
    )
}
