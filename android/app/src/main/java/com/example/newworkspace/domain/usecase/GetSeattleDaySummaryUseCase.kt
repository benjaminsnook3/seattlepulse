package com.example.newworkspace.domain.usecase

import com.example.newworkspace.domain.model.FailureReason
import com.example.newworkspace.domain.model.Outcome
import com.example.newworkspace.domain.model.SeattleDaySummary
import com.example.newworkspace.domain.repository.SeattleRepository
import java.time.Instant
import javax.inject.Inject

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

    suspend operator fun invoke(): DaySummaryResult = when (val result = repository.getDashboardSummary()) {
        is Outcome.Success -> DaySummaryResult(result.data, emptyList())
        is Outcome.Failure -> DaySummaryResult(
            summary = SeattleDaySummary(
                weather = null,
                transitAlerts = emptyList(),
                sportsEvents = emptyList(),
                publicEvents = emptyList(),
                trafficIncidents = emptyList(),
                impact = null,
                generatedAt = Instant.now()
            ),
            failures = DataSection.entries.map { SectionFailure(it, result.reason) }
        )
    }
}
