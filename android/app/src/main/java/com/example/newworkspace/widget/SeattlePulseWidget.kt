package com.example.newworkspace.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.newworkspace.MainActivity
import com.example.newworkspace.R
import com.example.newworkspace.domain.model.Outcome
import com.example.newworkspace.domain.model.Severity
import com.example.newworkspace.domain.model.SportsEvent
import com.example.newworkspace.domain.model.TransitAlert
import com.example.newworkspace.domain.model.TransitServiceType
import com.example.newworkspace.domain.model.SeattleDaySummary
import com.example.newworkspace.domain.repository.SeattleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SeattlePulseWidgetEntryPoint {
    fun repository(): SeattleRepository
}

class SeattlePulseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val repository = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SeattlePulseWidgetEntryPoint::class.java
        ).repository()

        CoroutineScope(Dispatchers.IO).launch {
            when (val result = repository.getDashboardSummary()) {
                is Outcome.Success -> {
                    val views = buildViews(context, result.data)
                    appWidgetIds.forEach { manager.updateAppWidget(it, views) }
                }
                is Outcome.Failure -> Unit
            }
            pendingResult.finish()
        }
    }

    override fun onEnabled(context: Context) {
        onUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            onUpdate(context)
        }
    }

    private fun onUpdate(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, SeattlePulseWidget::class.java))
        if (ids.isNotEmpty()) onUpdate(context, manager, ids)
    }

    private fun buildViews(
        context: Context,
        summary: SeattleDaySummary
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.seattle_pulse_widget)
        views.setTextViewText(
            R.id.widget_impact,
            summary.impact?.let { "${it.level.label()} impact · ${it.value}/100" } ?: "Impact unavailable"
        )
        views.setTextViewText(R.id.widget_transit, "Link: ${transitStatus(summary.transitAlerts, TransitServiceType.LINK)}")
        views.setTextViewText(R.id.widget_next_event, nextEventText(summary.sportsEvents))

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_title, openApp)
        return views
    }

    private fun transitStatus(alerts: List<TransitAlert>, type: TransitServiceType): String {
        val worst = alerts.filter { it.serviceType == type }.maxByOrNull { severityRank(it.severity) }?.severity
        return when (worst) {
            Severity.SEVERE -> "Major disruption"
            Severity.HIGH -> "Delays"
            Severity.MODERATE -> "Minor delays"
            else -> "Normal"
        }
    }

    private fun nextEventText(sports: List<SportsEvent>): String {
        val event = sports.filter { it.inSeattle }.minByOrNull { it.startAt } ?: return "On the road!"
        return "${event.team} · ${event.venue.name}"
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.LOW -> 1
        Severity.MODERATE -> 2
        Severity.HIGH -> 3
        Severity.SEVERE -> 4
        Severity.UNKNOWN -> 0
    }

    private fun <T> Outcome<T>.successOrNull(): T? =
        (this as? Outcome.Success)?.data

    private fun Severity.label(): String = when (this) {
        Severity.LOW -> "LOW"
        Severity.MODERATE -> "MODERATE"
        Severity.HIGH -> "HIGH"
        Severity.SEVERE -> "SEVERE"
        Severity.UNKNOWN -> "UPDATE"
    }

    companion object {
        const val ACTION_REFRESH = "com.example.newworkspace.widget.REFRESH"
    }
}
