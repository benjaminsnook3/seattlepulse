package com.seattlepulse.backend.integration

import com.seattlepulse.backend.integration.provider.SoundTransitLinkAlertsProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundTransitLinkAlertsProviderLiveTest {

    @Test
    fun `fetches real ongoing Link alerts from Sound Transit`() {
        val provider = SoundTransitLinkAlertsProvider(
            "https://www.soundtransit.org/ride-with-us/service-alerts?view=ongoing"
        )

        val alerts = provider.fetch()

        assertFalse(alerts.isEmpty(), "Expected real Link alerts from Sound Transit")
        assertTrue(alerts.all { it.affectedLine.contains("Line", ignoreCase = true) })
        assertTrue(alerts.all { it.description.isNotBlank() })
        assertTrue(alerts.all { it.sourceUrl?.contains("soundtransit.org", ignoreCase = true) == true })
    }
}
