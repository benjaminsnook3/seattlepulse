package com.seattlepulse.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class SeattlePulseBackendApplication

fun main(args: Array<String>) {
    runApplication<SeattlePulseBackendApplication>(*args)
}
