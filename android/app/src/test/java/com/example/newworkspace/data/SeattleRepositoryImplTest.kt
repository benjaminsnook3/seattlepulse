package com.example.newworkspace.data

import com.example.newworkspace.domain.model.FailureReason
import com.example.newworkspace.domain.model.Outcome
import com.example.newworkspace.network.api.GeoPointResponseDto
import com.example.newworkspace.network.api.SeattlePulseApi
import com.example.newworkspace.network.api.SourceResponseDto
import com.example.newworkspace.network.api.WeatherResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class SeattleRepositoryImplTest {

    private val api = mockk<SeattlePulseApi>()
    private val repository = SeattleRepositoryImpl(api)

    private val source = SourceResponseDto("TestProvider", null, "2026-09-14T18:00:00Z")

    private fun weatherDto(id: String = "w1") = WeatherResponseDto(
        id = id,
        observedAt = "2026-09-14T18:00:00Z",
        updatedAt = "2026-09-14T18:00:00Z",
        expiresAt = null,
        location = GeoPointResponseDto(47.6, -122.3, "Seattle"),
        temperatureCelsius = 15.0,
        rainProbabilityPercent = 40,
        windSpeedKmh = 10.0,
        condition = "Cloudy",
        forecastSummary = "Stable",
        forecastHighCelsius = 17.0,
        forecastLowCelsius = 12.0,
        forecastPeakRainProbabilityPercent = 50,
        forecast = emptyList(),
        description = "d",
        severity = "LOW",
        source = source
    )

    @Test
    fun `getWeather returns mapped domain on success`() = runTest {
        coEvery { api.getWeather() } returns listOf(weatherDto())

        val result = repository.getWeather()

        assertTrue(result is Outcome.Success)
        val weather = (result as Outcome.Success).data
        assertEquals("w1", weather?.id)
        assertEquals(Instant.parse("2026-09-14T18:00:00Z"), weather?.observedAt)
    }

    @Test
    fun `getWeather returns null data when backend has no rows`() = runTest {
        coEvery { api.getWeather() } returns emptyList()

        val result = repository.getWeather()

        assertTrue(result is Outcome.Success)
        assertNull((result as Outcome.Success).data)
    }

    @Test
    fun `network failure becomes Outcome Failure not exception`() = runTest {
        coEvery { api.getTransit() } throws IOException("connection refused")

        val result = repository.getTransitAlerts()

        assertTrue(result is Outcome.Failure)
        assertEquals(FailureReason.NETWORK, (result as Outcome.Failure).reason)
    }

    @Test
    fun `server error becomes SERVER failure reason`() = runTest {
        val httpException = mockk<HttpException>()
        io.mockk.every { httpException.code() } returns 503
        coEvery { api.getSports() } throws httpException

        val result = repository.getSportsEvents()

        assertTrue(result is Outcome.Failure)
        assertEquals(FailureReason.SERVER, (result as Outcome.Failure).reason)
    }

    @Test
    fun `provider quota error becomes recoverable network failure`() = runTest {
        val httpException = mockk<HttpException>()
        io.mockk.every { httpException.code() } returns 429
        coEvery { api.getWeather() } throws httpException

        val result = repository.getWeather()

        assertTrue(result is Outcome.Failure)
        assertEquals(FailureReason.NETWORK, (result as Outcome.Failure).reason)
    }
}
