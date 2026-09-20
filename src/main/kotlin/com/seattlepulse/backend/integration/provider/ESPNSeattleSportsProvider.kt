package com.seattlepulse.backend.integration.provider

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.seattlepulse.backend.integration.dto.ExternalSportsDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Fetches upcoming games for Seattle's five major professional teams from the
 * public ESPN team schedule API: Mariners, Seahawks, Kraken, Sounders, Storm.
 */
@Component
class ESPNSeattleSportsProvider(
    @Value("\${seattle.sports.espn-base-url:https://site.web.api.espn.com/apis/site/v2}")
    private val espnBaseUrl: String,
    @Value("\${seattle.sports.lookahead-days:14}")
    private val lookaheadDays: Long
) : SportsProvider {

    override val providerName = "ESPNSeattle"

    private val logger = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.builder().build()

    private data class TeamSpec(
        val sportPath: String,
        val teamId: String,
        val teamName: String,
        val emoji: String
    )

    private val teams = listOf(
        TeamSpec("baseball/mlb", "29", "Mariners", "⚾"),
        TeamSpec("football/nfl", "26", "Seahawks", "🏈"),
        TeamSpec("hockey/nhl", "124292", "Kraken", "🏒"),
        TeamSpec("soccer/usa.1", "9726", "Sounders", "⚽"),
        TeamSpec("basketball/wnba", "14", "Storm", "🏀")
    )

    override fun fetch(): List<ExternalSportsDto> {
        val now = Instant.now()
        val horizon = now.plus(lookaheadDays, ChronoUnit.DAYS)

        return teams.flatMap { spec ->
            try {
                fetchTeam(spec, now, horizon)
            } catch (ex: Exception) {
                logger.warn("Sports fetch failed for {}: {}", spec.teamName, ex.message)
                emptyList()
            }
        }
    }

    private fun fetchTeam(spec: TeamSpec, now: Instant, horizon: Instant): List<ExternalSportsDto> {
        val url = "$espnBaseUrl/sports/${spec.sportPath}/teams/${spec.teamId}/schedule"
        val response = restClient.get()
            .uri(url)
            .retrieve()
            .body(EspnScheduleResponse::class.java) ?: return emptyList()

        return response.events
            .orEmpty()
            .mapNotNull { event -> mapEvent(spec, event, now, horizon) }
    }

    private fun mapEvent(
        spec: TeamSpec,
        event: EspnEvent,
        now: Instant,
        horizon: Instant
    ): ExternalSportsDto? {
        val startAt = parseEspnDate(event.date) ?: return null
        val completed = event.competitions?.firstOrNull()?.status?.type?.completed ?: false
        if (completed || startAt.isBefore(now) || startAt.isAfter(horizon)) {
            return null
        }

        val competition = event.competitions?.firstOrNull() ?: return null
        val competitors = competition.competitors.orEmpty()

        val seattleCompetitor = competitors.firstOrNull {
            it.team?.displayName?.contains(spec.teamName) == true ||
                it.team?.nickname?.contains(spec.teamName) == true
        }
        val opponentCompetitor = competitors.firstOrNull { it !== seattleCompetitor }

        val homeAway = when (seattleCompetitor?.homeAway?.lowercase()) {
            "home" -> "HOME"
            else -> "AWAY"
        }
        val venueName = competition.venue?.fullName ?: "TBD"
        val venueCity = competition.venue?.address?.city
        val inSeattle = venueCity.equals("Seattle", ignoreCase = true) ||
            venueName.contains("T-Mobile Park", ignoreCase = true) ||
            venueName.contains("Lumen Field", ignoreCase = true) ||
            venueName.contains("Climate Pledge Arena", ignoreCase = true) ||
            venueName.contains("KeyArena", ignoreCase = true)

        val opponent = opponentCompetitor?.team?.displayName
            ?: opponentCompetitor?.team?.nickname
            ?: "TBD"

        val statusDescription = competition.status?.type?.description ?: "Scheduled"
        val severity = if (homeAway == "HOME" && inSeattle) "MODERATE" else "LOW"
        val description = "${spec.emoji} ${spec.teamName} vs $opponent ($homeAway) at $venueName"

        return ExternalSportsDto(
            id = "espn-${spec.teamName.lowercase()}-${event.id}",
            team = spec.teamName,
            opponent = opponent,
            startAt = startAt,
            venue = venueName,
            homeAway = homeAway,
            eventStatus = statusDescription,
            inSeattle = inSeattle,
            description = description,
            severity = severity,
            expiresAt = startAt.plus(6, ChronoUnit.HOURS)
        )
    }

    private fun parseEspnDate(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            // ESPN emits minute-precision instants like "2026-09-20T20:25Z"; add seconds if missing.
            val normalized = if (Regex("\\d{2}:\\d{2}Z$").containsMatchIn(raw)) "${raw.dropLast(1)}:00Z" else raw
            Instant.parse(normalized)
        } catch (_: Exception) {
            null
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnScheduleResponse(
    @JsonProperty("events") val events: List<EspnEvent>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnEvent(
    @JsonProperty("id") val id: String?,
    @JsonProperty("date") val date: String?,
    @JsonProperty("competitions") val competitions: List<EspnCompetition>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnCompetition(
    @JsonProperty("venue") val venue: EspnVenue?,
    @JsonProperty("competitors") val competitors: List<EspnCompetitor>?,
    @JsonProperty("status") val status: EspnStatus?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnVenue(
    @JsonProperty("fullName") val fullName: String?,
    @JsonProperty("address") val address: EspnVenueAddress?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnVenueAddress(
    @JsonProperty("city") val city: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnCompetitor(
    @JsonProperty("homeAway") val homeAway: String?,
    @JsonProperty("team") val team: EspnTeam?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnTeam(
    @JsonProperty("displayName") val displayName: String?,
    @JsonProperty("nickname") val nickname: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnStatus(
    @JsonProperty("type") val type: EspnStatusType?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnStatusType(
    @JsonProperty("completed") val completed: Boolean?,
    @JsonProperty("description") val description: String?
)
