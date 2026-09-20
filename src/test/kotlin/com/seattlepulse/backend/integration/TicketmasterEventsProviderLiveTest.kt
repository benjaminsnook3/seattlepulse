package com.seattlepulse.backend.integration

import com.seattlepulse.backend.integration.provider.TicketmasterEventsProvider
import com.seattlepulse.backend.service.EventImportanceService
import com.seattlepulse.backend.service.SeattleVenue
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@EnabledIfEnvironmentVariable(named = "TICKETMASTER_API_KEY", matches = ".+")
class TicketmasterEventsProviderLiveTest {

    @Test
    fun `fetches real major events at Seattle venues`() {
        val provider = TicketmasterEventsProvider(
            eventImportanceService = EventImportanceService(),
            apiKey = System.getenv("TICKETMASTER_API_KEY"),
            lookaheadDays = 30
        )

        val events = provider.fetch()

        // Feed may legitimately have no majors in a quiet window; assert shape when present.
        assertTrue(events.all { it.major }, "Provider must only return major events")
        assertTrue(events.all { it.importanceScore >= EventImportanceService.MAJOR_THRESHOLD })
        assertTrue(events.all { it.title.isNotBlank() })
        assertTrue(events.all { it.venue.isNotBlank() })
        assertTrue(events.all { it.id.startsWith("tm-") })

        // Every event must belong to an approved Seattle venue (guards venue filtering).
        val approvedIds = SeattleVenue.entries.mapNotNull { it.ticketmasterId }.toSet()
        val approvedNames = SeattleVenue.entries.map { it.displayName.lowercase() }
        assertTrue(
            events.all { event ->
                event.venueId in approvedIds ||
                    approvedNames.any { event.venue.lowercase().contains(it) }
            },
            "Unexpected venue in results: ${events.map { it.venue }.distinct()}"
        )
    }
}
