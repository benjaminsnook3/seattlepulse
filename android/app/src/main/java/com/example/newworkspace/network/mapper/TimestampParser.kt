package com.example.newworkspace.network.mapper

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Single place where backend ISO-8601 timestamp strings become [Instant].
 * Date parsing must not be scattered through the UI or ViewModels.
 */
object TimestampParser {

    /** Parses an ISO-8601 instant. Returns null for blank input. */
    fun parseOrNull(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(raw).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    /**
     * Parses a required ISO-8601 instant. Falls back to [fallback] when the
     * value is missing or unparseable so one malformed field cannot crash the
     * whole mapping.
     */
    fun parseOr(raw: String?, fallback: Instant): Instant = parseOrNull(raw) ?: fallback
}
