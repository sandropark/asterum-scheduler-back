package com.sandro.asterumscheduler.event.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.event.application.CreateEventRequest
import com.sandro.asterumscheduler.event.application.EventResponse
import com.sandro.asterumscheduler.event.application.EventService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
class EventController(private val eventService: EventService) : EventApi {

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    override fun createEvent(@Valid @RequestBody request: CreateEventRequest): ApiResponse<EventResponse> =
        ApiResponse.ok(eventService.create(request))

    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun deleteEvent(@PathVariable id: Long) =
        eventService.delete(id)
}
