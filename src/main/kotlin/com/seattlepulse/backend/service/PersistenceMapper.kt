package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.AlertRecord
import com.seattlepulse.backend.domain.model.DataSource
import com.seattlepulse.backend.domain.model.EventRecord
import com.seattlepulse.backend.domain.model.GeoPoint
import com.seattlepulse.backend.domain.model.SportsRecord
import com.seattlepulse.backend.domain.model.TrafficRecord
import com.seattlepulse.backend.domain.model.TransitAlert
import com.seattlepulse.backend.domain.model.TransitServiceType
import com.seattlepulse.backend.domain.model.WeatherRecord
import com.seattlepulse.backend.persistence.entity.AlertEntity
import com.seattlepulse.backend.persistence.entity.EventEntity
import com.seattlepulse.backend.persistence.entity.SportsEntity
import com.seattlepulse.backend.persistence.entity.TrafficEntity
import com.seattlepulse.backend.persistence.entity.TransitEntity
import com.seattlepulse.backend.persistence.entity.WeatherEntity
import org.springframework.stereotype.Component

@Component
class PersistenceMapper {

    fun toEntity(record: WeatherRecord): WeatherEntity = WeatherEntity(
        externalId = record.id,
        observedAt = record.observedAt,
        updatedAt = record.updatedAt,
        expiresAt = record.expiresAt,
        latitude = record.location.latitude,
        longitude = record.location.longitude,
        locationLabel = record.location.label,
        temperatureCelsius = record.temperatureCelsius,
        rainProbabilityPercent = record.rainProbabilityPercent,
        windSpeedKmh = record.windSpeedKmh,
        condition = record.condition,
        forecastSummary = record.forecastSummary,
        forecastHighCelsius = record.forecastHighCelsius,
        forecastLowCelsius = record.forecastLowCelsius,
        forecastPeakRainProbabilityPercent = record.forecastPeakRainProbabilityPercent,
        description = record.description,
        severity = record.severity,
        sourceProvider = record.source.provider,
        sourceEndpoint = record.source.endpoint
    )

    fun toEntity(record: TransitAlert): TransitEntity = TransitEntity(
        externalId = record.id,
        serviceType = record.serviceType,
        affectedLine = record.affectedLine,
        affectedArea = record.affectedArea,
        status = record.status,
        description = record.description,
        severity = record.severity,
        startTime = record.startTime,
        endTime = record.endTime,
        updatedAt = record.updatedAt,
        expiresAt = record.expiresAt,
        sourceProvider = record.source.provider,
        sourceEndpoint = record.source.endpoint,
        sourceUrl = record.source.endpoint
    )

    fun toEntity(record: SportsRecord): SportsEntity = SportsEntity(
        externalId = record.id,
        team = record.team,
        opponent = record.opponent,
        startAt = record.startAt,
        venue = record.venue,
        homeAway = record.homeAway,
        eventStatus = record.eventStatus,
        inSeattle = record.inSeattle,
        description = record.description,
        severity = record.severity,
        updatedAt = record.updatedAt,
        expiresAt = record.expiresAt,
        sourceProvider = record.source.provider,
        sourceEndpoint = record.source.endpoint
    )

    fun toEntity(record: EventRecord): EventEntity = EventEntity(
        externalId = record.id,
        title = record.title,
        category = record.category,
        venue = record.venue,
        venueId = record.venueId,
        latitude = record.location?.latitude,
        longitude = record.location?.longitude,
        startAt = record.startAt,
        endAt = record.endAt,
        attendanceEstimate = record.attendanceEstimate,
        importanceScore = record.importanceScore,
        major = record.major,
        description = record.description,
        severity = record.severity,
        updatedAt = record.updatedAt,
        expiresAt = record.expiresAt,
        sourceProvider = record.source.provider,
        sourceEndpoint = record.source.endpoint
    )

    fun toEntity(record: TrafficRecord): TrafficEntity = TrafficEntity(
        externalId = record.id,
        title = record.title,
        description = record.description,
        severity = record.severity,
        latitude = record.location.latitude,
        longitude = record.location.longitude,
        locationLabel = record.location.label,
        lanesImpacted = record.lanesImpacted,
        updatedAt = record.updatedAt,
        expiresAt = record.expiresAt,
        sourceProvider = record.source.provider,
        sourceEndpoint = record.source.endpoint
    )

