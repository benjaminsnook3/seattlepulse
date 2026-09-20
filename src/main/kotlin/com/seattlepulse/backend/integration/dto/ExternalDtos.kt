package com.seattlepulse.backend.integration.dto

import java.time.Instant

data class ExternalWeatherForecastDto(
    val time: Instant,
    val temperatureCelsius: Double,
    val rainProbabilityPercent: Int,
    val condition: String
)

data class ExternalWeatherDto(
    val id: String,
    val observedAt: Instant,
    val temperatureCelsius: Double,
    val windSpeedKmh: Double,
    val rainProbabilityPercent: Int,
    val condition: String,
    val forecastSummary: String,
    val forecast: List<ExternalWeatherForecastDto>,
    val description: String
)

data class ExternalTransitDto(
    val id: String,
    val serviceType: String = "OTHER",
    val affectedLine: String,
    val affectedArea: String?,
    val status: String,
    val description: String,
    val severity: String,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val sourceUrl: String? = null,
    val expiresAt: Instant? = null
)

data class ExternalSportsDto(
    val id: String,
    val team: String,
    val opponent: String,
    val startAt: Instant,
    val venue: String,
    val homeAway: String = "AWAY",
    val eventStatus: String = "Scheduled",
    val inSeattle: Boolean = false,
    val description: String,
    val severity: String,
    val expiresAt: Instant? = null
)

data class ExternalEventDto(
    val id: String,
    val title: String,
    val category: String,
    val venue: String,
    val venueId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startAt: Instant,
    val endAt: Instant? = null,
    val attendanceEstimate: Int? = null,
    val importanceScore: Int = 0,
    val major: Boolean = false,
    val description: String,
    val severity: String,
    val expiresAt: Instant? = null
)

data class ExternalTrafficDto(
    val id: String,
    val title: String,
    val description: String,
    val severity: String,
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String? = null,
    val lanesImpacted: Int,
    val expiresAt: Instant? = null
)
