package com.sandro.asterumscheduler.location.application

import com.sandro.asterumscheduler.common.event.EventReader
import com.sandro.asterumscheduler.location.infra.LocationRepository
import com.sandro.asterumscheduler.location.presentation.LocationResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class LocationService(
    private val locationRepository: LocationRepository,
    private val eventReader: EventReader,
) {

    fun findAll(request: LocationAvailabilityRequest): List<LocationResponse> {
        val reservedIds = eventReader.findReservedLocationIds(request.start, request.end).toSet()
        // TODO: 슬라이싱 고려
        return locationRepository.findAll()
            .map { LocationResponse(it.id, it.name, it.capacity, available = it.id !in reservedIds) }
    }
}
