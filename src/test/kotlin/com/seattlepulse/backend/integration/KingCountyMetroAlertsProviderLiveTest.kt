package com.seattlepulse.backend.integration

import com.seattlepulse.backend.integration.provider.KingCountyMetroAlertsProvider
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KingCountyMetroAlertsProviderLiveTest {

    @Test
    fun `fetches real Metro alerts from the GTFS-RT feed`() {
        val provider = KingCountyMetroAlertsProvider(
            "https://s3.amazonaws.com/kcm-alerts-realtime-prod/alerts.pb"
        )

        val alerts = provider.fetch()

        // The feed may legitimately be empty on quiet days; assert shape when present.
        assertTrue(alerts.all { it.serviceType == "METRO" }, "Metro alerts must carry METRO service type")
        assertTrue(alerts.all { it.affectedLine.isNotBlank() })
        assertTrue(alerts.all { it.description.isNotBlank() })
        assertTrue(alerts.all { it.id.startsWith("metro-") })
        assertTrue(alerts.all { it.expiresAt != null })
    }
}
