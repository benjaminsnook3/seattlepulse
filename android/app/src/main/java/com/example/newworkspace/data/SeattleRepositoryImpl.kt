package com.example.newworkspace.data

import com.example.newworkspace.domain.model.CityAlert
import com.example.newworkspace.domain.model.FailureReason
import com.example.newworkspace.domain.model.ImpactScore
import com.example.newworkspace.domain.model.Outcome
import com.example.newworkspace.domain.model.PublicEvent
import com.example.newworkspace.domain.model.SportsEvent
import com.example.newworkspace.domain.model.TrafficIncident
import com.example.newworkspace.domain.model.TransitAlert
import com.example.newworkspace.domain.model.Weather
import com.example.newworkspace.domain.repository.SeattleRepository
import com.example.newworkspace.network.api.SeattlePulseApi
import com.example.newworkspace.network.mapper.toDomain
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * Calls Retrofit, receives DTOs, maps them to domain models, and exposes only
 * domain models. Failures are captured per-call into [Outcome.Failure] rather
 * than thrown, so a single broken source does not take down the dashboard.
 */
class SeattleRepositoryImpl @Inject constructor(
    private val api: SeattlePulseApi
) : SeattleRepository {

    override suspend fun getWeather(): Outcome<Weather?> = call {
        api.getWeather().firstOrNull()?.toDomain()
    }

    override suspend fun getTransitAlerts(): Outcome<List<TransitAlert>> = call {
        api.getTransit().map { it.toDomain() }
    }

    override suspend fun getSportsEvents(): Outcome<List<SportsEvent>> = call {
        api.getSports().map { it.toDomain() }
    }

    override suspend fun getPublicEvents(): Outcome<List<PublicEvent>> = call {
        api.getEvents().map { it.toDomain() }
    }

    override suspend fun getTrafficIncidents(): Outcome<List<TrafficIncident>> = call {
        api.getTraffic().map { it.toDomain() }
    }

    /**
     * The backend exposes impact only as part of /dashboard; there is no
     * standalone /impact endpoint, so we read it from the dashboard payload.
     */
    override suspend fun getImpact(): Outcome<ImpactScore?> = call {
        api.getDashboard().impact.toDomain()
    }

    override suspend fun getAlerts(): Outcome<List<CityAlert>> = call {
        api.getAlerts().map { it.toDomain() }
    }

    private inline fun <T> call(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: SocketTimeoutException) {
        Outcome.Failure(FailureReason.TIMEOUT, e.message)
    } catch (e: IOException) {
        Outcome.Failure(FailureReason.NETWORK, e.message)
    } catch (e: HttpException) {
        Outcome.Failure(
            reason = if (e.code() >= 500) FailureReason.SERVER else FailureReason.NETWORK,
            message = "HTTP ${e.code()}"
        )
    } catch (e: SerializationException) {
        Outcome.Failure(FailureReason.PARSE, e.message)
    } catch (e: Exception) {
        Outcome.Failure(FailureReason.UNKNOWN, e.message)
    }
}
