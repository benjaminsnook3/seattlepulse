package com.seattlepulse.backend.integration.provider

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.seattlepulse.backend.integration.dto.ExternalEventDto
import com.seattlepulse.backend.integration.dto.ExternalSportsDto
import com.seattlepulse.backend.integration.dto.ExternalTrafficDto
import com.seattlepulse.backend.integration.dto.ExternalTransitDto
import com.seattlepulse.backend.integration.dto.ExternalWeatherDto
import com.seattlepulse.backend.integration.dto.ExternalWeatherForecastDto
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

interface WeatherProvider {
    fun fetch(): ExternalWeatherDto?
}

interface TransitProvider {
    val providerName: String
    fun fetch(): List<ExternalTransitDto>
}

interface SportsProvider {
    val providerName: String
    fun fetch(): List<ExternalSportsDto>
}

interface EventsProvider {
    val providerName: String
    fun fetch(): List<ExternalEventDto>
}

interface TrafficProvider {
    fun fetch(): List<ExternalTrafficDto>
}

@Component
class OpenMeteoWeatherProvider(
    @Value("\${seattle.weather.latitude}") private val latitude: Double,
    @Value("\${seattle.weather.longitude}") private val longitude: Double
) : WeatherProvider {

    private val restClient = RestClient.builder().baseUrl("https://api.open-meteo.com").build()

    override fun fetch(): ExternalWeatherDto? {
        val response = restClient.get()
            .uri { builder ->
                builder.path("/v1/forecast")
                    .queryParam("latitude", latitude)
                    .queryParam("longitude", longitude)
                    .queryParam("current", "temperature_2m,wind_speed_10m,weather_code")
                    .queryParam("hourly", "temperature_2m,precipitation_probability,weather_code")
                    .queryParam("forecast_days", 1)
                    .build()
            }
            .retrieve()
            .body(OpenMeteoResponse::class.java) ?: return null

        val code = response.current?.weatherCode ?: -1
        val hourlyTimes = response.hourly?.time.orEmpty()
        val hourlyTemps = response.hourly?.temperature.orEmpty()
        val hourlyRain = response.hourly?.precipitationProbability.orEmpty()
        val hourlyCodes = response.hourly?.weatherCode.orEmpty()

        val horizon = listOf(hourlyTimes.size, hourlyTemps.size, hourlyRain.size, hourlyCodes.size).minOrNull() ?: 0
        val forecastPoints = (0 until horizon)
            .take(12)
            .map { index ->
                ExternalWeatherForecastDto(
                    time = parseProviderTime(hourlyTimes[index]),
                    temperatureCelsius = hourlyTemps[index],
                    rainProbabilityPercent = hourlyRain[index],
                    condition = weatherCodeToCondition(hourlyCodes[index])
                )
            }

        val peakRain = forecastPoints.maxOfOrNull { it.rainProbabilityPercent } ?: 0
        val peakWind = response.current?.windSpeed ?: 0.0
        val summary = when {
            peakRain >= 70 -> "Rain likely in next 12 hours"
            peakWind >= 35 -> "Windy conditions expected"
            else -> "Stable conditions expected"
        }

        return ExternalWeatherDto(
            id = "weather-${Instant.now().truncatedTo(ChronoUnit.HOURS)}",
            observedAt = parseProviderTime(response.current?.time ?: Instant.now().toString()),
            temperatureCelsius = response.current?.temperature ?: 0.0,
            windSpeedKmh = response.current?.windSpeed ?: 0.0,
            rainProbabilityPercent = response.hourly?.precipitationProbability?.firstOrNull() ?: 0,
            condition = weatherCodeToCondition(code),
            forecastSummary = summary,
            forecast = forecastPoints,
            description = "Live weather from Open-Meteo"
        )
    }

    private fun weatherCodeToCondition(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2, 3 -> "Cloudy"
        in 45..48 -> "Fog"
        in 51..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        in 95..99 -> "Thunderstorm"
        else -> "Unknown"
    }

    private fun parseProviderTime(raw: String): Instant {
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC)
        }
    }
}

