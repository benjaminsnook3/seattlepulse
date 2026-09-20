package com.example.newworkspace.ui.dashboard

import com.example.newworkspace.domain.model.FailureReason
import com.example.newworkspace.domain.model.ImpactScore
import com.example.newworkspace.domain.model.SeattleDaySummary
import com.example.newworkspace.domain.model.Severity
import com.example.newworkspace.domain.usecase.DaySummaryResult
import com.example.newworkspace.domain.usecase.DataSection
import com.example.newworkspace.domain.usecase.GetSeattleDaySummaryUseCase
import com.example.newworkspace.domain.usecase.SectionFailure
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val useCase = mockk<GetSeattleDaySummaryUseCase>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun summary() = SeattleDaySummary(
        weather = null, transitAlerts = emptyList(), sportsEvents = emptyList(),
        publicEvents = emptyList(), trafficIncidents = emptyList(),
        impact = ImpactScore(62, Severity.HIGH, 0, 0, 0, 0, 0, listOf("x"), Instant.now(), "e"),
        generatedAt = Instant.now()
    )

    @Test
    fun `starts in loading then becomes success`() = runTest {
        coEvery { useCase() } returns DaySummaryResult(summary(), emptyList())

        val viewModel = DashboardViewModel(useCase)
        assertEquals(DashboardUiState.Loading, viewModel.uiState.value)

        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is DashboardUiState.Success)
    }

    @Test
    fun `partial failures surface as PartialSuccess`() = runTest {
        coEvery { useCase() } returns DaySummaryResult(
            summary(), listOf(SectionFailure(DataSection.SPORTS, FailureReason.NETWORK))
        )

        val viewModel = DashboardViewModel(useCase)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DashboardUiState.PartialSuccess)
        assertEquals(1, (state as DashboardUiState.PartialSuccess).failures.size)
    }

    @Test
    fun `use case throwing surfaces as Error`() = runTest {
        coEvery { useCase() } throws RuntimeException("boom")

        val viewModel = DashboardViewModel(useCase)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DashboardUiState.Error)
        assertEquals("boom", (state as DashboardUiState.Error).message)
    }
}
