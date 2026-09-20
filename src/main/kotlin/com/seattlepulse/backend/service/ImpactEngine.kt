package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.ImpactLevel
import com.seattlepulse.backend.domain.model.ImpactScore
import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.persistence.repository.EventRepository
import com.seattlepulse.backend.persistence.repository.SportsRepository
import com.seattlepulse.backend.persistence.repository.TrafficRepository
import com.seattlepulse.backend.persistence.repository.TransitRepository
import com.seattlepulse.backend.persistence.repository.WeatherRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The Seattle Impact Engine (Phase 10).
 *
 * Analyzes transit disruptions, sporting events, concerts, public events,
 * weather, and traffic incidents, and produces a 0-100 transportation impact
 * score with human-readable contributing factors:
 *
 *   HIGH IMPACT - "Mariners game + Link delays + downtown concert"
 *
 * Bands: 0-24 LOW, 25-49 MODERATE, 50-74 HIGH, 75-100 SEVERE.
 */
@Service
class ImpactEngine(
    private val weatherRepository: WeatherRepository,
    private val transitRepository: TransitRepository,
    private val sportsRepository: SportsRepository,
    private val eventRepository: EventRepository,
    private val trafficRepository: TrafficRepository,
    private val persistenceMapper: PersistenceMapper
) {

    fun calculateImpact(): ImpactScore {
        val now = Instant.now()
        val windowEnd = now.plus(NEAR_TERM_HOURS, ChronoUnit.HOURS)

        val weather = weatherRepository.findTop20ByOrderByObservedAtDesc()
            .firstOrNull()?.let { persistenceMapper.toDomain(it) }
        val transit = transitRepository.findActive(now).map { persistenceMapper.toDomain(it) }
        val sports = sportsRepository.findByStartAtBetween(now, windowEnd).map { persistenceMapper.toDomain(it) }
        val events = eventRepository.findByMajorTrueAndStartAtAfter(now)
            .filter { it.startAt.isBefore(windowEnd) }
            .map { persistenceMapper.toDomain(it) }
        val traffic = trafficRepository.findTop50ByOrderByUpdatedAtDesc().map { persistenceMapper.toDomain(it) }

        // Weather contribution.
        val weatherScore = weather?.let { severityWeight(it.severity) } ?: 0

        // Transit contribution: active disruptions, capped so one bad line does not dominate.
        val transitScore = transit.sumOf { severityWeight(it.severity) }.coerceAtMost(TRANSIT_CAP)

        // Sports contribution: only games near Seattle, weighted by venue size proxy.
        val sportsScore = sports
            .filter { it.inSeattle }
            .sumOf { severityWeight(it.severity) * 2 }
            .coerceAtMost(SPORTS_CAP)

        // Events contribution: major events already carry an importance score.
        val eventsScore = events.sumOf { (it.importanceScore / 5) + severityWeight(it.severity) / 2 }
            .coerceAtMost(EVENTS_CAP)

        // Traffic contribution: severity plus lane impact.
        val trafficScore = traffic.sumOf { severityWeight(it.severity) + (it.lanesImpacted * 4) }
            .coerceAtMost(TRAFFIC_CAP)

        val total = (weatherScore + transitScore + sportsScore + eventsScore + trafficScore)
            .coerceIn(0, 100)
        val level = ImpactLevel.fromScore(total)

        val factors = buildFactors(weather, transit, sports, events, traffic)
        val explanation = if (factors.isEmpty()) {
            "No significant disruptions right now. Conditions are clear."
        } else {
            "${level.name} impact: " + factors.joinToString(" + ")
        }

        return ImpactScore(
            value = total,
            level = level,
            weatherContribution = weatherScore,
            transitContribution = transitScore,
            sportsContribution = sportsScore,
            eventsContribution = eventsScore,
            trafficContribution = trafficScore,
            factors = factors,
            generatedAt = now,
            explanation = explanation
        )
    }

    private fun buildFactors(
        weather: com.seattlepulse.backend.domain.model.WeatherRecord?,
        transit: List<com.seattlepulse.backend.domain.model.TransitAlert>,
        sports: List<com.seattlepulse.backend.domain.model.SportsRecord>,
        events: List<com.seattlepulse.backend.domain.model.EventRecord>,
        traffic: List<com.seattlepulse.backend.domain.model.TrafficRecord>
    ): List<String> {
        val factors = mutableListOf<String>()

        weather?.let {
            if (it.severity == Severity.HIGH || it.severity == Severity.CRITICAL) {
                factors += when {
                    it.condition.contains("Thunderstorm", true) -> "thunderstorms"
                    it.condition.contains("Rain", true) && it.rainProbabilityPercent >= 60 -> "heavy rain"
                    it.condition.contains("Snow", true) -> "snow"
                    it.windSpeedKmh >= 35 -> "high winds"
                    else -> "bad weather (${it.condition.lowercase()})"
                }
            }
        }

        transit
            .sortedByDescending { severityWeight(it.severity) }
            .take(2)
            .forEach { alert ->
                val label = alert.affectedLine.substringBefore(" Line").trim()
                val kind = if (alert.serviceType.name == "LINK") "Link" else "Metro"
                factors += if (alert.severity == Severity.CRITICAL) {
                    "$kind $label line disruption"
                } else {
                    "$kind delays"
                }
            }

        sports.filter { it.inSeattle }.take(2).forEach { factors += "${it.team} game" }

        events.sortedByDescending { it.importanceScore }.take(2).forEach { ev ->
            val isConcert = ev.category.contains("concert", true) || ev.category.contains("music", true)
            factors += if (isConcert) "concert at ${ev.venue}" else "${ev.title} at ${ev.venue}"
        }

        traffic.sortedByDescending { severityWeight(it.severity) }.take(1).forEach {
            factors += "traffic: ${it.location.label ?: it.title}"
        }

        return factors
    }

    private fun severityWeight(severity: Severity): Int = when (severity) {
        Severity.LOW -> 5
        Severity.MODERATE -> 12
        Severity.HIGH -> 20
        Severity.CRITICAL -> 30
    }

    companion object {
        const val NEAR_TERM_HOURS = 6L
        const val TRANSIT_CAP = 40
        const val SPORTS_CAP = 30
        const val EVENTS_CAP = 30
        const val TRAFFIC_CAP = 35
    }
}