@Component
class SoundTransitLinkAlertsProvider(
    @Value("\${seattle.transit.soundtransit-alerts-url:https://www.soundtransit.org/ride-with-us/service-alerts?view=ongoing}")
    private val alertsUrl: String
) : TransitProvider {

    override val providerName = "SoundTransit"

    override fun fetch(): List<ExternalTransitDto> {
        val resolvedAlertsUrl = if (alertsUrl.contains("view=")) alertsUrl else "$alertsUrl?view=ongoing"
        val doc = Jsoup.connect(resolvedAlertsUrl)
            .userAgent("SeattlePulseBackend/1.0")
            .timeout(15000)
            .get()

        val baseUrl = "https://www.soundtransit.org"

        val now = Instant.now()
        val alerts = mutableListOf<ExternalTransitDto>()
        val routeRows = doc.select("#block-views-block-service-alerts-link-light-rail-block .route-station-row")

        routeRows.forEach { row ->
            val rowLineLabel = row.selectFirst(".service-alert-accordion-header .sr-only")?.text()?.trim().orEmpty()
            val lineText = Regex("([0-9A-Za-z]+ Line)").find(rowLineLabel)?.groupValues?.get(1)
                ?: row.selectFirst(".route-station-row-title")?.text()?.trim().orEmpty()
            if (!lineText.contains("Line", ignoreCase = true)) {
                return@forEach
            }

            row.select(".text-cards__service-alert").forEach { alertCard ->
                val headerText = alertCard.selectFirst(".alert--header")?.text()?.trim().orEmpty()
                val mainText = alertCard.selectFirst(".alert__body--main")?.text()?.trim().orEmpty()
                val bodyText = alertCard.selectFirst(".acc__panel.alert__body--section")?.text()?.trim().orEmpty()
                val description = listOf(headerText, mainText, bodyText)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" ")
                    .trim()
                    .take(3500)
                if (description.isBlank()) {
                    return@forEach
                }

                val cardLine = Regex("([0-9A-Za-z]+ Line(?:\\s*&\\s*[0-9A-Za-z]+ Line)?)")
                    .find(mainText)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                val affectedLine = cardLine ?: lineText

                val statusText = alertCard.select(".alert--label").eachText()
                    .firstOrNull { it.equals("Ongoing", ignoreCase = true) || it.equals("Upcoming", ignoreCase = true) }
                    ?: "Ongoing"

                if (!statusText.equals("Ongoing", ignoreCase = true)) {
                    return@forEach
                }

                val link = alertCard.selectFirst("a.copy-to-clipboard")?.attr("href")
                val sourceUrl = when {
                    link.isNullOrBlank() -> "$baseUrl/ride-with-us/service-alerts"
                    link.startsWith("http") -> link
                    else -> "$baseUrl$link"
                }

                val activeWindow = parseActiveWindow(description, now)
                val endTime = activeWindow.second ?: if (statusText.equals("Ongoing", ignoreCase = true)) now.plus(8, ChronoUnit.HOURS) else null

                alerts += ExternalTransitDto(
                    id = "st-link-${sourceUrl.hashCode()}-${lineText.hashCode()}",
                    serviceType = "LINK",
                    affectedLine = affectedLine,
                    affectedArea = inferArea(description),
                    status = statusText,
                    description = description,
                    severity = inferSeverity(description, statusText),
                    startTime = activeWindow.first,
                    endTime = endTime,
                    sourceUrl = sourceUrl,
                    expiresAt = endTime
                )
            }
        }

        return alerts
    }

    private fun inferSeverity(description: String, status: String): String {
        val text = description.lowercase(Locale.US)
        return when {
            text.contains("suspended") || text.contains("cancel") || text.contains("no service") -> "CRITICAL"
            text.contains("delay") || text.contains("reduced") || text.contains("maintenance") -> "HIGH"
            status.equals("Upcoming", ignoreCase = true) -> "MODERATE"
            else -> "LOW"
        }
    }

    private fun inferArea(description: String): String? {
        val stationMatch = Regex("([A-Z][A-Za-z0-9\\- ]+ Station)").find(description)
        if (stationMatch != null) {
            return stationMatch.value.trim()
        }

        val corridorMatch = Regex("(between [A-Z][A-Za-z0-9\\- ]+ and [A-Z][A-Za-z0-9\\- ]+)", RegexOption.IGNORE_CASE).find(description)
        return corridorMatch?.value?.trim()
    }

    private fun parseActiveWindow(description: String, now: Instant): Pair<Instant?, Instant?> {
        val rangePattern = Regex("from\\s+([A-Za-z]{3,9}\\.?\\s+\\d{1,2})\\s+through\\s+([A-Za-z]{3,9}\\.?\\s+\\d{1,2})", RegexOption.IGNORE_CASE)
        val match = rangePattern.find(description)
        if (match != null) {
            val start = parseMonthDay(match.groupValues[1], now)
            val end = parseMonthDay(match.groupValues[2], now)?.plus(1, ChronoUnit.DAYS)
            return Pair(start, end)
        }

        val inEffectPattern = Regex("in effect:\\s*([A-Za-z]{3,9}\\.?\\s*\\d{1,2})\\s*-\\s*([A-Za-z]{3,9}\\.?\\s*\\d{1,2})", RegexOption.IGNORE_CASE)
        val inEffect = inEffectPattern.find(description)
        if (inEffect != null) {
            val start = parseMonthDay(inEffect.groupValues[1], now)
            val end = parseMonthDay(inEffect.groupValues[2], now)?.plus(1, ChronoUnit.DAYS)
            return Pair(start, end)
        }

        val tonightPattern = Regex("tonight.*from\\s+(\\d{1,2}(:\\d{2})?\\s*(a\\.m\\.|p\\.m\\.|am|pm))", RegexOption.IGNORE_CASE)
        val tonight = tonightPattern.find(description)
        if (tonight != null) {
            val start = parseHourMinute(tonight.groupValues[1], now)
            return Pair(start, start?.plus(6, ChronoUnit.HOURS))
        }

        return Pair(null, null)
    }

    private fun parseMonthDay(value: String, now: Instant): Instant? {
        val year = LocalDateTime.ofInstant(now, ZoneOffset.UTC).year
        val normalized = value.replace(".", "").replace("Sept", "Sep").trim()
        val candidate = "$normalized $year"
        val patterns = listOf("MMM d yyyy", "MMMM d yyyy")

        for (pattern in patterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
                val date = LocalDate.parse(candidate, formatter)
                return date.atStartOfDay().toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                // Try next format.
            }
        }

        return null
    }

    private fun parseHourMinute(value: String, now: Instant): Instant? {
        val normalized = value.replace("a.m.", "AM").replace("p.m.", "PM").replace("am", "AM").replace("pm", "PM")
        val patterns = listOf("h:mm a", "h a")
        for (pattern in patterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
                val time = java.time.LocalTime.parse(normalized, formatter)
                val date = LocalDateTime.ofInstant(now, ZoneOffset.UTC).toLocalDate()
                return LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                // Try next pattern.
            }
        }
        return null
    }
}

