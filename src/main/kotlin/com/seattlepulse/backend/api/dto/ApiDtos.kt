package com.seattlepulse.backend.api.dto

import com.seattlepulse.backend.domain.model.ImpactLevel
import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.domain.model.TransitServiceType
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class GeoPointResponse(
    val latitude: Double,
    val longitude: Double,
    val label: String?
)

data class SourceResponse(
    val provider: String,
    val endpoint: String?,
    val fetchedAt: Instant
)

data class ForecastPointResponse(
    val time: Instant,
    val temperatureCelsius: Double,
    val rainProbabilityPercent: Int,
    val condition: String
)

data class WeatherResponse(
    val id: String,
    val observedAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val location: GeoPointResponse,
    val temperatureCelsius: Double,
    val rainProbabilityPercent: Int,
    val windSpeedKmh: Double,
    val condition: String,
    val forecastSummary: String,
    val forecastHighCelsius: Double,
    val forecastLowCelsius: Double,
    val forecastPeakRainProbabilityPercent: Int,
    val forecast: List<ForecastPointResponse>,
    val description: String,
    val severity: Severity,
    val source: SourceResponse
)

data class TransitResponse(
    val id: String,
    val serviceType: TransitServiceType,
    val affectedLine: String,
    val affectedArea: String?,
    val status: String,
    val description: String,
    val severity: Severity,
    val startTime: Instant?,
    val endTime: Instant?,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val source: SourceResponse
)

data class SportsResponse(
    val id: String,
    val team: String,
    val opponent: String,
    val startAt: Instant,
    val venue: String,
    val homeAway: String,
    val eventStatus: String,
    val inSeattle: Boolean,
    val description: String,
    val severity: Severity,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val source: SourceResponse
)

data class EventResponse(
    val id: String,
    val title: String,
    val category: String,
    val venue: String,
    val venueId: String?,
    val location: GeoPointResponse?,
    val startAt: Instant,
    val endAt: Instant?,
    val attendanceEstimate: Int?,
    val importanceScore: Int,
    val major: Boolean,
    val description: String,
    val severity: Severity,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val source: SourceResponse
)

data class TrafficResponse(
    val id: String,
    val title: String,
    val description: String,
    val severity: Severity,
    val location: GeoPointResponse,
    val lanesImpacted: Int,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val source: SourceResponse
)

data class ImpactResponse(
    val value: Int,
    val level: ImpactLevel,
    val weatherContribution: Int,
    val transitContribution: Int,
    val sportsContribution: Int,
    val eventsContribution: Int,
    val trafficContribution: Int,
    val factors: List<String>,
    val generatedAt: Instant,
    val explanation: String
)

data class AlertResponse(
    val id: String,
    val category: String,
    val title: String,
    val message: String,
    val severity: Severity,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val detailsUrl: String? = null
)

data class DashboardResponse(
    val weather: List<WeatherResponse>,
    val transit: List<TransitResponse>,
    val sports: List<SportsResponse>,
    val events: List<EventResponse>,
    val traffic: List<TrafficResponse>,
    val tonight: List<SportsResponse>,
    val impact: ImpactResponse,
    val generatedAt: Instant
)

data class RegisterAlertDeviceRequest(
    @field:NotBlank
    val token: String,
    @field:NotBlank
    val platform: String
)

data class ApiMessage(
    val message: String
)

data class AreaResponse(
    val neighborhood: String,
    val center: GeoPointResponse,
    val radiusKm: Double,
    val linkStatus: String,
    val metroStatus: String,
    val nearbyEvents: List<EventResponse>,
    val nearbyTraffic: List<TrafficResponse>,
    val relevantTransit: List<TransitResponse>,
    val rainProbabilityPercent: Int?,
    val condition: String?
)
