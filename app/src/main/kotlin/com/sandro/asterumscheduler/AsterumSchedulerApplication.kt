package com.sandro.asterumscheduler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = ["com.sandro.asterumscheduler"])
@EnableJpaAuditing
@EnableScheduling
class AsterumSchedulerApplication

fun main(args: Array<String>) {
    runApplication<AsterumSchedulerApplication>(*args)
}
