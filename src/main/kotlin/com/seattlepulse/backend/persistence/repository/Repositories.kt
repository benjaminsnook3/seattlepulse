package com.seattlepulse.backend.persistence.repository

import com.seattlepulse.backend.persistence.entity.AlertEntity
import com.seattlepulse.backend.persistence.entity.DeviceTokenEntity
import com.seattlepulse.backend.persistence.entity.EventEntity
import com.seattlepulse.backend.persistence.entity.SportsEntity
import com.seattlepulse.backend.persistence.entity.TrafficEntity
import com.seattlepulse.backend.persistence.entity.TransitEntity
import com.seattlepulse.backend.persistence.entity.WeatherEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface WeatherRepository : JpaRepository<WeatherEntity, Long> {
    fun findTop20ByOrderByObservedAtDesc(): List<WeatherEntity>
    fun deleteByExpiresAtBefore(cutoff: Instant): Long
}

@Repository
interface TransitRepository : JpaRepository<TransitEntity, Long> {
    fun findTop50ByOrderByUpdatedAtDesc(): List<TransitEntity>
    @Query("""
        select t from TransitEntity t
        where (t.expiresAt is null or t.expiresAt > :cutoff)
          and (t.endTime is null or t.endTime > :cutoff)
        order by t.updatedAt desc
    """)
    fun findActive(cutoff: Instant): List<TransitEntity>
    fun deleteBySourceProvider(sourceProvider: String): Long
    fun deleteByExpiresAtBefore(cutoff: Instant): Long
}

@Repository
interface SportsRepository : JpaRepository<SportsEntity, Long> {
    fun findTop50ByOrderByStartAtDesc(): List<SportsEntity>
    fun findByStartAtBetween(startAt: Instant, endAt: Instant): List<SportsEntity>
    fun deleteBySourceProvider(sourceProvider: String): Long
    fun deleteByExpiresAtBefore(cutoff: Instant): Long
}

@Repository
interface EventRepository : JpaRepository<EventEntity, Long> {
    fun findTop50ByOrderByStartAtDesc(): List<EventEntity>
    fun findByMajorTrueAndStartAtAfter(cutoff: Instant): List<EventEntity>
    fun deleteBySourceProvider(sourceProvider: String): Long
    fun deleteByExpiresAtBefore(cutoff: Instant): Long
}

@Repository
interface TrafficRepository : JpaRepository<TrafficEntity, Long> {
    fun findTop50ByOrderByUpdatedAtDesc(): List<TrafficEntity>
    fun deleteByExpiresAtBefore(cutoff: Instant): Long
}

@Repository
interface AlertRepository : JpaRepository<AlertEntity, Long> {
    fun findTop50ByOrderByCreatedAtDesc(): List<AlertEntity>
    fun existsByExternalId(externalId: String): Boolean
    fun deleteByExpiresAtBefore(cutoff: Instant): Long
}

@Repository
interface DeviceTokenRepository : JpaRepository<DeviceTokenEntity, Long> {
    fun findByToken(token: String): DeviceTokenEntity?
    fun findAllByEnabledTrue(): List<DeviceTokenEntity>
}
