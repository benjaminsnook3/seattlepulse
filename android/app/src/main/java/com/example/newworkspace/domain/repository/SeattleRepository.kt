package com.example.newworkspace.domain.repository

import com.example.newworkspace.domain.model.CityAlert
import com.example.newworkspace.domain.model.ImpactScore
import com.example.newworkspace.domain.model.Outcome
import com.example.newworkspace.domain.model.PublicEvent
import com.example.newworkspace.domain.model.SportsEvent
import com.example.newworkspace.domain.model.TrafficIncident
import com.example.newworkspace.domain.model.TransitAlert
import com.example.newworkspace.domain.model.Weather

/**
 * Domain contract for Seattle Pulse data. Exposes domain models only; the UI
 * and use cases never see DTOs or Retrofit. Each method reports its own
 * success/failure via [Outcome] so one source failing does not block others.
 */
interface SeattleRepository {
    suspend fun getWeather(): Outcome<Weather?>
    suspend fun getTransitAlerts(): Outcome<List<TransitAlert>>
    suspend fun getSportsEvents(): Outcome<List<SportsEvent>>
    suspend fun getPublicEvents(): Outcome<List<PublicEvent>>
    suspend fun getTrafficIncidents(): Outcome<List<TrafficIncident>>
    suspend fun getImpact(): Outcome<ImpactScore?>
    suspend fun getAlerts(): Outcome<List<CityAlert>>
}
