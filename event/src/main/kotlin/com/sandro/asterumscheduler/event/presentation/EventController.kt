package com.sandro.asterumscheduler.event.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.event.application.EventCreateRequest
import com.sandro.asterumscheduler.event.application.EventInstanceDetail
import com.sandro.asterumscheduler.event.application.EventInstanceSummary
import com.sandro.asterumscheduler.event.application.EventQueryService
import com.sandro.asterumscheduler.event.application.EventService
import com.sandro.asterumscheduler.event.application.MonthlyEventQuery
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/events")
class EventController(
    private val eventService: EventService,
    private val eventQueryService: EventQueryService,
) {
    @GetMapping
    fun monthly(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
    ): ApiResponse<List<EventInstanceSummary>> =
        ApiResponse.ok(eventQueryService.findMonthly(MonthlyEventQuery(from, to)))

    @GetMapping("/instances/{id}")
    fun detail(@PathVariable id: Long): ApiResponse<EventInstanceDetail> =
        ApiResponse.ok(eventQueryService.findDetail(id))

    @PostMapping
    fun create(@RequestBody @Valid request: EventCreateRequest): ApiResponse<EventCreateResponse> {
        val event = eventService.create(request)
        return ApiResponse.ok(EventCreateResponse(event.id!!))
    }

    @DeleteMapping("/instances/{id}")
    fun deleteSingle(@PathVariable id: Long): ApiResponse<Unit> {
        eventService.deleteSingle(id)
        return ApiResponse.ok()
    }

    @DeleteMapping("/instances/{id}/this-only")
    fun deleteThisOnly(@PathVariable id: Long): ApiResponse<Unit> {
        eventService.deleteThisOnly(id)
        return ApiResponse.ok()
    }

    @DeleteMapping("/instances/{id}/all")
    fun deleteAll(@PathVariable id: Long): ApiResponse<Unit> {
        eventService.deleteAll(id)
        return ApiResponse.ok()
    }

    @DeleteMapping("/instances/{id}/this-and-future")
    fun deleteThisAndFuture(@PathVariable id: Long): ApiResponse<Unit> {
        eventService.deleteThisAndFuture(id)
        return ApiResponse.ok()
    }
}
