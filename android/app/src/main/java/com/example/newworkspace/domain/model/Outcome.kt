package com.example.newworkspace.domain.model

/**
 * Domain-level result for a single data source. Lets one failing source be
 * represented without destroying the others. Carries no HTTP/JSON detail.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val reason: FailureReason, val message: String? = null) : Outcome<Nothing>
}

enum class FailureReason {
    NETWORK,
    TIMEOUT,
    SERVER,
    PARSE,
    UNKNOWN
}
