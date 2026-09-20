package com.seattlepulse.backend.domain.model

import java.time.Instant

enum class Severity {
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}

enum class TransitServiceType {
    LINK,
    METRO,
    OTHER
}

/**
 * Seattle transportation impact bands (Phase 10):
 * 0-24 LOW, 25-49 MODERATE, 50-74 HIGH, 75-100 SEVERE.
 */
enum class ImpactLevel {
    LOW,
    MODERATE,
    HIGH,
    SEVERE;

    companion object {
        fun fromScore(value: Int): ImpactLevel = when {
            value >= 75 -> SEVERE
            value >= 50 -> HIGH
            value >= 25 -> MODERATE
            else -> LOW
        }
    }
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null
)

data class DataSource(
    val provider: String,
    val endpoint: String? = null,
    val fetchedAt: Instant = Instant.now()
)

data class ForecastPoint(
    val time: Instant,
    val temperatureCelsius: Double,
    val rainProbabilityPercent: Int,
    val condition: String
)

data class WeatherRecord(
    val id: String,
    val observedAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val location: GeoPoint,
    val temperatureCelsius: Double,
    val rainProbabilityPercent: Int,
    val windSpeedKmh: Double,
    val condition: String,
    val forecastSummary: String,
    val forecastHighCelsius: Double,
    val forecastLowCelsius: Double,
    val forecastPeakRainProbabilityPercent: Int,
    val forecast: List<ForecastPoint>,
    val description: String,
    val severity: Severity,
    val source: DataSource
)

data class TransitAlert(
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
    val source: DataSource
)

data class SportsRecord(
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
    val source: DataSource
)

data class EventRecord(
    val id: String,
    val title: String,
    val category: String,
    val venue: String,
    val venueId: String?,
    val location: GeoPoint?,
    val startAt: Instant,
    val endAt: Instant?,
    val attendanceEstimate: Int?,
    val importanceScore: Int,
    val major: Boolean,
    val description: String,
    val severity: Severity,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val source: DataSource
)

data class TrafficRecord(
    val id: String,
    val title: String,
    val description: String,
    val severity: Severity,
    val location: GeoPoint,
    val lanesImpacted: Int,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val source: DataSource
)

data class ImpactScore(
    val value: Int,
    val level: ImpactLevel,
    val weatherContribution: Int,
    val transitContribution: Int,
    val sportsContribution: Int,
    val eventsContribution: Int,
    val trafficContribution: Int,
    val factors: List<String> = emptyList(),
    val generatedAt: Instant,
    val explanation: String
)

data class Dashboard(
    val weather: List<WeatherRecord>,
    val transit: List<TransitAlert>,
    val sports: List<SportsRecord>,
    val events: List<EventRecord>,
    val traffic: List<TrafficRecord>,
    val tonight: List<SportsRecord>,
    val impact: ImpactScore,
    val generatedAt: Instant
)

data class AlertRecord(
    val id: String,
    val category: String,
    val title: String,
    val message: String,
    val severity: Severity,
    val createdAt: Instant,
    val expiresAt: Instant?
)
