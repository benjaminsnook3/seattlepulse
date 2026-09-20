package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.GeoPoint
import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.domain.model.TransitServiceType
import com.seattlepulse.backend.domain.model.EventRecord
import com.seattlepulse.backend.domain.model.TrafficRecord
import com.seattlepulse.backend.domain.model.DataSource
import com.seattlepulse.backend.domain.model.TransitAlert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class UserAreaServiceTest {

    private val service = UserAreaService()
    private val now = Instant.now()

    @Test
    fun `no location defaults to downtown without error`() {
        val area = service.resolveArea(null, null, null)
        assertEquals(SeattleNeighborhood.DOWNTOWN, area.neighborhood)
        assertEquals(47.6062, area.center.latitude, 0.001)
    }

    @Test
    fun `manual neighborhood selection wins over coordinates`() {
        val area = service.resolveArea(47.6062, -122.3321, "Ballard")
        assertEquals(SeattleNeighborhood.BALLARD, area.neighborhood)
    }

    @Test
    fun `coordinates resolve to nearest neighborhood`() {
        // Near Climate Pledge Arena / First Hill area.
        val area = service.resolveArea(47.6222, -122.3541, null)
        assertEquals(SeattleNeighborhood.QUEEN_ANNE, area.neighborhood)
    }

    @Test
    fun `events outside radius are filtered out`() {
        val area = service.resolveArea(null, null, "Capitol Hill")
        val nearby = event("near", 47.6251, -122.3222)
        val far = event("far", 47.5480, -122.2690)

        val result = service.relevantEvents(area, listOf(nearby, far))
        assertEquals(listOf("near"), result.map { it.id })
    }

    @Test
    fun `traffic outside radius is filtered out`() {
        val area = service.resolveArea(null, null, "Downtown")
        val nearby = traffic("near", 47.61, -122.33)
        val far = traffic("far", 47.75, -122.40)

        val result = service.relevantTraffic(area, listOf(nearby, far))
        assertEquals(listOf("near"), result.map { it.id })
    }

    @Test
    fun `transit matching area label is relevant`() {
        val area = service.resolveArea(null, null, "Northgate")
        val alerts = listOf(
            transit("northgate", "delays between Northgate and downtown"),
            transit("rainier", "delays near Rainier Beach")
        )

        val result = service.relevantTransit(area, alerts)
        assertEquals(1, result.size)
        assertTrue(result.first().description.contains("Northgate"))
    }

    private fun event(id: String, lat: Double, lon: Double) = EventRecord(
        id = id, title = "Concert", category = "Music", venue = "Venue", venueId = null,
        location = GeoPoint(lat, lon, "V"), startAt = now.plus(2, ChronoUnit.HOURS), endAt = null,
        attendanceEstimate = null, importanceScore = 70, major = true, description = "d",
        severity = Severity.MODERATE, updatedAt = now, expiresAt = now.plus(6, ChronoUnit.HOURS),
        source = DataSource("Ticketmaster", null)
    )

    private fun traffic(id: String, lat: Double, lon: Double) = TrafficRecord(
        id = id, title = "Crash", description = "d", severity = Severity.HIGH,
        location = GeoPoint(lat, lon, "I-5"), lanesImpacted = 1, updatedAt = now,
        expiresAt = now.plus(1, ChronoUnit.HOURS), source = DataSource("Mock", null)
    )

    private fun transit(id: String, description: String) = TransitAlert(
        id = id, serviceType = TransitServiceType.LINK, affectedLine = "1 Line", affectedArea = null,
        status = "Ongoing", description = description, severity = Severity.MODERATE,
        startTime = now, endTime = now.plus(2, ChronoUnit.HOURS), updatedAt = now,
        expiresAt = now.plus(2, ChronoUnit.HOURS), source = DataSource("SoundTransit", null)
    )
}
