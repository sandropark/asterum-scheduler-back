package com.sandro.asterumscheduler.event.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.event.application.CreateEventRequest
import com.sandro.asterumscheduler.event.application.EventResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Event", description = "이벤트 API")
interface EventApi {

    @Operation(summary = "이벤트 생성")
    @SwaggerApiResponse(responseCode = "201", description = "성공")
    fun createEvent(request: CreateEventRequest): ApiResponse<EventResponse>

    @Operation(summary = "이벤트 삭제")
    @SwaggerApiResponse(responseCode = "204", description = "성공")
    fun deleteEvent(@PathVariable id: Long)
}
