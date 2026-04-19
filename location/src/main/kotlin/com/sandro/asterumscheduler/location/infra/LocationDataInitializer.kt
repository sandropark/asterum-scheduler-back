package com.sandro.asterumscheduler.location.infra

import com.sandro.asterumscheduler.location.domain.Location
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Profile("dev")
@Component
class LocationDataInitializer(
    private val locationRepository: LocationRepository,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (locationRepository.count() > 0) return

        locationRepository.saveAll(
            listOf(
                Location(name = "대회의실", capacity = 20),
                Location(name = "소회의실 A", capacity = 8),
                Location(name = "소회의실 B", capacity = 8),
                Location(name = "교육실", capacity = 30),
            )
        )
    }
}
