package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.EventRecord
import com.seattlepulse.backend.domain.model.GeoPoint
import com.seattlepulse.backend.domain.model.TrafficRecord
import com.seattlepulse.backend.domain.model.TransitAlert
import org.springframework.stereotype.Service
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Approximate Seattle neighborhood centers used for location-aware views.
 * The user may pick one manually instead of granting device location.
 */
enum class SeattleNeighborhood(val label: String, val latitude: Double, val longitude: Double) {
    DOWNTOWN("Downtown", 47.6062, -122.3321),
    PIONEER_SQUARE("Pioneer Square", 47.6011, -122.3317),
    INTERNATIONAL_DISTRICT("International District", 47.5982, -122.3245),
    SODO("SoDo", 47.5929, -122.3331),
    CAPITOL_HILL("Capitol Hill", 47.6251, -122.3222),
    FIRST_HILL("First Hill", 47.6092, -122.3100),
    QUEEN_ANNE("Queen Anne", 47.6366, -122.3575),
    FREMONT("Fremont", 47.6510, -122.3502),
    WALLINGFORD("Wallingford", 47.6610, -122.3340),
    UNIVERSITY_DISTRICT("University District", 47.6615, -122.3121),
    NORTHGATE("Northgate", 47.7061, -122.3252),
    BALLARD("Ballard", 47.6685, -122.3835),
    INTERBAY("Interbay", 47.6470, -122.3770),
    WEST_SEATTLE("West Seattle", 47.5810, -122.3700),
    BEACON_HILL("Beacon Hill", 47.5796, -122.3110),
    COLUMBIA_CITY("Columbia City", 47.5605, -122.2870),
    RAINIER_BEACH("Rainier Beach", 47.5480, -122.2690);

    companion object {
        fun fromName(name: String?): SeattleNeighborhood? {
            if (name.isNullOrBlank()) return null
            val normalized = name.trim().lowercase()
            return entries.firstOrNull {
                it.label.lowercase() == normalized ||
                    it.name.lowercase() == normalized ||
                    it.name.lowercase().replace('_', ' ') == normalized
            }
        }

        fun nearest(latitude: Double, longitude: Double): SeattleNeighborhood =
            entries.minByOrNull { UserAreaService.distanceKm(latitude, longitude, it.latitude, it.longitude) } ?: DOWNTOWN
    }
}

data class UserArea(
    val center: GeoPoint,
    val neighborhood: SeattleNeighborhood,
    val radiusKm: Double
)

data class AreaRelevance(
    val area: UserArea,
    val transit: List<TransitAlert>,
    val events: List<EventRecord>,
    val traffic: List<TrafficRecord>
)

/**
 * Resolves the user's area (device coordinates or a manually selected
 * neighborhood) and filters transit, events, and traffic down to what is
 * relevant to that area. Location is never required: with no input the area
 * defaults to Downtown Seattle.
 */
@Service
class UserAreaService {

    fun resolveArea(
        latitude: Double?,
        longitude: Double?,
        neighborhoodName: String?,
        radiusKm: Double = DEFAULT_RADIUS_KM
    ): UserArea {
        val neighborhood = SeattleNeighborhood.fromName(neighborhoodName)
            ?: if (latitude != null && longitude != null) {
                SeattleNeighborhood.nearest(latitude, longitude)
            } else {
                SeattleNeighborhood.DOWNTOWN
            }

        val center = if (latitude != null && longitude != null) {
            GeoPoint(latitude, longitude, neighborhood.label)
        } else {
            GeoPoint(neighborhood.latitude, neighborhood.longitude, neighborhood.label)
        }

        return UserArea(center = center, neighborhood = neighborhood, radiusKm = radiusKm)
    }

    fun relevantTransit(area: UserArea, transit: List<TransitAlert>): List<TransitAlert> {
        val label = area.neighborhood.label.lowercase()
        return transit.filter { alert ->
            val text = listOfNotNull(alert.affectedArea, alert.description, alert.affectedLine)
                .joinToString(" ")
                .lowercase()
            text.contains(label) ||
                (area.neighborhood == SeattleNeighborhood.DOWNTOWN &&
                    (text.contains("downtown") || text.contains("seattle center") || text.contains("sodo")))
        }
    }

    fun relevantEvents(area: UserArea, events: List<EventRecord>): List<EventRecord> =
        events.filter { event ->
            event.location?.let {
                distanceKm(area.center.latitude, area.center.longitude, it.latitude, it.longitude) <= area.radiusKm
            } ?: false
        }

    fun relevantTraffic(area: UserArea, traffic: List<TrafficRecord>): List<TrafficRecord> =
        traffic.filter { record ->
            distanceKm(area.center.latitude, area.center.longitude, record.location.latitude, record.location.longitude) <= area.radiusKm
        }

    fun evaluate(
        area: UserArea,
        transit: List<TransitAlert>,
        events: List<EventRecord>,
        traffic: List<TrafficRecord>
    ): AreaRelevance = AreaRelevance(
        area = area,
        transit = relevantTransit(area, transit),
        events = relevantEvents(area, events),
        traffic = relevantTraffic(area, traffic)
    )

    companion object {
        const val DEFAULT_RADIUS_KM = 3.0

        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earthRadiusKm = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (earthRadiusKm * c * 100).roundToInt() / 100.0
        }
    }
}
