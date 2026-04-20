package com.sandro.asterumscheduler.location.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.location.application.LocationAvailabilityRequest
import com.sandro.asterumscheduler.location.application.LocationService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RestController

@RestController
class LocationController(private val locationService: LocationService) : LocationApi {

    @GetMapping("/locations")
    override fun getAvailableLocations(
        @Valid @ModelAttribute request: LocationAvailabilityRequest,
    ): ApiResponse<List<LocationResponse>> =
        ApiResponse.ok(locationService.findAll(request))
}
