package com.seattlepulse.backend.service

import org.springframework.stereotype.Service

/**
 * Known major Seattle venues with Ticketmaster venue ids and typical capacity.
 * Used to decide whether an event is "major" and worth surfacing.
 */
enum class SeattleVenue(
    val ticketmasterId: String?,
    val displayName: String,
    val typicalCapacity: Int,
    val transitSensitive: Boolean
) {
    CLIMATE_PLEDGE_ARENA("KovZ917Ahkk", "Climate Pledge Arena", 17000, true),
    LUMEN_FIELD("KovZpZAEknnA", "Lumen Field", 69000, true),
    T_MOBILE_PARK("KovZpZAEevAA", "T-Mobile Park", 47000, true),
    SEATTLE_CENTER("KovZpZAFkktA", "Seattle Center", 20000, true),
    PARAMOUNT_THEATRE("KovZpZAFkvEA", "Paramount Theatre", 3100, false),
    MERCER_ARTS_ARENA("KovZpZA1tnIA", "Seattle Center Mercer Arts Arena", 2000, false),
    WAMU_THEATER("KovZpZAFFE7A", "WAMU Theater", 10000, true),
    SEATTLE_CONVENTION_CENTER("KovZ917AcnU", "Seattle Convention Center", 15000, true),
    BENAROYA_HALL("KovZpZAaIAkA", "Benaroya Hall", 2500, false),
    MOORE_THEATRE("KovZpZA1vFJA", "Moore Theatre", 1400, false);

    companion object {
        fun fromTicketmasterId(id: String?): SeattleVenue? =
            entries.firstOrNull { it.ticketmasterId != null && it.ticketmasterId == id }

        fun fromName(name: String?): SeattleVenue? {
            if (name.isNullOrBlank()) return null
            val normalized = name.lowercase()
            return entries.firstOrNull { venue ->
                normalized.contains(venue.displayName.lowercase()) ||
                    venue.displayName.lowercase().contains(normalized)
            }
        }
    }
}

data class ImportanceResult(
    val score: Int,
    val major: Boolean,
    val reasons: List<String>
)

/**
 * Scores events 0-100 and decides whether they are "major" enough to surface.
 *
 * Rules (per Phase 9):
 * - major professional sporting event
 * - large concert at a major venue
 * - major festival / parade / large public gathering
 * - anything expected to significantly affect transit
 *
 * Anything below the threshold is filtered out so the app is not an endless
 * list of random events.
 */
@Service
class EventImportanceService {

    fun score(
        title: String,
        category: String,
        venueName: String?,
        venueId: String?,
        attendanceEstimate: Int?
    ): ImportanceResult {
        val reasons = mutableListOf<String>()
        var score = 0

        val venue = SeattleVenue.fromTicketmasterId(venueId) ?: SeattleVenue.fromName(venueName)

        // 1) Attendance estimate is the strongest signal when available.
        val effectiveAttendance = attendanceEstimate ?: venue?.typicalCapacity
        if (effectiveAttendance != null) {
            when {
                effectiveAttendance >= 40000 -> { score += 45; reasons += "very large crowd (~$effectiveAttendance)" }
                effectiveAttendance >= 15000 -> { score += 35; reasons += "large crowd (~$effectiveAttendance)" }
                effectiveAttendance >= 5000 -> { score += 20; reasons += "moderate crowd (~$effectiveAttendance)" }
                else -> score += 5
            }
        }

        // 2) Known major venue bonus.
        if (venue != null) {
            score += 20
            reasons += "major venue: ${venue.displayName}"
        }

        // 3) Category weighting.
        val cat = category.lowercase()
        when {
            listOf("sports", "football", "baseball", "hockey", "basketball", "soccer", "lacrosse")
                .any { cat.contains(it) } -> { score += 20; reasons += "professional sporting event" }
            listOf("festival", "parade", "fair", "marathon", "race", "concert", "music")
                .any { cat.contains(it) } -> { score += 15; reasons += "large public gathering" }
            listOf("theatre", "comedy", "family", "arts", "ballet", "opera")
                .any { cat.contains(it) } -> score += 5
        }

        // 4) Title keywords that indicate a big deal even if category is vague.
        val t = title.lowercase()
        if (listOf("championship", "playoffs", "finals", "super", "derby", "tournament")
                .any { t.contains(it) }
        ) {
            score += 10
            reasons += "championship/high-profile matchup"
        }
        if (listOf("festival", "parade", "marathon", "new year's eve", "4th of july", "fireworks")
                .any { t.contains(it) }
        ) {
            score += 10
            reasons += "citywide event"
        }

        // 5) Transit sensitivity of the venue area.
        if (venue?.transitSensitive == true) {
            score += 10
            reasons += "transit-sensitive location"
        }

        val capped = score.coerceIn(0, 100)
        val major = capped >= MAJOR_THRESHOLD ||
            (venue != null && (venue.typicalCapacity >= 15000)) ||
            effectiveAttendance?.let { it >= 15000 } == true

        return ImportanceResult(score = capped, major = major, reasons = reasons)
    }

    companion object {
        const val MAJOR_THRESHOLD = 55
    }
}
