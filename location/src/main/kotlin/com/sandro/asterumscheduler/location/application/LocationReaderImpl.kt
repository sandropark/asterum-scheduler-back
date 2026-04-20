package com.sandro.asterumscheduler.location.application

import com.sandro.asterumscheduler.common.location.LocationReader
import com.sandro.asterumscheduler.location.infra.LocationRepository
import org.springframework.stereotype.Component

@Component
class LocationReaderImpl(private val locationRepository: LocationRepository) : LocationReader {
    override fun existsById(locationId: Long) = locationRepository.existsById(locationId)
}
