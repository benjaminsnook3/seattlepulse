package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.AlertRecord
import com.seattlepulse.backend.domain.model.Dashboard
import com.seattlepulse.backend.domain.model.ImpactLevel
import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.domain.model.SportsRecord
import com.seattlepulse.backend.integration.provider.EventsProvider
import com.seattlepulse.backend.integration.provider.SportsProvider
import com.seattlepulse.backend.integration.provider.TrafficProvider
import com.seattlepulse.backend.integration.provider.TransitProvider
import com.seattlepulse.backend.persistence.entity.DeviceTokenEntity
import com.seattlepulse.backend.persistence.repository.AlertRepository
import com.seattlepulse.backend.persistence.repository.DeviceTokenRepository
import com.seattlepulse.backend.persistence.repository.EventRepository
import com.seattlepulse.backend.persistence.repository.SportsRepository
import com.seattlepulse.backend.persistence.repository.TrafficRepository
import com.seattlepulse.backend.persistence.repository.TransitRepository
import com.seattlepulse.backend.persistence.repository.WeatherRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
class IngestionService(
    private val weatherCacheService: WeatherCacheService,
    private val transitProviders: List<TransitProvider>,
    private val sportsProviders: List<SportsProvider>,
    private val eventsProviders: List<EventsProvider>,
    private val trafficProvider: TrafficProvider,
    private val normalizationService: NormalizationService,
    private val persistenceMapper: PersistenceMapper,
    private val weatherRepository: WeatherRepository,
    private val transitRepository: TransitRepository,
    private val sportsRepository: SportsRepository,
    private val eventRepository: EventRepository,
    private val trafficRepository: TrafficRepository,
    private val alertService: AlertService,
    @Value("\${seattle.weather.refresh-minutes:60}") private val weatherRefreshMinutes: Long = 60
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${seattle.ingestion.fixed-delay-ms}",
        initialDelayString = "\${seattle.ingestion.initial-delay-ms:45000}"
    )
    @Transactional
    fun refreshAll() {
        ingestWeather()
        ingestTransit()
        ingestSports()
        ingestEvents()
        ingestTraffic()
        alertService.evaluateAndEmitAlerts()
    }

    @Transactional
    fun ingestWeather() {
        try {
            val latest = weatherRepository.findTop20ByOrderByObservedAtDesc().firstOrNull()
            val refreshAfter = Instant.now().minusSeconds(weatherRefreshMinutes * 60)
            if (latest != null && latest.observedAt.isAfter(refreshAfter)) {
                logger.info("Weather ingestion skipped; latest record is less than one hour old")
                return
            }

            weatherCacheService.getLatestWeather(forceRefresh = true)?.let { weather ->
                weatherRepository.save(persistenceMapper.toEntity(weather))
                logger.info("Weather ingestion updated {}", weather.id)
            }
        } catch (ex: Exception) {
            logger.error("Weather ingestion failed safely", ex)
        }
    }

    @Transactional
    fun ingestTransit() {
        transitProviders.forEach { provider ->
            try {
                transitRepository.deleteBySourceProvider(provider.providerName)
                val entities = provider.fetch()
                    .map { normalizationService.toTransit(it, provider.providerName, transitEndpoint(provider)) }
                    .map { persistenceMapper.toEntity(it) }
                transitRepository.saveAll(entities)
                logger.info("Transit ingestion [{}] upserted {} rows", provider.providerName, entities.size)
            } catch (ex: Exception) {
                logger.error("Transit ingestion [{}] failed safely", provider.providerName, ex)
            }
        }
    }

    private fun transitEndpoint(provider: TransitProvider): String = when (provider.providerName) {
        "SoundTransit" -> "https://www.soundtransit.org/ride-with-us/service-alerts"
        "KingCountyMetro" -> "https://kingcounty.gov/en/dept/metro/travel-options/riders-guide/service-alerts"
        else -> "https://www.soundtransit.org/ride-with-us/service-alerts"
    }

    @Transactional
    fun ingestSports() {
        sportsProviders.forEach { provider ->
            try {
                sportsRepository.deleteBySourceProvider(provider.providerName)
                val entities = provider.fetch()
                    .map { normalizationService.toSports(it, provider.providerName, "https://www.espn.com/") }
                    .map { persistenceMapper.toEntity(it) }
                sportsRepository.saveAll(entities)
                logger.info("Sports ingestion [{}] upserted {} rows", provider.providerName, entities.size)
            } catch (ex: Exception) {
                logger.error("Sports ingestion [{}] failed safely", provider.providerName, ex)
            }
        }
    }

    @Transactional
    fun ingestEvents() {
        eventsProviders.forEach { provider ->
            try {
                eventRepository.deleteBySourceProvider(provider.providerName)
                val entities = provider.fetch()
                    .map { normalizationService.toEvent(it, provider.providerName, "https://www.ticketmaster.com/") }
                    .map { persistenceMapper.toEntity(it) }
                eventRepository.saveAll(entities)
                logger.info("Events ingestion [{}] upserted {} rows", provider.providerName, entities.size)
            } catch (ex: Exception) {
                logger.error("Events ingestion [{}] failed safely", provider.providerName, ex)
            }
        }
    }

    @Transactional
    fun ingestTraffic() {
        val entities = trafficProvider.fetch()
            .map { normalizationService.toTraffic(it, "MockTrafficFeed", null) }
            .map { persistenceMapper.toEntity(it) }
        trafficRepository.saveAll(entities)
        logger.info("Traffic ingestion upserted {} rows", entities.size)
    }
}

