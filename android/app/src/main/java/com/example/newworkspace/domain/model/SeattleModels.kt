package com.example.newworkspace.domain.model

import java.time.Instant

/**
 * Clean domain models. These know nothing about JSON, Retrofit, the backend's
 * naming conventions, or API response formats. Timestamps are [Instant].
 */

enum class Severity {
    LOW,
    MODERATE,
    HIGH,
    SEVERE,
    UNKNOWN;

    companion object {
        /**
         * Maps backend values onto the single domain scale. Record severity uses
         * CRITICAL for the top band while the impact engine uses SEVERE; both map
         * to [Severity.SEVERE].
         */
        fun fromApi(raw: String?): Severity = when (raw?.trim()?.uppercase()) {
            "LOW" -> LOW
            "MODERATE" -> MODERATE
            "HIGH" -> HIGH
            "CRITICAL", "SEVERE" -> SEVERE
            else -> UNKNOWN
        }
    }
}

enum class TransitServiceType {
    LINK,
    METRO,
    OTHER;

    companion object {
        fun fromApi(raw: String?): TransitServiceType = when (raw?.trim()?.uppercase()) {
            "LINK" -> LINK
            "METRO" -> METRO
            else -> OTHER
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
    val fetchedAt: Instant? = null
)

data class Venue(
    val id: String?,
    val name: String,
    val location: GeoPoint? = null
)

data class Weather(
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

data class SportsEvent(
    val id: String,
    val team: String,
    val opponent: String,
    val startAt: Instant,
    val venue: Venue,
    val homeAway: String,
    val eventStatus: String,
    val inSeattle: Boolean,
    val description: String,
    val severity: Severity,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val source: DataSource
)

data class PublicEvent(
    val id: String,
    val title: String,
    val category: String,
    val venue: Venue,
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

data class TrafficIncident(
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
    val level: Severity,
    val weatherContribution: Int,
    val transitContribution: Int,
    val sportsContribution: Int,
    val eventsContribution: Int,
    val trafficContribution: Int,
    val factors: List<String>,
    val generatedAt: Instant,
    val explanation: String
)

/**
 * Application-level aggregate assembled from the individual data sources by
 * GetSeattleDaySummaryUseCase. A section is null/empty when its source failed,
 * so a partial failure still renders the rest.
 */
data class SeattleDaySummary(
    val weather: Weather?,
    val transitAlerts: List<TransitAlert>,
    val sportsEvents: List<SportsEvent>,
    val publicEvents: List<PublicEvent>,
    val trafficIncidents: List<TrafficIncident>,
    val impact: ImpactScore?,
    val generatedAt: Instant
)
