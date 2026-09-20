package com.example.newworkspace.domain.usecase

import com.example.newworkspace.domain.model.FailureReason
import com.example.newworkspace.domain.model.Outcome
import com.example.newworkspace.domain.model.SeattleDaySummary
import com.example.newworkspace.domain.repository.SeattleRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class DataSection {
    WEATHER,
    TRANSIT,
    SPORTS,
    EVENTS,
    TRAFFIC,
    IMPACT
}

data class SectionFailure(
    val section: DataSection,
    val reason: FailureReason
)

data class DaySummaryResult(
    val summary: SeattleDaySummary,
    val failures: List<SectionFailure>
)

/**
 * Retrieves the individual data sources concurrently and assembles the
 * SeattleDaySummary aggregate. One failing source never cancels the others:
 * its section is left empty/null and reported in [DaySummaryResult.failures].
 */
class GetSeattleDaySummaryUseCase @Inject constructor(
    private val repository: SeattleRepository
) {

    suspend operator fun invoke(): DaySummaryResult = coroutineScope {
        val weatherDeferred = async { repository.getWeather() }
        val transitDeferred = async { repository.getTransitAlerts() }
        val sportsDeferred = async { repository.getSportsEvents() }
        val eventsDeferred = async { repository.getPublicEvents() }
        val trafficDeferred = async { repository.getTrafficIncidents() }
        val impactDeferred = async { repository.getImpact() }

        val failures = mutableListOf<SectionFailure>()

        val weather = when (val o = weatherDeferred.await()) {
            is Outcome.Success -> o.data
            is Outcome.Failure -> { failures += SectionFailure(DataSection.WEATHER, o.reason); null }
        }
        val transit = when (val o = transitDeferred.await()) {
            is Outcome.Success -> o.data
            is Outcome.Failure -> { failures += SectionFailure(DataSection.TRANSIT, o.reason); emptyList() }
        }
        val sports = when (val o = sportsDeferred.await()) {
            is Outcome.Success -> o.data
            is Outcome.Failure -> { failures += SectionFailure(DataSection.SPORTS, o.reason); emptyList() }
        }
        val events = when (val o = eventsDeferred.await()) {
            is Outcome.Success -> o.data
            is Outcome.Failure -> { failures += SectionFailure(DataSection.EVENTS, o.reason); emptyList() }
        }
        val traffic = when (val o = trafficDeferred.await()) {
            is Outcome.Success -> o.data
            is Outcome.Failure -> { failures += SectionFailure(DataSection.TRAFFIC, o.reason); emptyList() }
        }
        val impact = when (val o = impactDeferred.await()) {
            is Outcome.Success -> o.data
            is Outcome.Failure -> { failures += SectionFailure(DataSection.IMPACT, o.reason); null }
        }

        DaySummaryResult(
            summary = SeattleDaySummary(
                weather = weather,
                transitAlerts = transit,
                sportsEvents = sports,
                publicEvents = events,
                trafficIncidents = traffic,
                impact = impact,
                generatedAt = Instant.now()
            ),
            failures = failures
        )
    }
}
