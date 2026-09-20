package com.seattlepulse.backend.service

import com.seattlepulse.backend.domain.model.WeatherRecord
import com.seattlepulse.backend.integration.provider.WeatherProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Service
class WeatherCacheService(
    private val weatherProviders: List<WeatherProvider>,
    private val normalizationService: NormalizationService,
    @Value("\${seattle.weather.cache-ttl-seconds:900}") private val cacheTtlSeconds: Long
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val cache = AtomicReference<CachedWeather?>(null)

    fun getLatestWeather(forceRefresh: Boolean = false): WeatherRecord? {
        val cached = cache.get()
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt)) {
            return cached.record
        }

        return try {
            val normalized = weatherProviders.asSequence().mapNotNull { provider ->
                runCatching {
                    provider.fetch()?.let {
                        normalizationService.toWeather(it, provider.providerName, provider.endpoint)
                    }
                }.onFailure { ex ->
                    logger.warn("Weather provider {} failed; trying fallback", provider.providerName, ex)
                }.getOrNull()
            }.firstOrNull() ?: throw IllegalStateException("All weather providers failed")
            cache.set(CachedWeather(normalized, Instant.now()))
            normalized
        } catch (ex: Exception) {
            if (cached == null) {
                logger.warn("Weather provider failed and no cache is available", ex)
                null
            } else {
                val age = Duration.between(cached.cachedAt, Instant.now()).seconds
                if (age <= cacheTtlSeconds) {
                    logger.warn("Weather provider failed, serving cached weather ({}s old)", age)
                } else {
                    logger.warn("Weather provider failed, serving stale cached weather ({}s old)", age)
                }
                cached.record
            }
        }
    }

    fun cacheSnapshot(): WeatherRecord? = cache.get()?.record

    private data class CachedWeather(
        val record: WeatherRecord,
        val cachedAt: Instant
    )

    private fun isExpired(cachedAt: Instant): Boolean =
        Duration.between(cachedAt, Instant.now()).seconds > cacheTtlSeconds
}
