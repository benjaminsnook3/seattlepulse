package com.example.newworkspace.preferences

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NotificationSensitivity {
    ALL,
    IMPORTANT,
    CRITICAL
}

data class UserPreferenceState(
    val favoriteTeams: Set<String> = emptySet(),
    val transitSystems: Set<String> = setOf("LINK", "METRO"),
    val neighborhood: String = "Downtown Seattle",
    val notificationSensitivity: NotificationSensitivity = NotificationSensitivity.IMPORTANT
)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<UserPreferenceState> = _state.asStateFlow()

    fun updateFavoriteTeams(teams: Set<String>) = update { copy(favoriteTeams = teams) }

    fun updateTransitSystems(systems: Set<String>) = update { copy(transitSystems = systems) }

    fun updateNeighborhood(neighborhood: String) = update { copy(neighborhood = neighborhood) }

    fun updateNotificationSensitivity(sensitivity: NotificationSensitivity) =
        update { copy(notificationSensitivity = sensitivity) }

    private fun update(transform: UserPreferenceState.() -> UserPreferenceState) {
        val updated = _state.value.transform()
        preferences.edit {
            putStringSet(KEY_TEAMS, updated.favoriteTeams)
            putStringSet(KEY_TRANSIT, updated.transitSystems)
            putString(KEY_NEIGHBORHOOD, updated.neighborhood)
            putString(KEY_SENSITIVITY, updated.notificationSensitivity.name)
        }
        _state.value = updated
    }

    private fun readState(): UserPreferenceState {
        val sensitivity = preferences.getString(KEY_SENSITIVITY, null)
            ?.let { runCatching { NotificationSensitivity.valueOf(it) }.getOrNull() }
            ?: NotificationSensitivity.IMPORTANT
        return UserPreferenceState(
            favoriteTeams = preferences.getStringSet(KEY_TEAMS, emptySet()).orEmpty(),
            transitSystems = preferences.getStringSet(KEY_TRANSIT, setOf("LINK", "METRO")).orEmpty(),
            neighborhood = preferences.getString(KEY_NEIGHBORHOOD, "Downtown Seattle").orEmpty(),
            notificationSensitivity = sensitivity
        )
    }

    private companion object {
        const val FILE_NAME = "seattle_pulse_preferences"
        const val KEY_TEAMS = "favorite_teams"
        const val KEY_TRANSIT = "transit_systems"
        const val KEY_NEIGHBORHOOD = "neighborhood"
        const val KEY_SENSITIVITY = "notification_sensitivity"
    }
}
