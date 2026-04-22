package com.sandro.asterumscheduler.event.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.event.application.EventAllTimeUpdateRequest
import com.sandro.asterumscheduler.event.application.EventAllTitleUpdateRequest
import com.sandro.asterumscheduler.event.application.EventCreateRequest
import com.sandro.asterumscheduler.event.application.EventInstanceDetail
import com.sandro.asterumscheduler.event.application.EventInstanceSummary
import com.sandro.asterumscheduler.event.application.EventQueryService
import com.sandro.asterumscheduler.event.application.EventService
import com.sandro.asterumscheduler.event.application.EventSingleUpdateRequest
import com.sandro.asterumscheduler.event.application.EventThisAndFutureTimeUpdateRequest
import com.sandro.asterumscheduler.event.application.EventThisAndFutureTitleUpdateRequest
import com.sandro.asterumscheduler.event.application.EventThisOnlyUpdateRequest
import com.sandro.asterumscheduler.event.application.MonthlyEventQuery
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/events")
class EventController(
    private val eventService: EventService,
    private val eventQueryService: EventQueryService,
) {
    @GetMapping
    fun monthly(
        @ModelAttribute @Valid query: MonthlyEventQuery,
    ): ApiResponse<List<EventInstanceSummary>> =
        ApiResponse.ok(eventQueryService.findMonthly(query))

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

    @PatchMapping("/instances/{id}")
    fun updateSingle(
        @PathVariable id: Long,
        @RequestBody @Valid request: EventSingleUpdateRequest,
    ): ApiResponse<Unit> {
        eventService.updateSingle(id, request)
        return ApiResponse.ok()
    }

    @PatchMapping("/instances/{id}/this-only")
    fun updateThisOnly(
        @PathVariable id: Long,
        @RequestBody @Valid request: EventThisOnlyUpdateRequest,
    ): ApiResponse<Unit> {
        eventService.updateThisOnly(id, request)
        return ApiResponse.ok()
    }

    @PatchMapping("/instances/{id}/all/title")
    fun updateAllTitle(
        @PathVariable id: Long,
        @RequestBody @Valid request: EventAllTitleUpdateRequest,
    ): ApiResponse<Unit> {
        eventService.updateAllTitle(id, request)
        return ApiResponse.ok()
    }

    @PatchMapping("/instances/{id}/all/time")
    fun updateAllTime(
        @PathVariable id: Long,
        @RequestBody @Valid request: EventAllTimeUpdateRequest,
    ): ApiResponse<Unit> {
        eventService.updateAllTime(id, request)
        return ApiResponse.ok()
    }

    @PatchMapping("/instances/{id}/this-and-future/title")
    fun updateTitleThisAndFuture(
        @PathVariable id: Long,
        @RequestBody @Valid request: EventThisAndFutureTitleUpdateRequest,
    ): ApiResponse<Unit> {
        eventService.updateTitleThisAndFuture(id, request)
        return ApiResponse.ok()
    }

    @PatchMapping("/instances/{id}/this-and-future/time")
    fun updateTimeThisAndFuture(
        @PathVariable id: Long,
        @RequestBody @Valid request: EventThisAndFutureTimeUpdateRequest,
    ): ApiResponse<Unit> {
        eventService.updateTimeThisAndFuture(id, request)
        return ApiResponse.ok()
    }
}
