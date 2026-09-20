package com.seattlepulse.backend.service

import com.seattlepulse.backend.integration.dto.ExternalWeatherDto
import com.seattlepulse.backend.integration.dto.ExternalWeatherForecastDto
import com.seattlepulse.backend.integration.provider.WeatherProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class WeatherCacheServiceTest {

    private val normalizationService = NormalizationService()

    @Test
    fun `returns normalized weather when provider succeeds`() {
        val provider = MutableWeatherProvider(successPayload())
        val service = WeatherCacheService(provider, normalizationService, cacheTtlSeconds = 900)

        val weather = service.getLatestWeather()

        assertNotNull(weather)
        assertEquals("weather-success", weather?.id)
        assertEquals("Rain likely in next 12 hours", weather?.forecastSummary)
        assertEquals(2, weather?.forecast?.size)
        assertEquals(80, weather?.forecastPeakRainProbabilityPercent)
    }

    @Test
    fun `returns cached weather when provider fails after successful fetch`() {
        val provider = MutableWeatherProvider(successPayload())
        val service = WeatherCacheService(provider, normalizationService, cacheTtlSeconds = 900)

        val initial = service.getLatestWeather()
        provider.fail = true

        val cached = service.getLatestWeather()

        assertNotNull(initial)
        assertNotNull(cached)
        assertEquals(initial?.id, cached?.id)
        assertEquals(initial?.forecastSummary, cached?.forecastSummary)
    }

    @Test
    fun `returns null when provider fails and no cache exists`() {
        val provider = MutableWeatherProvider(successPayload())
        provider.fail = true
        val service = WeatherCacheService(provider, normalizationService, cacheTtlSeconds = 900)

        val weather = service.getLatestWeather()

        assertNull(weather)
    }

    private fun successPayload(): ExternalWeatherDto {
        val now = Instant.parse("2026-09-08T10:00:00Z")
        return ExternalWeatherDto(
            id = "weather-success",
            observedAt = now,
            temperatureCelsius = 16.5,
            windSpeedKmh = 22.0,
            rainProbabilityPercent = 60,
            condition = "Rain",
            forecastSummary = "Rain likely in next 12 hours",
            forecast = listOf(
                ExternalWeatherForecastDto(
                    time = now.plusSeconds(3600),
                    temperatureCelsius = 17.0,
                    rainProbabilityPercent = 80,
                    condition = "Rain"
                ),
                ExternalWeatherForecastDto(
                    time = now.plusSeconds(7200),
                    temperatureCelsius = 15.0,
                    rainProbabilityPercent = 50,
                    condition = "Cloudy"
                )
            ),
            description = "Live weather from provider"
        )
    }

    private class MutableWeatherProvider(
        private val payload: ExternalWeatherDto
    ) : WeatherProvider {
        var fail: Boolean = false

        override fun fetch(): ExternalWeatherDto? {
            if (fail) {
                throw RuntimeException("provider unavailable")
            }
            return payload
        }
    }
}
