package com.seattlepulse.backend.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class StartupWarmup(
    private val ingestionService: IngestionService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun warmup() {
        logger.info("Running initial Seattle Pulse ingestion")
        ingestionService.refreshAll()
    }
}
