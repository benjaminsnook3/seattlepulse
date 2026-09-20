package com.example.newworkspace.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.example.newworkspace.R
import com.example.newworkspace.domain.usecase.SectionFailure
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri

private val SEATTLE_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(SEATTLE_ZONE)
private val dayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US).withZone(SEATTLE_ZONE)
private val alertTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).withZone(SEATTLE_ZONE)

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
                        .statusBarsPadding()
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
                    transit = current.summary.transitAlerts,
                    sports = current.summary.sportsEvents,
                    events = current.summary.publicEvents,
                    impact = current.summary.impact,
                    failures = emptyList(),
                    onRetry = viewModel::refresh
                )

                is DashboardUiState.PartialSuccess -> DashboardContent(
                    innerPadding = innerPadding,
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
            Image(
                painter = painterResource(R.drawable.seattle_skyline),
                contentDescription = "Seattle skyline",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                contentScale = ContentScale.Crop
            )
        }

        item { TransitCard(transit) }

        item {
            val nextGame = sports.filter { it.inSeattle }.minByOrNull { it.startAt }
            if (nextGame != null) SportsCard(nextGame) else UnavailableCard("Sports", "On the road!")
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
            Image(
                painter = painterResource(weatherIcon(weather.condition)),
                contentDescription = weather.condition,
                modifier = Modifier.height(54.dp),
                contentScale = ContentScale.Fit
            )
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
        if (transit.isEmpty()) {
            Text(
                text = "No active alerts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            TransitGroup(title = "Link", alerts = linkAlerts)
            TransitGroup(title = "Metro", alerts = metroAlerts)
        }
    }
}

@Composable
private fun TransitGroup(title: String, alerts: List<TransitAlert>) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }

    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(
                    if (title == "Metro") R.drawable.logo_metro else R.drawable.logo_link
                ),
                contentDescription = "$title logo",
                modifier = Modifier.height(28.dp).padding(end = 8.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "$title · ${statusLabel(alerts)} (${alerts.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (expanded) "Hide" else "Show",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (expanded) {
            if (alerts.isEmpty()) {
                Text(
                    text = "No active alerts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            } else {
                alerts.take(8).forEach { TransitAlertRow(it) }
            }
        }
    }
}

@Composable
private fun TransitAlertRow(alert: TransitAlert) {
    var expanded by rememberSaveable(alert.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val detailsUrl = alert.source.endpoint

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!detailsUrl.isNullOrBlank()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(detailsUrl)))
                } else {
                    expanded = !expanded
                }
            }
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = alert.affectedLine,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (detailsUrl.isNullOrBlank()) "Details" else "Tap for details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = compactTransitSummary(alert) ?: transitTimeWindow(alert),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (expanded && detailsUrl.isNullOrBlank()) {
            Text(
                text = "${alert.status}: ${alert.description}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}

private fun transitTimeWindow(alert: TransitAlert): String {
    val start = alert.startTime?.let(alertTimeFormatter::format)
    val end = alert.endTime?.let(alertTimeFormatter::format)
    return when {
        start != null && end != null -> "$start - $end"
        start != null -> "Starting $start"
        end != null -> "Until $end"
        else -> "Ongoing"
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

private fun compactTransitSummary(alert: TransitAlert): String? {
    val tripPattern = Regex(
        "(?i)to\\s+(.+?)\\s+scheduled at\\s+([0-9]{1,2}:[0-9]{2}\\s*(?:a\\.m\\.|p\\.m\\.|am|pm))\\s+from\\s+(.+?)(?=\\r?\\n|$)"
    )
    val trips = tripPattern.findAll(alert.description).mapNotNull { match ->
        val destination = match.groupValues[1].trim()
        val time = match.groupValues[2].trim().replace("a.m.", "AM").replace("p.m.", "PM")
        val origin = match.groupValues[3].trim()
        Triple(destination, time, origin)
    }.toList()

    if (trips.size < 2) return null
    val first = trips.first()
    val last = trips.last()
    if (first.first != last.first || first.third != last.third) return null

    return "Affected trips to ${first.first} from ${first.third} ${first.second} - ${last.second}"
}
@Composable
private fun SportsCard(game: SportsEvent) {
    DashboardCard(title = "Next Game") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            teamLogo(game.team)?.let { logo ->
                Image(
                    painter = painterResource(logo),
                    contentDescription = "${game.team} logo",
                    modifier = Modifier.height(42.dp).padding(end = 10.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = "${game.team} vs ${game.opponent}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        InfoRow(
            label = "When",
            value = "${dayFormatter.format(game.startAt)} • ${timeFormatter.format(game.startAt)}"
        )
        InfoRow(label = "Venue", value = game.venue.name, showDivider = false)
    }
}

private fun weatherIcon(condition: String): Int {
    val normalized = condition.lowercase(Locale.US)
    return when {
        normalized.contains("rain") || normalized.contains("shower") -> R.drawable.weather_rain
        normalized.contains("cloud") || normalized.contains("fog") -> R.drawable.weather_cloudy_rain
        else -> R.drawable.weather_sun
    }
}

private fun teamLogo(team: String): Int? = when (team.lowercase(Locale.US)) {
    "seahawks" -> R.drawable.logo_seahawks
    "sounders" -> R.drawable.logo_sounders
    else -> null
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
    val current = impact.level.name.lowercase().replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
    }
    DashboardCard(title = "Seattle Impact") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(impactColor(impact.level), CircleShape)
            )
            Text(
                text = "$current impact",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (impact.factors.isNotEmpty()) {
            Text(
                text = impact.factors.joinToString(" + "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

private fun impactColor(level: Severity): Color = when (level) {
    Severity.LOW -> Color(0xFF35C759)
    Severity.MODERATE -> Color(0xFFFFCC00)
    Severity.HIGH -> Color(0xFFFF9500)
    Severity.SEVERE -> Color(0xFFFF3B30)
    Severity.UNKNOWN -> Color(0xFF9B9DA5)
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
