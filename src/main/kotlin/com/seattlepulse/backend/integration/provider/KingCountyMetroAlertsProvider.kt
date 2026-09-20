package com.seattlepulse.backend.integration.provider

import com.google.transit.realtime.GtfsRealtime
import com.seattlepulse.backend.integration.dto.ExternalTransitDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Reads King County Metro service alerts from the official GTFS-RT alerts feed.
 * Alerts are normalized into the shared ExternalTransitDto shape so they flow
 * through the same TransitAlert pipeline as Sound Transit Link alerts.
 */
@Component
class KingCountyMetroAlertsProvider(
    @Value("\${seattle.transit.metro-alerts-url:https://s3.amazonaws.com/kcm-alerts-realtime-prod/alerts.pb}")
    private val alertsUrl: String
) : TransitProvider {

    override val providerName = "KingCountyMetro"

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun fetch(): List<ExternalTransitDto> {
        val bytes = restClient.get()
            .uri(alertsUrl)
            .accept(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .body(ByteArray::class.java)

        if (bytes == null || bytes.isEmpty()) {
            logger.warn("Metro alerts feed returned no data")
            return emptyList()
        }

        val feed = GtfsRealtime.FeedMessage.parseFrom(bytes)
        if (!feed.hasHeader()) {
            return emptyList()
        }

        // Feed timestamps are GPS epoch seconds; guard against nonsense values.
        val feedTimestamp = feed.header.timestamp.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it) } ?: Instant.now()
        val now = Instant.now()

        return feed.entityList
            .filter { it.hasAlert() }
            .mapNotNull { entity -> mapAlert(entity.alert, entity.id, feedTimestamp, now) }
    }

    private fun mapAlert(
        alert: GtfsRealtime.Alert,
        entryId: String,
        feedTimestamp: Instant,
        now: Instant
    ): ExternalTransitDto? {
        val cause = alert.cause.name
        val effect = alert.effect.name
        val header = translatedText(alert.headerText)
        val description = translatedText(alert.descriptionText)

        val text = listOf(header, cause, effect, description)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(". ")
            .take(3500)

        if (text.isBlank()) {
            return null
        }

        val activePeriod = alert.activePeriodList.firstOrNull()
        val startTime = activePeriod?.takeIf { it.hasStart() }?.start?.let { Instant.ofEpochSecond(it) }
        val endTime = activePeriod?.takeIf { it.hasEnd() }?.end?.let { Instant.ofEpochSecond(it) }

        // Skip alerts whose active window has already closed relative to the feed.
        if (endTime != null && endTime.isBefore(feedTimestamp)) {
            return null
        }

        val routeNames = alert.informedEntityList
            .mapNotNull { it.routeId.takeIf { id -> id.isNotBlank() } }
            .distinct()
            .map { routeDisplayName(it) }

        val affectedLine = routeNames.firstOrNull() ?: "Metro"

        val stopNames = alert.informedEntityList
            .mapNotNull { it.stopId.takeIf { id -> id.isNotBlank() } }
            .distinct()

        val affectedArea = when {
            stopNames.isNotEmpty() -> stopNames.joinToString(", ")
            routeNames.isNotEmpty() -> "Route ${routeNames.joinToString(", ")}"
            else -> null
        }

        val severity = inferSeverity(cause, effect, description)
        val expiresAt = endTime ?: now.plus(8, ChronoUnit.HOURS)

        return ExternalTransitDto(
            id = "metro-$entryId",
            serviceType = "METRO",
            affectedLine = affectedLine,
            affectedArea = affectedArea,
            status = statusOf(startTime, endTime, now),
            description = text,
            severity = severity,
            startTime = startTime,
            endTime = endTime,
            sourceUrl = METRO_ALERTS_PAGE_URL,
            expiresAt = expiresAt
        )
    }

    private fun translatedText(value: GtfsRealtime.TranslatedString): String =
        value.translationList.firstOrNull()?.text?.trim().orEmpty()

    private fun routeDisplayName(routeId: String): String {
        // Metro route ids are numeric strings like "8" or "E"; keep them readable.
        val trimmed = routeId.trim()
        return if (trimmed.all { it.isDigit() }) "Route $trimmed" else "Route $trimmed"
    }

    private fun statusOf(startTime: Instant?, endTime: Instant?, now: Instant): String = when {
        startTime != null && startTime.isAfter(now) -> "Upcoming"
        else -> "Ongoing"
    }

    private fun inferSeverity(cause: String, effect: String, description: String): String {
        val text = "$cause $effect $description".lowercase(Locale.US)
        return when {
            listOf("suspended", "cancelled", "canceled", "no service", "closed", "closure")
                .any { text.contains(it) } -> "CRITICAL"
            listOf("delay", "reduced", "detour", "diverted", "modified", "accident", "police", "fire", "medical")
                .any { text.contains(it) } -> "HIGH"
            listOf("stop moved", "relocated", "info", "information", "schedule change")
                .any { text.contains(it) } -> "LOW"
            else -> "MODERATE"
        }
    }

    companion object {
        private const val METRO_ALERTS_PAGE_URL = "https://kingcounty.gov/en/dept/metro/travel-options/riders-guide/service-alerts"
        private val restClient = org.springframework.web.client.RestClient.builder().build()
    }
}
