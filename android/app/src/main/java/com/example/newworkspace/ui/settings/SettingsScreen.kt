package com.example.newworkspace.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newworkspace.preferences.NotificationSensitivity
import com.example.newworkspace.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences
) : ViewModel() {
    val state: StateFlow<com.example.newworkspace.preferences.UserPreferenceState> = preferences.state

    fun toggleTeam(team: String) {
        val updated = state.value.favoriteTeams.toMutableSet()
        if (!updated.add(team)) updated.remove(team)
        preferences.updateFavoriteTeams(updated)
    }

    fun toggleTransit(system: String) {
        val updated = state.value.transitSystems.toMutableSet()
        if (!updated.add(system)) updated.remove(system)
        preferences.updateTransitSystems(updated)
    }

    fun updateNeighborhood(value: String) = preferences.updateNeighborhood(value)

    fun updateSensitivity(value: NotificationSensitivity) = preferences.updateNotificationSensitivity(value)
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var neighborhood by rememberSaveable(state.neighborhood) { mutableStateOf(state.neighborhood) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Personalize Seattle Pulse", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onBack) { Text("Done") }
            }

            PreferenceSection("Favorite teams") {
                ChipRow(TEAM_OPTIONS, state.favoriteTeams, viewModel::toggleTeam)
            }

            PreferenceSection("Transit systems") {
                ChipRow(TRANSIT_OPTIONS, state.transitSystems, viewModel::toggleTransit)
            }

            PreferenceSection("Your neighborhood") {
                OutlinedTextField(
                    value = neighborhood,
                    onValueChange = {
                        neighborhood = it
                        viewModel.updateNeighborhood(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Neighborhood or area") }
                )
            }

            PreferenceSection("Notification sensitivity") {
                NotificationSensitivity.entries.forEach { sensitivity ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.notificationSensitivity == sensitivity,
                            onClick = { viewModel.updateSensitivity(sensitivity) }
                        )
                        Text(sensitivity.label())
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = { Text(option) }
            )
        }
    }
}

private fun NotificationSensitivity.label(): String = when (this) {
    NotificationSensitivity.ALL -> "All updates"
    NotificationSensitivity.IMPORTANT -> "Important updates"
    NotificationSensitivity.CRITICAL -> "Critical disruptions only"
}

private val TEAM_OPTIONS = listOf("Mariners", "Seahawks", "Kraken", "Sounders", "Storm")
private val TRANSIT_OPTIONS = listOf("LINK", "METRO")
