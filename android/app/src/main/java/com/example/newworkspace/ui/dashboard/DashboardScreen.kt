package com.example.newworkspace.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newworkspace.domain.model.ImpactScore
import com.example.newworkspace.domain.model.PublicEvent
import com.example.newworkspace.domain.model.Severity
import com.example.newworkspace.domain.model.SportsEvent
import com.example.newworkspace.domain.model.TransitAlert
import com.example.newworkspace.domain.model.TransitServiceType
import com.example.newworkspace.domain.model.Weather
import com.example.newworkspace.domain.usecase.SectionFailure
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SEATTLE_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(SEATTLE_ZONE)
private val dayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US).withZone(SEATTLE_ZONE)

@Composable
fun DashboardScreen(
    onSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF0D1B3A), Color(0xFF0B1A34), Color(0xFF081325))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Seattle Pulse",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Live city status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onSettings) { Text("Settings") }
                }
            }
        ) { innerPadding ->
            when (val current = state) {
                DashboardUiState.Loading -> CenteredContent(innerPadding) {
                    CircularProgressIndicator()
                }

                is DashboardUiState.Error -> CenteredContent(innerPadding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(
                            onClick = viewModel::refresh,
                            modifier = Modifier.padding(top = 12.dp)
                        ) { Text("Retry") }
                    }
                }

                is DashboardUiState.Success -> DashboardContent(
                    innerPadding = innerPadding,
                    weather = current.summary.weather,
                    transit = current.summary.transitAlerts,
                    sports = current.summary.sportsEvents,
                    events = current.summary.publicEvents,
                    impact = current.summary.impact,
                    failures = emptyList(),
                    onRetry = viewModel::refresh
                )

                is DashboardUiState.PartialSuccess -> DashboardContent(
                    innerPadding = innerPadding,
                    weather = current.summary.weather,
                    transit = current.summary.transitAlerts,
                    sports = current.summary.sportsEvents,
                    events = current.summary.publicEvents,
                    impact = current.summary.impact,
                    failures = current.failures,
                    onRetry = viewModel::refresh
                )
            }
        }
    }
}

@Composable
private fun CenteredContent(
    innerPadding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun DashboardContent(
    innerPadding: PaddingValues,
    weather: Weather?,
    transit: List<TransitAlert>,
    sports: List<SportsEvent>,
    events: List<PublicEvent>,
    impact: ImpactScore?,
    failures: List<SectionFailure>,
    onRetry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (failures.isNotEmpty()) {
            item {
                PartialFailureBanner(
                    sections = failures.map { it.section.name.lowercase() },
                    onRetry = onRetry
                )
            }
        }

        item {
            if (weather != null) WeatherCard(weather) else UnavailableCard("Weather")
        }

        item { TransitCard(transit) }

        item {
            val nextGame = sports.firstOrNull { it.inSeattle }
            if (nextGame != null) SportsCard(nextGame) else UnavailableCard("Sports", "No upcoming Seattle games")
        }

        item { EventsCard(events) }

        item {
            if (impact != null) SeattleImpactCard(impact) else UnavailableCard("Impact")
        }
    }
}

@Composable
private fun PartialFailureBanner(
    sections: List<String>,
    onRetry: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Some data unavailable: ${sections.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun WeatherCard(weather: Weather) {
    DashboardCard(title = "Weather") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${weather.temperatureCelsius.toInt()} F",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            StatusPill(text = "Rain ${weather.rainProbabilityPercent}%")
        }
        Text(
            text = weather.condition,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = weather.forecastSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TransitCard(transit: List<TransitAlert>) {
    val linkAlerts = transit.filter { it.serviceType == TransitServiceType.LINK }
    val metroAlerts = transit.filter { it.serviceType == TransitServiceType.METRO }

    DashboardCard(title = "Transit") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPill(text = "Link: ${statusLabel(linkAlerts)}")
            StatusPill(text = "Metro: ${statusLabel(metroAlerts)}")
        }

        if (transit.isEmpty()) {
            Text(
                text = "No active alerts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            Text(
                text = "Active alerts",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
            transit.take(5).forEach { alert ->
                Text(
                    text = "• ${alert.serviceType.name}: ${alert.affectedLine} — ${alert.description}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

private fun statusLabel(alerts: List<TransitAlert>): String {
    val worst = alerts.maxByOrNull { it.severity.ordinal }?.severity ?: return "Normal"
    return when (worst) {
        Severity.LOW -> "Minor"
        Severity.MODERATE -> "Minor Delays"
        Severity.HIGH -> "Delays"
        Severity.SEVERE -> "Major Disruption"
        Severity.UNKNOWN -> "Normal"
    }
}

@Composable
private fun SportsCard(game: SportsEvent) {
    DashboardCard(title = "Next Game") {
        InfoRow(label = "Matchup", value = "${game.team} vs ${game.opponent}")
        InfoRow(
            label = "When",
            value = "${dayFormatter.format(game.startAt)} • ${timeFormatter.format(game.startAt)}"
        )
        InfoRow(label = "Venue", value = game.venue.name, showDivider = false)
    }
}

@Composable
private fun EventsCard(events: List<PublicEvent>) {
    DashboardCard(title = "Major Events") {
        if (events.isEmpty()) {
            Text(
                text = "No major events coming up",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            events.take(5).forEach { event ->
                Text(
                    text = "• ${event.title} — ${event.venue.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    text = "  ${dayFormatter.format(event.startAt)} • ${timeFormatter.format(event.startAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SeattleImpactCard(impact: ImpactScore) {
    val levels = listOf("Low", "Moderate", "High", "Severe")
    val current = impact.level.name.lowercase().replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
    }
    DashboardCard(title = "Seattle Impact") {
        Text(
            text = "Current: $current (${impact.value})",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (impact.factors.isNotEmpty()) {
            Text(
                text = impact.factors.joinToString(" + "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            levels.forEach { level ->
                val selected = level == current
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(level) },
                    modifier = Modifier.height(34.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContainerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        disabledLabelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun UnavailableCard(title: String, message: String = "Unavailable right now") {
    DashboardCard(title = title) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, showDivider: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        if (showDivider) {
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium
        )
    }
}
