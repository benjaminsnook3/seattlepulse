package com.example.newworkspace.network.mapper

import com.example.newworkspace.domain.model.DataSource
import com.example.newworkspace.domain.model.CityAlert
import com.example.newworkspace.domain.model.GeoPoint
import com.example.newworkspace.domain.model.ImpactScore
import com.example.newworkspace.domain.model.PublicEvent
import com.example.newworkspace.domain.model.Severity
import com.example.newworkspace.domain.model.SportsEvent
import com.example.newworkspace.domain.model.TrafficIncident
import com.example.newworkspace.domain.model.TransitAlert
import com.example.newworkspace.domain.model.TransitServiceType
import com.example.newworkspace.domain.model.Venue
import com.example.newworkspace.domain.model.Weather
import com.example.newworkspace.network.api.AlertResponseDto
import com.example.newworkspace.network.api.EventResponseDto
import com.example.newworkspace.network.api.GeoPointResponseDto
import com.example.newworkspace.network.api.ImpactResponseDto
import com.example.newworkspace.network.api.SourceResponseDto
import com.example.newworkspace.network.api.SportsResponseDto
import com.example.newworkspace.network.api.TrafficResponseDto
import com.example.newworkspace.network.api.TransitResponseDto
import com.example.newworkspace.network.api.WeatherResponseDto
import java.time.Instant

/**
 * Explicit Backend DTO -> Domain Model mapping. This is the only layer that
 * knows both the API shape and the domain shape. Timestamp conversion happens
 * here via [TimestampParser].
 */

fun SourceResponseDto.toDomain(): DataSource = DataSource(
    provider = provider,
    endpoint = endpoint,
    fetchedAt = TimestampParser.parseOrNull(fetchedAt)
)

fun GeoPointResponseDto.toDomain(): GeoPoint = GeoPoint(
    latitude = latitude,
    longitude = longitude,
    label = label
)

fun WeatherResponseDto.toDomain(): Weather = Weather(
    id = id,
    observedAt = TimestampParser.parseOr(observedAt, Instant.EPOCH),
    updatedAt = TimestampParser.parseOr(updatedAt, Instant.EPOCH),
    expiresAt = TimestampParser.parseOrNull(expiresAt),
    location = location.toDomain(),
    temperatureCelsius = temperatureCelsius,
    rainProbabilityPercent = rainProbabilityPercent,
    windSpeedKmh = windSpeedKmh,
    condition = condition,
    forecastSummary = forecastSummary,
    forecastHighCelsius = forecastHighCelsius,
    forecastLowCelsius = forecastLowCelsius,
    forecastPeakRainProbabilityPercent = forecastPeakRainProbabilityPercent,
    description = description,
    severity = Severity.fromApi(severity),
    source = source.toDomain()
)

fun TransitResponseDto.toDomain(): TransitAlert = TransitAlert(
    id = id,
    serviceType = TransitServiceType.fromApi(serviceType),
    affectedLine = affectedLine,
    affectedArea = affectedArea,
    status = status,
    description = description,
    severity = Severity.fromApi(severity),
    startTime = TimestampParser.parseOrNull(startTime),
    endTime = TimestampParser.parseOrNull(endTime),
    updatedAt = TimestampParser.parseOr(updatedAt, Instant.EPOCH),
    expiresAt = TimestampParser.parseOrNull(expiresAt),
    source = source.toDomain()
)

fun SportsResponseDto.toDomain(): SportsEvent = SportsEvent(
    id = id,
    team = team,
    opponent = opponent,
    startAt = TimestampParser.parseOr(startAt, Instant.EPOCH),
    venue = Venue(id = null, name = venue, location = null),
    homeAway = homeAway,
    eventStatus = eventStatus,
    inSeattle = inSeattle,
    description = description,
    severity = Severity.fromApi(severity),
    updatedAt = TimestampParser.parseOr(updatedAt, Instant.EPOCH),
    expiresAt = TimestampParser.parseOrNull(expiresAt),
    source = source.toDomain()
)

fun EventResponseDto.toDomain(): PublicEvent = PublicEvent(
    id = id,
    title = title,
    category = category,
    venue = Venue(id = venueId, name = venue, location = location?.toDomain()),
    startAt = TimestampParser.parseOr(startAt, Instant.EPOCH),
    endAt = TimestampParser.parseOrNull(endAt),
    attendanceEstimate = attendanceEstimate,
    importanceScore = importanceScore,
    major = major,
    description = description,
    severity = Severity.fromApi(severity),
    updatedAt = TimestampParser.parseOr(updatedAt, Instant.EPOCH),
    expiresAt = TimestampParser.parseOrNull(expiresAt),
    source = source.toDomain()
)

fun TrafficResponseDto.toDomain(): TrafficIncident = TrafficIncident(
    id = id,
    title = title,
    description = description,
    severity = Severity.fromApi(severity),
    location = location.toDomain(),
    lanesImpacted = lanesImpacted,
    updatedAt = TimestampParser.parseOr(updatedAt, Instant.EPOCH),
    expiresAt = TimestampParser.parseOrNull(expiresAt),
    source = source.toDomain()
)

fun ImpactResponseDto.toDomain(): ImpactScore = ImpactScore(
    value = value,
    level = Severity.fromApi(level),
    weatherContribution = weatherContribution,
    transitContribution = transitContribution,
    sportsContribution = sportsContribution,
    eventsContribution = eventsContribution,
    trafficContribution = trafficContribution,
    factors = factors,
    generatedAt = TimestampParser.parseOr(generatedAt, Instant.EPOCH),
    explanation = explanation
)

fun AlertResponseDto.toDomain(): CityAlert = CityAlert(
    id = id,
    category = category,
    title = title,
    message = message,
    severity = Severity.fromApi(severity),
    createdAt = TimestampParser.parseOr(createdAt, Instant.EPOCH),
    expiresAt = TimestampParser.parseOrNull(expiresAt),
    detailsUrl = detailsUrl
)
