package com.seattlepulse.backend.integration.provider

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.seattlepulse.backend.integration.dto.ExternalEventDto
import com.seattlepulse.backend.service.EventImportanceService
import com.seattlepulse.backend.service.SeattleVenue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Pulls upcoming events at approved major Seattle venues from the Ticketmaster
 * Discovery API and keeps only events classified as "major" by the
 * EventImportanceService, so the app does not become an endless event list.
 */
@Component
class TicketmasterEventsProvider(
    private val eventImportanceService: EventImportanceService,
    @Value("\${seattle.events.ticketmaster-api-key:}") private val apiKey: String,
    @Value("\${seattle.events.lookahead-days:14}") private val lookaheadDays: Long
) : EventsProvider {

    override val providerName = "Ticketmaster"

    private val logger = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.builder().baseUrl("https://app.ticketmaster.com/discovery/v2").build()

    override fun fetch(): List<ExternalEventDto> {
        if (apiKey.isBlank()) {
            logger.warn("Ticketmaster API key not configured; skipping events ingestion")
            return emptyList()
        }

        val today = LocalDate.now(ZoneId.of("America/Los_Angeles"))
        val endDate = today.plusDays(lookaheadDays)
        val dateRange = "$today-$endDate"

        // The Discovery API only honors the singular "venueId" parameter, so each
        // approved venue is queried separately.
        val seen = mutableSetOf<String>()
        val events = mutableListOf<ExternalEventDto>()

        SeattleVenue.entries.mapNotNull { it.ticketmasterId }.forEach { venueId ->
            var page = 0
            while (page < MAX_PAGES) {
                val response = try {
                    restClient.get()
                        .uri { builder ->
                            builder.path("/events.json")
                                .queryParam("venueId", venueId)
                                .queryParam("dates", dateRange)
                                .queryParam("size", PAGE_SIZE)
                                .queryParam("page", page)
                                .queryParam("apikey", apiKey)
                                .build()
                        }
                        .retrieve()
                        .body(TmEventsResponse::class.java)
                } catch (ex: Exception) {
                    logger.warn("Ticketmaster venue {} page {} fetch failed: {}", venueId, page, ex.message)
                    break
                }

                val pageEvents = response?.embedded?.events.orEmpty()
                if (pageEvents.isEmpty()) break

                pageEvents.forEach { tm ->
                    val dto = mapEvent(tm)
                    if (seen.add(dto.id)) {
                        events += dto
                    }
                }

                val total = response?.page?.totalElements ?: 0
                if ((page + 1) * PAGE_SIZE >= total) break
                page++
            }
        }

        val majors = events.filter { it.major }
        logger.info("Ticketmaster returned {} events, {} major", events.size, majors.size)
        return majors
    }

    private fun mapEvent(tm: TmEvent): ExternalEventDto {
        val startAt = parseTmDate(tm.dates?.start?.dateTime) ?: Instant.now()
        val endAt = tm.dates?.end?.dateTime?.let { parseTmDate(it) }

        val venue = tm.embedded?.venues?.firstOrNull()
        val venueName = venue?.name ?: "Seattle"
        val venueId = venue?.id

        val category = listOfNotNull(
            tm.classifications?.firstOrNull()?.primaryCategory?.name,
            tm.classifications?.firstOrNull()?.genre?.name
        ).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "Event" }

        // Ticketmaster does not expose attendance estimates; capacity is used
        // by the importance service via the venue registry.
        val importance = eventImportanceService.score(
            title = tm.name ?: "",
            category = category,
            venueName = venueName,
            venueId = venueId,
            attendanceEstimate = null
        )

        val severity = when {
            importance.score >= 75 -> "HIGH"
            importance.score >= EventImportanceService.MAJOR_THRESHOLD -> "MODERATE"
            else -> "LOW"
        }

        val city = venue?.city?.name
        val inSeattleArea = city == null || city.contains("Seattle", ignoreCase = true) ||
            city.contains("Renton", ignoreCase = true) || city.contains("Tacoma", ignoreCase = true)

        val description = buildString {
            append(tm.name ?: "Event")
            append(" at ").append(venueName)
            if (importance.reasons.isNotEmpty()) {
                append(". Impact factors: ").append(importance.reasons.joinToString(", "))
            }
        }.take(3500)

        return ExternalEventDto(
            id = "tm-${tm.id ?: "${venueId}-${startAt.epochSecond}"}",
            title = tm.name ?: "Untitled event",
            category = category,
            venue = venueName,
            venueId = venueId,
            latitude = if (inSeattleArea) venue?.latitude else null,
            longitude = if (inSeattleArea) venue?.longitude else null,
            startAt = startAt,
            endAt = endAt,
            attendanceEstimate = null,
            importanceScore = importance.score,
            major = importance.major,
            description = description,
            severity = severity,
            expiresAt = (endAt ?: startAt.plus(4, ChronoUnit.HOURS)).plus(12, ChronoUnit.HOURS)
        )
    }

    private fun parseTmDate(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            try {
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(raw, Instant::from)
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 3
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TmEventsResponse(
    @JsonProperty("_embedded") val embedded: TmEmbedded?,
    @JsonProperty("page") val page: TmPage?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TmEmbedded(
    @JsonProperty("events") val events: List<TmEvent>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TmPage(
    @JsonProperty("totalElements") val totalElements: Int?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TmEvent(
    @JsonProperty("id") val id: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("dates") val dates: TmDates?,
    @JsonProperty("classifications") val classifications: List<TmClassification>?,
    @JsonProperty("_embedded") val embedded: TmEventEmbedded?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TmDates(
    @JsonProperty("start") val start: TmDateTime?,
    @JsonProperty("end") val end: TmDateTime?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TmDateTime(
    @JsonProperty("dateTime") val dateTime: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TmClassification(
    @JsonProperty("primaryCategory") val primaryCategory: TmNamed?,
    @JsonProperty("genre") val genre: TmNamed?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TmNamed(
    @JsonProperty("name") val name: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TmEventEmbedded(
    @JsonProperty("venues") val venues: List<TmVenue>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TmVenue(
    @JsonProperty("id") val id: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("latitude") val latitude: Double?,
    @JsonProperty("longitude") val longitude: Double?,
    @JsonProperty("city") val city: TmNamed?
)
