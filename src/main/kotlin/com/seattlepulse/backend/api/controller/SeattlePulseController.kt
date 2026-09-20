package com.seattlepulse.backend.api.controller

import com.seattlepulse.backend.api.dto.AlertResponse
import com.seattlepulse.backend.api.dto.ApiMessage
import com.seattlepulse.backend.api.dto.AreaResponse
import com.seattlepulse.backend.api.dto.DashboardResponse
import com.seattlepulse.backend.api.dto.EventResponse
import com.seattlepulse.backend.api.dto.ForecastPointResponse
import com.seattlepulse.backend.api.dto.GeoPointResponse
import com.seattlepulse.backend.api.dto.ImpactResponse
import com.seattlepulse.backend.api.dto.RegisterAlertDeviceRequest
import com.seattlepulse.backend.api.dto.SourceResponse
import com.seattlepulse.backend.api.dto.SportsResponse
import com.seattlepulse.backend.api.dto.TrafficResponse
import com.seattlepulse.backend.api.dto.TransitResponse
import com.seattlepulse.backend.api.dto.WeatherResponse
import com.seattlepulse.backend.persistence.repository.EventRepository
import com.seattlepulse.backend.persistence.repository.SportsRepository
import com.seattlepulse.backend.persistence.repository.TrafficRepository
import com.seattlepulse.backend.persistence.repository.TransitRepository
import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.domain.model.TransitAlert
import com.seattlepulse.backend.domain.model.TransitServiceType
import com.seattlepulse.backend.persistence.repository.WeatherRepository
import com.seattlepulse.backend.service.AlertService
import com.seattlepulse.backend.service.DashboardService
import com.seattlepulse.backend.service.ImpactEngine
import com.seattlepulse.backend.service.IngestionService
import com.seattlepulse.backend.service.PersistenceMapper
import com.seattlepulse.backend.service.UserAreaService
import com.seattlepulse.backend.service.WeatherCacheService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping
class SeattlePulseController(
    private val ingestionService: IngestionService,
    private val weatherRepository: WeatherRepository,
    private val transitRepository: TransitRepository,
    private val sportsRepository: SportsRepository,
    private val eventRepository: EventRepository,
    private val trafficRepository: TrafficRepository,
    private val alertService: AlertService,
    private val impactEngine: ImpactEngine,
    private val dashboardService: DashboardService,
    private val persistenceMapper: PersistenceMapper,
    private val weatherCacheService: WeatherCacheService,
    private val userAreaService: UserAreaService
) {

    @GetMapping("/weather")
    fun weather(): List<WeatherResponse> {
        val persistedWeather = try {
            weatherRepository.findTop20ByOrderByObservedAtDesc()
                .map { persistenceMapper.toDomain(it).toResponse() }
        } catch (_: Exception) {
            emptyList()
        }
        if (persistedWeather.isNotEmpty()) {
            return persistedWeather
        }

        val cachedWeather = weatherCacheService.getLatestWeather()
        if (cachedWeather != null) {
            return listOf(cachedWeather.toResponse())
        }

        return emptyList()
    }

    @GetMapping("/transit")
    fun transit(): List<TransitResponse> {
        warmStartIfNeeded()
        return transitRepository.findActive(Instant.now())
            .map { persistenceMapper.toDomain(it).toResponse() }
    }

    @GetMapping("/sports")
    fun sports(): List<SportsResponse> {
        warmStartIfNeeded()
        return sportsRepository.findTop50ByOrderByStartAtDesc()
            .map { persistenceMapper.toDomain(it).toResponse() }
    }

    @GetMapping("/events")
    fun events(): List<EventResponse> {
        warmStartIfNeeded()
        return eventRepository.findByMajorTrueAndStartAtAfter(Instant.now())
            .map { persistenceMapper.toDomain(it).toResponse() }
    }

    @GetMapping("/traffic")
    fun traffic(): List<TrafficResponse> {
        warmStartIfNeeded()
        return trafficRepository.findTop50ByOrderByUpdatedAtDesc()
            .map { persistenceMapper.toDomain(it).toResponse() }
    }

    @GetMapping("/dashboard")
    fun dashboard(): DashboardResponse {
        warmStartIfNeeded()
        val dashboard = dashboardService.buildDashboard()
        return DashboardResponse(
            weather = dashboard.weather.map { it.toResponse() },
            transit = dashboard.transit.map { it.toResponse() },
            sports = dashboard.sports.map { it.toResponse() },
            events = dashboard.events.map { it.toResponse() },
            traffic = dashboard.traffic.map { it.toResponse() },
            tonight = dashboard.tonight.map { it.toResponse() },
            impact = impactEngine.calculateImpact().toResponse(),
            generatedAt = dashboard.generatedAt
        )
    }

    /**
     * Location-aware view. Location is optional: pass device lat/lon, or a
     * neighborhood name for manual selection, or nothing at all (defaults to
     * Downtown Seattle).
     */
    @GetMapping("/area")
    fun area(
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lon: Double?,
        @RequestParam(required = false) neighborhood: String?,
        @RequestParam(required = false) radiusKm: Double?
    ): AreaResponse {
        warmStartIfNeeded()
        val now = Instant.now()
        val area = userAreaService.resolveArea(lat, lon, neighborhood, radiusKm ?: UserAreaService.DEFAULT_RADIUS_KM)

        val activeTransit = transitRepository.findActive(now).map { persistenceMapper.toDomain(it) }
        val majorEvents = eventRepository.findByMajorTrueAndStartAtAfter(now).map { persistenceMapper.toDomain(it) }
        val traffic = trafficRepository.findTop50ByOrderByUpdatedAtDesc().map { persistenceMapper.toDomain(it) }

        val relevance = userAreaService.evaluate(area, activeTransit, majorEvents, traffic)

        val weather = weatherCacheService.getLatestWeather()
            ?: weatherRepository.findTop20ByOrderByObservedAtDesc().firstOrNull()?.let { persistenceMapper.toDomain(it) }

        return AreaResponse(
            neighborhood = area.neighborhood.label,
            center = area.center.toResponse(),
            radiusKm = area.radiusKm,
            linkStatus = serviceStatus(relevance.transit.filter { it.serviceType == TransitServiceType.LINK }),
            metroStatus = serviceStatus(relevance.transit.filter { it.serviceType == TransitServiceType.METRO }),
            nearbyEvents = relevance.events.map { it.toResponse() },
            nearbyTraffic = relevance.traffic.map { it.toResponse() },
            relevantTransit = relevance.transit.map { it.toResponse() },
            rainProbabilityPercent = weather?.rainProbabilityPercent,
            condition = weather?.condition
        )
    }

    private fun serviceStatus(alerts: List<TransitAlert>): String {
        val worst = alerts.maxByOrNull { severityRank(it.severity) }?.severity
        return when (worst) {
            Severity.CRITICAL -> "Major Disruption"
            Severity.HIGH -> "Delays"
            Severity.MODERATE -> "Minor Delays"
            else -> "Normal"
        }
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.LOW -> 1
        Severity.MODERATE -> 2
        Severity.HIGH -> 3
        Severity.CRITICAL -> 4
    }

    @GetMapping("/alerts")
    fun alerts(): List<AlertResponse> {
        warmStartIfNeeded()
        return alertService.listAlerts().map { it.toResponse() }
    }

    @PostMapping("/alerts")
    fun registerAlertDevice(@Valid @RequestBody request: RegisterAlertDeviceRequest): ApiMessage {
        alertService.registerDeviceToken(request.token, request.platform)
        return ApiMessage("Device registered for Seattle Pulse notifications")
    }

    private fun warmStartIfNeeded() {
        if (weatherRepository.count() == 0L) {
            ingestionService.refreshAll()
        }
    }

    private fun com.seattlepulse.backend.domain.model.GeoPoint.toResponse() = GeoPointResponse(latitude, longitude, label)

    private fun com.seattlepulse.backend.domain.model.DataSource.toResponse() = SourceResponse(provider, endpoint, fetchedAt)

    private fun com.seattlepulse.backend.domain.model.WeatherRecord.toResponse() = WeatherResponse(
        id = id,
        observedAt = observedAt,
        updatedAt = updatedAt,
        expiresAt = expiresAt,
        location = location.toResponse(),
        temperatureCelsius = temperatureCelsius,
        rainProbabilityPercent = rainProbabilityPercent,
        windSpeedKmh = windSpeedKmh,
        condition = condition,
        forecastSummary = forecastSummary,
        forecastHighCelsius = forecastHighCelsius,
        forecastLowCelsius = forecastLowCelsius,
        forecastPeakRainProbabilityPercent = forecastPeakRainProbabilityPercent,
        forecast = forecast.map {
            ForecastPointResponse(
                time = it.time,
                temperatureCelsius = it.temperatureCelsius,
                rainProbabilityPercent = it.rainProbabilityPercent,
                condition = it.condition
            )
        },
        description = description,
        severity = severity,
        source = source.toResponse()
    )

    private fun com.seattlepulse.backend.domain.model.TransitAlert.toResponse() = TransitResponse(
        id = id,
        serviceType = serviceType,
        affectedLine = affectedLine,
        affectedArea = affectedArea,
        status = status,
        description = description,
        severity = severity,
        startTime = startTime,
        endTime = endTime,
        updatedAt = updatedAt,
        expiresAt = expiresAt,
        source = source.toResponse()
    )

    private fun com.seattlepulse.backend.domain.model.SportsRecord.toResponse() = SportsResponse(
        id = id,
        team = team,
        opponent = opponent,
        startAt = startAt,
        venue = venue,
        homeAway = homeAway,
        eventStatus = eventStatus,
        inSeattle = inSeattle,
        description = description,
        severity = severity,
        updatedAt = updatedAt,
        expiresAt = expiresAt,
        source = source.toResponse()
    )

    private fun com.seattlepulse.backend.domain.model.EventRecord.toResponse() = EventResponse(
        id = id,
        title = title,
        category = category,
        venue = venue,
        venueId = venueId,
        location = location?.toResponse(),
        startAt = startAt,
        endAt = endAt,
        attendanceEstimate = attendanceEstimate,
        importanceScore = importanceScore,
        major = major,
        description = description,
        severity = severity,
        updatedAt = updatedAt,
        expiresAt = expiresAt,
        source = source.toResponse()
    )

    private fun com.seattlepulse.backend.domain.model.TrafficRecord.toResponse() = TrafficResponse(
        id = id,
        title = title,
        description = description,
        severity = severity,
        location = location.toResponse(),
        lanesImpacted = lanesImpacted,
        updatedAt = updatedAt,
        expiresAt = expiresAt,
        source = source.toResponse()
    )

    private fun com.seattlepulse.backend.domain.model.ImpactScore.toResponse() = ImpactResponse(
        value = value,
        level = level,
        weatherContribution = weatherContribution,
        transitContribution = transitContribution,
        sportsContribution = sportsContribution,
        eventsContribution = eventsContribution,
        trafficContribution = trafficContribution,
        factors = factors,
        generatedAt = generatedAt,
        explanation = explanation
    )

    private fun com.seattlepulse.backend.domain.model.AlertRecord.toResponse() = AlertResponse(
        id = id,
        category = category,
        title = title,
        message = message,
        severity = severity,
        createdAt = createdAt,
        expiresAt = expiresAt
    )
}