@Service
class CleanupService(
    private val weatherRepository: WeatherRepository,
    private val transitRepository: TransitRepository,
    private val sportsRepository: SportsRepository,
    private val eventRepository: EventRepository,
    private val trafficRepository: TrafficRepository,
    private val alertRepository: AlertRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${seattle.cleanup.fixed-delay-ms}")
    @Transactional
    fun removeExpired() {
        val cutoff = Instant.now()
        val removed = weatherRepository.deleteByExpiresAtBefore(cutoff) +
            transitRepository.deleteByExpiresAtBefore(cutoff) +
            sportsRepository.deleteByExpiresAtBefore(cutoff) +
            eventRepository.deleteByExpiresAtBefore(cutoff) +
            trafficRepository.deleteByExpiresAtBefore(cutoff) +
            alertRepository.deleteByExpiresAtBefore(cutoff)

        if (removed > 0) {
            logger.info("Removed {} expired rows", removed)
        }
    }
}

interface PushNotificationGateway {
    fun send(token: String, title: String, body: String)
}

@Service
class LoggingPushNotificationGateway : PushNotificationGateway {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(token: String, title: String, body: String) {
        logger.info("Push notification token={} title={} body={}", token, title, body)
    }
}

@Service
class AlertService(
    private val impactEngine: ImpactEngine,
    private val trafficRepository: TrafficRepository,
    private val transitRepository: TransitRepository,
    private val sportsRepository: SportsRepository,
    private val eventRepository: EventRepository,
    private val weatherRepository: WeatherRepository,
    private val alertRepository: AlertRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val persistenceMapper: PersistenceMapper,
    private val pushNotificationGateway: PushNotificationGateway,
    private val notificationPolicy: NotificationPolicy,
    @Value("\${seattle.notifications.enabled}") private val notificationsEnabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Tracked across cycles so we only alert on meaningful *changes*.
    @Volatile private var lastImpactLevel: ImpactLevel? = null
    @Volatile private var lastWeatherSeverity: Severity? = null

    fun listAlerts() = alertRepository.findTop50ByOrderByCreatedAtDesc().map { persistenceMapper.toDomain(it) }

    @Transactional
    fun registerDeviceToken(token: String, platform: String) {
        val existing = deviceTokenRepository.findByToken(token)
        if (existing == null) {
            deviceTokenRepository.save(
                DeviceTokenEntity(
                    token = token,
                    platform = platform,
                    createdAt = Instant.now(),
                    enabled = true
                )
            )
        } else if (!existing.enabled) {
            deviceTokenRepository.save(existing.copy(enabled = true))
        }
    }

    @Transactional
    fun evaluateAndEmitAlerts() {
        val now = Instant.now()
        val impact = impactEngine.calculateImpact()
        val candidates = mutableListOf<Triple<AlertRecord, NotificationPriority, String>>()

        // 1) Impact score change (significant level change only).
        if (impact.level != lastImpactLevel && (impact.level == ImpactLevel.HIGH || impact.level == ImpactLevel.SEVERE)) {
            candidates += Triple(
                AlertRecord(
                    id = "impact-${impact.level}-${now.truncatedTo(ChronoUnit.HOURS).epochSecond}",
                    category = "IMPACT",
                    title = "Seattle Pulse — ${impact.level} IMPACT",
                    message = impact.factors.joinToString(" + ").ifBlank { "City impact score ${impact.value}." },
                    severity = impact.level.toSeverity(),
                    createdAt = now,
                    expiresAt = now.plus(2, ChronoUnit.HOURS)
                ),
                if (impact.level == ImpactLevel.SEVERE) NotificationPriority.CRITICAL else NotificationPriority.HIGH,
                "impact-level"
            )
        }
        lastImpactLevel = impact.level

        // 2) Major Link / Metro disruptions.
        transitRepository.findActive(now)
            .filter { it.severity == Severity.HIGH || it.severity == Severity.CRITICAL }
            .forEach { alert ->
                val kind = if (alert.serviceType == com.seattlepulse.backend.domain.model.TransitServiceType.LINK) "Link" else "Metro"
                candidates += Triple(
                    AlertRecord(
                        id = "transit-${alert.externalId}",
                        category = "TRANSIT",
                        title = "$kind ${alert.affectedLine} disruption",
                        message = alert.description,
                        severity = alert.severity,
                        createdAt = now,
                        expiresAt = now.plus(90, ChronoUnit.MINUTES)
                    ),
                    NotificationPriority.CRITICAL,
                    "transit-${alert.serviceType}-${alert.affectedLine}"
                )
            }

        // 3) Critical traffic incidents.
        trafficRepository.findTop50ByOrderByUpdatedAtDesc()
            .filter { it.severity == Severity.CRITICAL }
            .forEach { incident ->
                candidates += Triple(
                    AlertRecord(
                        id = "traffic-${incident.externalId}",
                        category = "TRAFFIC",
                        title = incident.title,
                        message = incident.description,
                        severity = incident.severity,
                        createdAt = now,
                        expiresAt = now.plus(90, ChronoUnit.MINUTES)
                    ),
                    NotificationPriority.HIGH,
                    "traffic-${incident.externalId}"
                )
            }

        // 4) Game starting soon (within 90 minutes, in Seattle).
        sportsRepository.findByStartAtBetween(now, now.plus(90, ChronoUnit.MINUTES))
            .filter { it.inSeattle }
            .forEach { game ->
                candidates += Triple(
                    AlertRecord(
                        id = "game-soon-${game.externalId}",
                        category = "SPORTS",
                        title = "${game.team} game starts in 90 minutes",
                        message = "Expect heavier transit around ${game.venue}.",
                        severity = Severity.MODERATE,
                        createdAt = now,
                        expiresAt = game.startAt
                    ),
                    NotificationPriority.NORMAL,
                    "game-soon-${game.externalId}"
                )
            }

        // 5) Large concert / major event starting soon (within 90 minutes).
        eventRepository.findByMajorTrueAndStartAtAfter(now)
            .filter { it.startAt.isBefore(now.plus(90, ChronoUnit.MINUTES)) }
            .forEach { event ->
                val isConcert = event.category.contains("concert", true) || event.category.contains("music", true)
                candidates += Triple(
                    AlertRecord(
                        id = "event-soon-${event.externalId}",
                        category = "EVENT",
                        title = if (isConcert) "Concert starting soon" else "Major event starting soon",
                        message = "${event.title} at ${event.venue}. Expect crowded transit.",
                        severity = Severity.MODERATE,
                        createdAt = now,
                        expiresAt = event.startAt
                    ),
                    NotificationPriority.NORMAL,
                    "event-soon-${event.externalId}"
                )
            }

        // 6) Significant weather change (severity jump of two or more levels).
        val weather = weatherRepository.findTop20ByOrderByObservedAtDesc().firstOrNull()?.let { persistenceMapper.toDomain(it) }
        if (weather != null) {
            val previous = lastWeatherSeverity
            val jumped = previous != null && severityRank(weather.severity) - severityRank(previous) >= 2
            if (jumped && (weather.severity == Severity.HIGH || weather.severity == Severity.CRITICAL)) {
                candidates += Triple(
                    AlertRecord(
                        id = "weather-${now.truncatedTo(ChronoUnit.HOURS).epochSecond}",
                        category = "WEATHER",
                        title = "Weather worsening: ${weather.condition}",
                        message = "Rain ${weather.rainProbabilityPercent}%, wind ${weather.windSpeedKmh.toInt()} km/h. Commute impact rising.",
                        severity = weather.severity,
                        createdAt = now,
                        expiresAt = now.plus(2, ChronoUnit.HOURS)
                    ),
                    NotificationPriority.HIGH,
                    "weather-change"
                )
            }
            lastWeatherSeverity = weather.severity
        }

        // Emit highest priority first, respecting cooldowns and the per-cycle cap.
        var sent = 0
        candidates
            .sortedBy { it.second.ordinal }
            .forEach { (alert, priority, cooldownKey) ->
                sent = emitAlert(alert, priority, cooldownKey, sent)
            }
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.LOW -> 1
        Severity.MODERATE -> 2
        Severity.HIGH -> 3
        Severity.CRITICAL -> 4
    }

    private fun ImpactLevel.toSeverity(): Severity = when (this) {
        ImpactLevel.LOW -> Severity.LOW
        ImpactLevel.MODERATE -> Severity.MODERATE
        ImpactLevel.HIGH -> Severity.HIGH
        ImpactLevel.SEVERE -> Severity.CRITICAL
    }

    /**
     * Saves the alert and pushes it only when the notification policy allows.
     * Returns the updated count of notifications sent this cycle.
     */
    private fun emitAlert(alert: AlertRecord, priority: NotificationPriority, cooldownKey: String, sentSoFar: Int): Int {
        val now = alert.createdAt
        val allowed = notificationPolicy.shouldSend(cooldownKey, priority, now) &&
            sentSoFar < NotificationPolicy.MAX_PER_CYCLE

        if (!allowed) {
            logger.debug("Suppressed notification key={} priority={} sentSoFar={}", cooldownKey, priority, sentSoFar)
            return sentSoFar
        }

        val normalizedAlert = alert.copy(
            title = alert.title.take(450),
            message = alert.message.take(3500)
        )
        try {
            if (!alertRepository.existsByExternalId(normalizedAlert.id)) {
                alertRepository.save(persistenceMapper.toEntity(normalizedAlert))
            }
        } catch (_: DataIntegrityViolationException) {
            logger.debug("Skipped duplicate alert id={}", normalizedAlert.id)
        }

        notificationPolicy.recordSent(cooldownKey, priority, now)

        if (notificationsEnabled) {
            deviceTokenRepository.findAllByEnabledTrue().forEach { token ->
                pushNotificationGateway.send(token.token, normalizedAlert.title, normalizedAlert.message)
            }
        }
        return sentSoFar + 1
    }
}

