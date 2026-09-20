package com.example.newworkspace.domain.model

import java.time.Instant

/**
 * An alert the backend has decided is worth notifying about. Distinct from the
 * raw dashboard data: these are already filtered/prioritized server-side.
 */
data class CityAlert(
    val id: String,
    val category: String,
    val title: String,
    val message: String,
    val severity: Severity,
    val createdAt: Instant,
    val expiresAt: Instant?
)
