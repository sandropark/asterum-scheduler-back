package com.sandro.asterumscheduler.location.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.location.application.LocationAvailabilityRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Location", description = "장소 API")
interface LocationApi {

    @Operation(summary = "예약 가능한 장소 목록 조회")
    @SwaggerApiResponse(responseCode = "200", description = "성공")
    fun getAvailableLocations(request: LocationAvailabilityRequest): ApiResponse<List<LocationResponse>>
}
