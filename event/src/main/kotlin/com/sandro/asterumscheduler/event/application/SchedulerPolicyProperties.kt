package com.sandro.asterumscheduler.event.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("scheduler.policy")
data class SchedulerPolicyProperties(
    val expansionYears: Int = 10,
)