    fun toEntity(record: AlertRecord): AlertEntity = AlertEntity(
        externalId = record.id,
        category = record.category,
        title = record.title,
        message = record.message,
        severity = record.severity,
        createdAt = record.createdAt,
        expiresAt = record.expiresAt
    )

    fun toDomain(entity: WeatherEntity): WeatherRecord = WeatherRecord(
        id = entity.externalId,
        observedAt = entity.observedAt,
        updatedAt = entity.updatedAt,
        expiresAt = entity.expiresAt,
        location = GeoPoint(entity.latitude, entity.longitude, entity.locationLabel),
        temperatureCelsius = entity.temperatureCelsius,
        rainProbabilityPercent = entity.rainProbabilityPercent,
        windSpeedKmh = entity.windSpeedKmh,
        condition = entity.condition,
        forecastSummary = entity.forecastSummary,
        forecastHighCelsius = entity.forecastHighCelsius,
        forecastLowCelsius = entity.forecastLowCelsius,
        forecastPeakRainProbabilityPercent = entity.forecastPeakRainProbabilityPercent,
        forecast = emptyList(),
        description = entity.description,
        severity = entity.severity,
        source = DataSource(entity.sourceProvider, entity.sourceEndpoint)
    )

    fun toDomain(entity: TransitEntity): TransitAlert = TransitAlert(
        id = entity.externalId,
        serviceType = entity.serviceType ?: TransitServiceType.OTHER,
        affectedLine = entity.affectedLine,
        affectedArea = entity.affectedArea,
        status = entity.status,
        description = entity.description,
        severity = entity.severity,
        startTime = entity.startTime,
        endTime = entity.endTime,
        updatedAt = entity.updatedAt,
        expiresAt = entity.expiresAt,
        source = DataSource(entity.sourceProvider, entity.sourceUrl ?: entity.sourceEndpoint)
    )

    fun toDomain(entity: SportsEntity): SportsRecord = SportsRecord(
        id = entity.externalId,
        team = entity.team,
        opponent = entity.opponent,
        startAt = entity.startAt,
        venue = entity.venue,
        homeAway = entity.homeAway,
        eventStatus = entity.eventStatus,
        inSeattle = entity.inSeattle,
        description = entity.description,
        severity = entity.severity,
        updatedAt = entity.updatedAt,
        expiresAt = entity.expiresAt,
        source = DataSource(entity.sourceProvider, entity.sourceEndpoint)
    )

    fun toDomain(entity: EventEntity): EventRecord = EventRecord(
        id = entity.externalId,
        title = entity.title,
        category = entity.category,
        venue = entity.venue,
        venueId = entity.venueId,
        location = if (entity.latitude != null && entity.longitude != null) {
            GeoPoint(entity.latitude, entity.longitude, entity.venue)
        } else {
            null
        },
        startAt = entity.startAt,
        endAt = entity.endAt,
        attendanceEstimate = entity.attendanceEstimate,
        importanceScore = entity.importanceScore,
        major = entity.major,
        description = entity.description,
        severity = entity.severity,
        updatedAt = entity.updatedAt,
        expiresAt = entity.expiresAt,
        source = DataSource(entity.sourceProvider, entity.sourceEndpoint)
    )

    fun toDomain(entity: TrafficEntity): TrafficRecord = TrafficRecord(
        id = entity.externalId,
        title = entity.title,
        description = entity.description,
        severity = entity.severity,
        location = GeoPoint(entity.latitude, entity.longitude, entity.locationLabel),
        lanesImpacted = entity.lanesImpacted,
        updatedAt = entity.updatedAt,
        expiresAt = entity.expiresAt,
        source = DataSource(entity.sourceProvider, entity.sourceEndpoint)
    )

    fun toDomain(entity: AlertEntity): AlertRecord = AlertRecord(
        id = entity.externalId,
        category = entity.category,
        title = entity.title,
        message = entity.message,
        severity = entity.severity,
        createdAt = entity.createdAt,
        expiresAt = entity.expiresAt
    )
}
