package com.example.newworkspace.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API DTOs mirroring the Seattle Pulse backend contract EXACTLY as it exists:
 * camelCase JSON, ISO-8601 timestamp strings, separate endpoints per category.
 *
 * These types are the network boundary only. Never expose them outside the
 * data layer; map them to domain models via network/mapper.
 */

@Serializable
data class SourceResponseDto(
    val provider: String,
    val endpoint: String? = null,
    val fetchedAt: String? = null
)

@Serializable
data class GeoPointResponseDto(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null
)

@Serializable
data class ForecastPointResponseDto(
    val time: String,
    val temperatureCelsius: Double,
    val rainProbabilityPercent: Int,
    val condition: String
)

@Serializable
data class WeatherResponseDto(
    val id: String,
    val observedAt: String,
    val updatedAt: String,
    val expiresAt: String? = null,
    val location: GeoPointResponseDto,
    val temperatureCelsius: Double,
    val rainProbabilityPercent: Int,
    val windSpeedKmh: Double,
    val condition: String,
    val forecastSummary: String,
    val forecastHighCelsius: Double,
    val forecastLowCelsius: Double,
    val forecastPeakRainProbabilityPercent: Int,
    val forecast: List<ForecastPointResponseDto> = emptyList(),
    val description: String,
    val severity: String,
    val source: SourceResponseDto
)

@Serializable
data class TransitResponseDto(
    val id: String,
    val serviceType: String,
    val affectedLine: String,
    val affectedArea: String? = null,
    val status: String,
    val description: String,
    val severity: String,
    val startTime: String? = null,
    val endTime: String? = null,
    val updatedAt: String,
    val expiresAt: String? = null,
    val source: SourceResponseDto
)

@Serializable
data class SportsResponseDto(
    val id: String,
    val team: String,
    val opponent: String,
    val startAt: String,
    val venue: String,
    val homeAway: String,
    val eventStatus: String,
    val inSeattle: Boolean,
    val description: String,
    val severity: String,
    val updatedAt: String,
    val expiresAt: String? = null,
    val source: SourceResponseDto
)

@Serializable
data class EventResponseDto(
    val id: String,
    val title: String,
    val category: String,
    val venue: String,
    val venueId: String? = null,
    val location: GeoPointResponseDto? = null,
    val startAt: String,
    val endAt: String? = null,
    val attendanceEstimate: Int? = null,
    val importanceScore: Int,
    val major: Boolean,
    val description: String,
    val severity: String,
    val updatedAt: String,
    val expiresAt: String? = null,
    val source: SourceResponseDto
)

@Serializable
data class TrafficResponseDto(
    val id: String,
    val title: String,
    val description: String,
    val severity: String,
    val location: GeoPointResponseDto,
    val lanesImpacted: Int,
    val updatedAt: String,
    val expiresAt: String? = null,
    val source: SourceResponseDto
)

@Serializable
data class ImpactResponseDto(
    val value: Int,
    val level: String,
    val weatherContribution: Int,
    val transitContribution: Int,
    val sportsContribution: Int,
    val eventsContribution: Int,
    val trafficContribution: Int,
    val factors: List<String> = emptyList(),
    val generatedAt: String,
    val explanation: String
)

@Serializable
data class DashboardResponseDto(
    val weather: List<WeatherResponseDto> = emptyList(),
    val transit: List<TransitResponseDto> = emptyList(),
    val sports: List<SportsResponseDto> = emptyList(),
    val events: List<EventResponseDto> = emptyList(),
    val traffic: List<TrafficResponseDto> = emptyList(),
    val tonight: List<SportsResponseDto> = emptyList(),
    val impact: ImpactResponseDto,
    val generatedAt: String
)

@Serializable
data class AlertResponseDto(
    val id: String,
    val category: String,
    val title: String,
    val message: String,
    val severity: String,
    val createdAt: String,
    val expiresAt: String? = null
)
