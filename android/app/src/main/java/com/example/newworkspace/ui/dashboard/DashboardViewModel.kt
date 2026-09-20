package com.example.newworkspace.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newworkspace.domain.model.SeattleDaySummary
import com.example.newworkspace.domain.usecase.DaySummaryResult
import com.example.newworkspace.domain.usecase.GetSeattleDaySummaryUseCase
import com.example.newworkspace.domain.usecase.SectionFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Explicit dashboard UI state. No JSON, DTO, or HTTP detail leaks in here.
 */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val summary: SeattleDaySummary) : DashboardUiState
    data class PartialSuccess(
        val summary: SeattleDaySummary,
        val failures: List<SectionFailure>
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getSeattleDaySummary: GetSeattleDaySummaryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            val result: DaySummaryResult = try {
                getSeattleDaySummary()
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "Unable to load Seattle Pulse data")
                return@launch
            }

            _uiState.value = if (result.failures.isEmpty()) {
                DashboardUiState.Success(result.summary)
            } else {
                DashboardUiState.PartialSuccess(result.summary, result.failures)
            }
        }
    }
}
