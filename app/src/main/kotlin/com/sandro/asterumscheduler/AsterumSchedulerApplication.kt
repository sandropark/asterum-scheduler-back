package com.sandro.asterumscheduler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class AsterumSchedulerApplication

fun main(args: Array<String>) {
    runApplication<AsterumSchedulerApplication>(*args)
}
