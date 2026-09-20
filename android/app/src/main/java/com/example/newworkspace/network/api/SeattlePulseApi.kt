package com.example.newworkspace.network.api

import retrofit2.http.GET

/**
 * Retrofit interface for the real Seattle Pulse backend endpoints.
 * Only endpoints that actually exist are declared.
 */
interface SeattlePulseApi {

    @GET("weather")
    suspend fun getWeather(): List<WeatherResponseDto>

    @GET("transit")
    suspend fun getTransit(): List<TransitResponseDto>

    @GET("sports")
    suspend fun getSports(): List<SportsResponseDto>

    @GET("events")
    suspend fun getEvents(): List<EventResponseDto>

    @GET("traffic")
    suspend fun getTraffic(): List<TrafficResponseDto>

    @GET("dashboard")
    suspend fun getDashboard(): DashboardResponseDto

    @GET("alerts")
    suspend fun getAlerts(): List<AlertResponseDto>
}
