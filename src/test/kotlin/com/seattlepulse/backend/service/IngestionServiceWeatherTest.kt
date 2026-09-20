package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.DataSource
import com.seattlepulse.backend.domain.model.ForecastPoint
import com.seattlepulse.backend.domain.model.GeoPoint
import com.seattlepulse.backend.domain.model.Severity
import com.seattlepulse.backend.domain.model.WeatherRecord
import com.seattlepulse.backend.persistence.entity.WeatherEntity
import com.seattlepulse.backend.persistence.repository.EventRepository
import com.seattlepulse.backend.persistence.repository.SportsRepository
import com.seattlepulse.backend.persistence.repository.TrafficRepository
import com.seattlepulse.backend.persistence.repository.TransitRepository
import com.seattlepulse.backend.persistence.repository.WeatherRepository
import com.seattlepulse.backend.integration.provider.EventsProvider
import com.seattlepulse.backend.integration.provider.SportsProvider
import com.seattlepulse.backend.integration.provider.TrafficProvider
import com.seattlepulse.backend.integration.provider.TransitProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import java.time.Instant

class IngestionServiceWeatherTest {

    @Test
    fun `ingestWeather stores normalized weather on success`() {
        val weatherCacheService = mockk<WeatherCacheService>()
        val weatherRepository = mockk<WeatherRepository>()
        val persistenceMapper = mockk<PersistenceMapper>()
        val ingestionService = buildService(weatherCacheService, weatherRepository, persistenceMapper)

        val weather = sampleWeather()
        val entity = sampleEntity(weather)

        every { weatherRepository.findTop20ByOrderByObservedAtDesc() } returns emptyList()
        every { weatherCacheService.getLatestWeather(true) } returns weather
        every { persistenceMapper.toEntity(weather) } returns entity
        every { weatherRepository.save(entity) } returns entity

        assertDoesNotThrow { ingestionService.ingestWeather() }
        verify(exactly = 1) { weatherCacheService.getLatestWeather(true) }
        verify(exactly = 1) { weatherRepository.save(entity) }
    }

    @Test
    fun `ingestWeather does not throw when weather service fails`() {
        val weatherCacheService = mockk<WeatherCacheService>()
        val weatherRepository = mockk<WeatherRepository>()
        val persistenceMapper = mockk<PersistenceMapper>()
        val ingestionService = buildService(weatherCacheService, weatherRepository, persistenceMapper)

        every { weatherRepository.findTop20ByOrderByObservedAtDesc() } returns emptyList()
        every { weatherCacheService.getLatestWeather(true) } throws RuntimeException("network down")

        assertDoesNotThrow { ingestionService.ingestWeather() }
        verify(exactly = 1) { weatherCacheService.getLatestWeather(true) }
        verify(exactly = 0) { weatherRepository.save(any<WeatherEntity>()) }
    }

    private fun buildService(
        weatherCacheService: WeatherCacheService,
        weatherRepository: WeatherRepository,
        persistenceMapper: PersistenceMapper
    ): IngestionService {
        return IngestionService(
            weatherCacheService = weatherCacheService,
            transitProviders = listOf(mockk<TransitProvider>()),
            sportsProviders = listOf(mockk<SportsProvider>()),
            eventsProviders = listOf(mockk<EventsProvider>()),
            trafficProvider = mockk<TrafficProvider>(),
            normalizationService = mockk<NormalizationService>(),
            persistenceMapper = persistenceMapper,
            weatherRepository = weatherRepository,
            transitRepository = mockk<TransitRepository>(),
            sportsRepository = mockk<SportsRepository>(),
            eventRepository = mockk<EventRepository>(),
            trafficRepository = mockk<TrafficRepository>(),
            alertService = mockk<AlertService>()
        )
    }

    private fun sampleWeather(): WeatherRecord {
        val now = Instant.parse("2026-09-08T10:00:00Z")
        return WeatherRecord(
            id = "weather-test",
            observedAt = now,
            updatedAt = now,
            expiresAt = now.plusSeconds(3600),
            location = GeoPoint(47.6062, -122.3321, "Seattle"),
            temperatureCelsius = 15.0,
            rainProbabilityPercent = 60,
            windSpeedKmh = 19.0,
            condition = "Rain",
            forecastSummary = "Rain likely in next 12 hours",
            forecastHighCelsius = 16.0,
            forecastLowCelsius = 13.0,
            forecastPeakRainProbabilityPercent = 75,
            forecast = listOf(
                ForecastPoint(
                    time = now.plusSeconds(3600),
                    temperatureCelsius = 16.0,
                    rainProbabilityPercent = 75,
                    condition = "Rain"
                )
            ),
            description = "Weather test payload",
            severity = Severity.MODERATE,
            source = DataSource("TestProvider", "/weather")
        )
    }

    private fun sampleEntity(weather: WeatherRecord): WeatherEntity {
        return WeatherEntity(
            externalId = weather.id,
            observedAt = weather.observedAt,
            updatedAt = weather.updatedAt,
            expiresAt = weather.expiresAt,
            latitude = weather.location.latitude,
            longitude = weather.location.longitude,
            locationLabel = weather.location.label,
            temperatureCelsius = weather.temperatureCelsius,
            rainProbabilityPercent = weather.rainProbabilityPercent,
            windSpeedKmh = weather.windSpeedKmh,
            condition = weather.condition,
            forecastSummary = weather.forecastSummary,
            forecastHighCelsius = weather.forecastHighCelsius,
            forecastLowCelsius = weather.forecastLowCelsius,
            forecastPeakRainProbabilityPercent = weather.forecastPeakRainProbabilityPercent,
            description = weather.description,
            severity = weather.severity,
            sourceProvider = weather.source.provider,
            sourceEndpoint = weather.source.endpoint
        )
    }
}