@Service
class DashboardService(
    private val weatherRepository: WeatherRepository,
    private val transitRepository: TransitRepository,
    private val sportsRepository: SportsRepository,
    private val eventRepository: EventRepository,
    private val trafficRepository: TrafficRepository,
    private val impactEngine: ImpactEngine,
    private val persistenceMapper: PersistenceMapper
) {
    fun buildDashboard(): Dashboard {
        val now = Instant.now()
        val sports = sportsRepository.findTop50ByOrderByStartAtDesc().map { persistenceMapper.toDomain(it) }
        return Dashboard(
            weather = weatherRepository.findTop20ByOrderByObservedAtDesc().map { persistenceMapper.toDomain(it) },
            transit = transitRepository.findActive(now).map { persistenceMapper.toDomain(it) },
            sports = sports,
            events = eventRepository.findByMajorTrueAndStartAtAfter(now).map { persistenceMapper.toDomain(it) },
            traffic = trafficRepository.findTop50ByOrderByUpdatedAtDesc().map { persistenceMapper.toDomain(it) },
            tonight = tonightGames(sports, now),
            impact = impactEngine.calculateImpact(),
            generatedAt = now
        )
    }

    private fun tonightGames(sports: List<SportsRecord>, now: Instant): List<SportsRecord> {
        val seattleZone = ZoneId.of("America/Los_Angeles")
        val endOfToday = now.atZone(seattleZone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(seattleZone)
            .toInstant()
        return sports
            .filter { it.inSeattle && it.startAt.isAfter(now) && it.startAt.isBefore(endOfToday) }
            .sortedBy { it.startAt }
    }
}
