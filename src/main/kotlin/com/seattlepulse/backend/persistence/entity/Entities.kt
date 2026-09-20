package com.seattlepulse.backend.persistence.entity

import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.domain.model.TransitServiceType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "weather_records")
data class WeatherEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val externalId: String,
    val observedAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String?,
    val temperatureCelsius: Double,
    val rainProbabilityPercent: Int,
    val windSpeedKmh: Double,
    val condition: String,
    val forecastSummary: String,
    val forecastHighCelsius: Double,
    val forecastLowCelsius: Double,
    val forecastPeakRainProbabilityPercent: Int,
    val description: String,
    @Enumerated(EnumType.STRING)
    val severity: Severity,
    val sourceProvider: String,
    val sourceEndpoint: String?
)

@Entity
@Table(name = "transit_records")
data class TransitEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val externalId: String,
    @Enumerated(EnumType.STRING)
    val serviceType: TransitServiceType? = TransitServiceType.OTHER,
    val affectedLine: String,
    val affectedArea: String?,
    val status: String,
    @Column(length = 4000)
    val description: String,
    @Enumerated(EnumType.STRING)
    val severity: Severity,
    val startTime: Instant?,
    val endTime: Instant?,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val sourceProvider: String,
    val sourceEndpoint: String?,
    val sourceUrl: String?
)

@Entity
@Table(name = "sports_records")
data class SportsEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val externalId: String,
    val team: String,
    val opponent: String,
    val startAt: Instant,
    val venue: String,
    val homeAway: String = "AWAY",
    val eventStatus: String = "Scheduled",
    val inSeattle: Boolean = false,
    val description: String,
    @Enumerated(EnumType.STRING)
    val severity: Severity,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val sourceProvider: String,
    val sourceEndpoint: String?
)

@Entity
@Table(name = "event_records")
data class EventEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val externalId: String,
    val title: String,
    val category: String,
    val venue: String,
    val venueId: String?,
    val latitude: Double?,
    val longitude: Double?,
    val startAt: Instant,
    val endAt: Instant?,
    val attendanceEstimate: Int?,
    val importanceScore: Int = 0,
    val major: Boolean = false,
    val description: String,
    @Enumerated(EnumType.STRING)
    val severity: Severity,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val sourceProvider: String,
    val sourceEndpoint: String?
)

@Entity
@Table(name = "traffic_records")
data class TrafficEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val externalId: String,
    val title: String,
    val description: String,
    @Enumerated(EnumType.STRING)
    val severity: Severity,
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String?,
    val lanesImpacted: Int,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val sourceProvider: String,
    val sourceEndpoint: String?
)

@Entity
@Table(name = "alert_records")
data class AlertEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(unique = true, nullable = false)
    val externalId: String,
    val category: String,
    @Column(length = 500)
    val title: String,
    @Column(length = 4000)
    val message: String,
    @Enumerated(EnumType.STRING)
    val severity: Severity,
    val createdAt: Instant,
    val expiresAt: Instant?
)

@Entity
@Table(name = "device_tokens")
data class DeviceTokenEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(unique = true, nullable = false)
    val token: String,
    val platform: String,
    val createdAt: Instant,
    val enabled: Boolean = true
)
