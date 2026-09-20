package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.DataSource
import com.seattlepulse.backend.domain.model.EventRecord
import com.seattlepulse.backend.domain.model.ForecastPoint
import com.seattlepulse.backend.domain.model.GeoPoint
import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.domain.model.SportsRecord
import com.seattlepulse.backend.domain.model.TrafficRecord
import com.seattlepulse.backend.domain.model.TransitAlert
import com.seattlepulse.backend.domain.model.TransitServiceType
import com.seattlepulse.backend.domain.model.WeatherRecord
import com.seattlepulse.backend.integration.dto.ExternalEventDto
import com.seattlepulse.backend.integration.dto.ExternalSportsDto
import com.seattlepulse.backend.integration.dto.ExternalTrafficDto
import com.seattlepulse.backend.integration.dto.ExternalTransitDto
import com.seattlepulse.backend.integration.dto.ExternalWeatherDto
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class NormalizationService {

    fun toWeather(dto: ExternalWeatherDto, sourceProvider: String, sourceEndpoint: String?): WeatherRecord {
        val now = Instant.now()
        val forecast = dto.forecast.map {
            ForecastPoint(
                time = it.time,
                temperatureCelsius = it.temperatureCelsius,
                rainProbabilityPercent = it.rainProbabilityPercent.coerceIn(0, 100),
                condition = it.condition
            )
        }

        val forecastHigh = forecast.maxOfOrNull { it.temperatureCelsius } ?: dto.temperatureCelsius
        val forecastLow = forecast.minOfOrNull { it.temperatureCelsius } ?: dto.temperatureCelsius
        val forecastPeakRain = forecast.maxOfOrNull { it.rainProbabilityPercent } ?: dto.rainProbabilityPercent

        return WeatherRecord(
            id = dto.id,
            observedAt = dto.observedAt,
            updatedAt = now,
            expiresAt = now.plusSeconds(3600),
            location = GeoPoint(47.6062, -122.3321, "Seattle"),
            temperatureCelsius = dto.temperatureCelsius,
            rainProbabilityPercent = dto.rainProbabilityPercent.coerceIn(0, 100),
            windSpeedKmh = dto.windSpeedKmh,
            condition = dto.condition,
            forecastSummary = dto.forecastSummary,
            forecastHighCelsius = forecastHigh,
            forecastLowCelsius = forecastLow,
            forecastPeakRainProbabilityPercent = forecastPeakRain,
            forecast = forecast,
            description = dto.description,
            severity = weatherSeverity(dto),
            source = DataSource(sourceProvider, sourceEndpoint)
        )
    }

    fun toTransit(dto: ExternalTransitDto, sourceProvider: String, sourceEndpoint: String?): TransitAlert {
        return TransitAlert(
            id = dto.id,
            serviceType = dto.serviceType.toServiceType(),
            affectedLine = dto.affectedLine,
            affectedArea = dto.affectedArea,
            status = dto.status,
            description = dto.description,
            severity = dto.severity.toSeverity(),
            startTime = dto.startTime,
            endTime = dto.endTime,
            updatedAt = Instant.now(),
            expiresAt = dto.expiresAt,
            source = DataSource(sourceProvider, dto.sourceUrl ?: sourceEndpoint)
        )
    }

    fun toSports(dto: ExternalSportsDto, sourceProvider: String, sourceEndpoint: String?): SportsRecord {
        return SportsRecord(
            id = dto.id,
            team = dto.team,
            opponent = dto.opponent,
            startAt = dto.startAt,
            venue = dto.venue,
            homeAway = dto.homeAway,
            eventStatus = dto.eventStatus,
            inSeattle = dto.inSeattle,
            description = dto.description,
            severity = dto.severity.toSeverity(),
            updatedAt = Instant.now(),
            expiresAt = dto.expiresAt,
            source = DataSource(sourceProvider, sourceEndpoint)
        )
    }

    fun toEvent(dto: ExternalEventDto, sourceProvider: String, sourceEndpoint: String?): EventRecord {
        return EventRecord(
            id = dto.id,
            title = dto.title,
            category = dto.category,
            venue = dto.venue,
            venueId = dto.venueId,
            location = if (dto.latitude != null && dto.longitude != null) {
                GeoPoint(dto.latitude, dto.longitude, dto.venue)
            } else {
                null
            },
            startAt = dto.startAt,
            endAt = dto.endAt,
            attendanceEstimate = dto.attendanceEstimate,
            importanceScore = dto.importanceScore,
            major = dto.major,
            description = dto.description,
            severity = dto.severity.toSeverity(),
            updatedAt = Instant.now(),
            expiresAt = dto.expiresAt,
            source = DataSource(sourceProvider, sourceEndpoint)
        )
    }

    fun toTraffic(dto: ExternalTrafficDto, sourceProvider: String, sourceEndpoint: String?): TrafficRecord {
        return TrafficRecord(
            id = dto.id,
            title = dto.title,
            description = dto.description,
            severity = dto.severity.toSeverity(),
            location = GeoPoint(dto.latitude, dto.longitude, dto.locationLabel),
            lanesImpacted = dto.lanesImpacted.coerceAtLeast(0),
            updatedAt = Instant.now(),
            expiresAt = dto.expiresAt,
            source = DataSource(sourceProvider, sourceEndpoint)
        )
    }

    private fun weatherSeverity(dto: ExternalWeatherDto): Severity {
        val forecastPeakRain = dto.forecast.maxOfOrNull { it.rainProbabilityPercent } ?: dto.rainProbabilityPercent
        if (dto.condition.contains("Thunderstorm", ignoreCase = true)) return Severity.CRITICAL
        if (forecastPeakRain >= 75 || dto.windSpeedKmh >= 40) return Severity.HIGH
        if (forecastPeakRain >= 40 || dto.windSpeedKmh >= 25) return Severity.MODERATE
        return Severity.LOW
    }

    private fun String.toSeverity(): Severity = when (trim().uppercase()) {
        "LOW" -> Severity.LOW
        "MODERATE" -> Severity.MODERATE
        "HIGH" -> Severity.HIGH
        "CRITICAL" -> Severity.CRITICAL
        else -> Severity.MODERATE
    }

    private fun String.toServiceType(): TransitServiceType = when (trim().uppercase()) {
        "LINK" -> TransitServiceType.LINK
        "METRO" -> TransitServiceType.METRO
        else -> TransitServiceType.OTHER
    }
}