@Component
class MockTrafficProvider : TrafficProvider {
    override fun fetch(): List<ExternalTrafficDto> {
        val now = Instant.now()
        return listOf(
            ExternalTrafficDto(
                id = "traffic-i5-collision",
                title = "I-5 Collision",
                description = "Multi-car collision affecting two lanes",
                severity = "CRITICAL",
                latitude = 47.6205,
                longitude = -122.3493,
                locationLabel = "I-5 Southbound",
                lanesImpacted = 2,
                expiresAt = now.plus(90, ChronoUnit.MINUTES)
            )
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenMeteoResponse(
    @JsonProperty("current") val current: OpenMeteoCurrent?,
    @JsonProperty("hourly") val hourly: OpenMeteoHourly?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenMeteoCurrent(
    @JsonProperty("time") val time: String?,
    @JsonProperty("temperature_2m") val temperature: Double?,
    @JsonProperty("wind_speed_10m") val windSpeed: Double?,
    @JsonProperty("weather_code") val weatherCode: Int?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenMeteoHourly(
    @JsonProperty("time") val time: List<String>?,
    @JsonProperty("temperature_2m") val temperature: List<Double>?,
    @JsonProperty("precipitation_probability") val precipitationProbability: List<Int>?,
    @JsonProperty("weather_code") val weatherCode: List<Int>?
)
