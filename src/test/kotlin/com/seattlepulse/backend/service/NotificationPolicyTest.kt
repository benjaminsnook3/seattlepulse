package com.seattlepulse.backend.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class NotificationPolicyTest {

    private val policy = NotificationPolicy()
    private val now = Instant.parse("2026-09-14T10:00:00Z")

    @Test
    fun `first alert is always allowed`() {
        assertTrue(policy.shouldSend("key-1", NotificationPriority.HIGH, now))
    }

    @Test
    fun `same key within cooldown is suppressed`() {
        policy.recordSent("key-1", NotificationPriority.HIGH, now)
        assertFalse(policy.shouldSend("key-1", NotificationPriority.HIGH, now.plus(30, ChronoUnit.MINUTES)))
    }

    @Test
    fun `same key after cooldown is allowed again`() {
        policy.recordSent("key-1", NotificationPriority.HIGH, now)
        assertTrue(policy.shouldSend("key-1", NotificationPriority.HIGH, now.plus(61, ChronoUnit.MINUTES)))
    }

    @Test
    fun `critical escalation bypasses normal cooldown`() {
        policy.recordSent("key-1", NotificationPriority.NORMAL, now)
        assertTrue(policy.shouldSend("key-1", NotificationPriority.CRITICAL, now.plus(1, ChronoUnit.MINUTES)))
    }

    @Test
    fun `different keys are independent`() {
        policy.recordSent("key-1", NotificationPriority.HIGH, now)
        assertTrue(policy.shouldSend("key-2", NotificationPriority.HIGH, now))
    }

    @Test
    fun `prune clears old cooldown state`() {
        policy.recordSent("key-1", NotificationPriority.HIGH, now)
        policy.prune(now.plus(2, ChronoUnit.HOURS))
        assertTrue(policy.shouldSend("key-1", NotificationPriority.HIGH, now.plus(3, ChronoUnit.HOURS)))
    }
}
