package com.seattlepulse.backend.integration

import com.seattlepulse.backend.integration.provider.ESPNSeattleSportsProvider
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ESPNSeattleSportsProviderLiveTest {

    @Test
    fun `fetches real upcoming games for Seattle teams`() {
        val provider = ESPNSeattleSportsProvider(
            "https://site.web.api.espn.com/apis/site/v2",
            lookaheadDays = 30
        )

        val games = provider.fetch()
        val now = Instant.now()

        assertTrue(games.isNotEmpty(), "Expected at least one upcoming Seattle game in 30 days")
        assertTrue(games.all { it.startAt.isAfter(now) }, "All games must be in the future")
        assertTrue(games.all { it.opponent.isNotBlank() })
        assertTrue(games.all { it.homeAway == "HOME" || it.homeAway == "AWAY" })
        assertTrue(games.all { it.eventStatus.isNotBlank() })

        // At least one team among the five should have a scheduled game in a 30-day window.
        val teamsSeen = games.map { it.team }.distinct()
        assertTrue(
            teamsSeen.any { it in setOf("Mariners", "Seahawks", "Kraken", "Sounders", "Storm") },
            "Expected a known Seattle team, got: $teamsSeen"
        )
    }
}
