package com.sandro.asterumscheduler.event.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.event.application.EventAllParticipantsUpdateRequest
import com.sandro.asterumscheduler.event.application.EventAllTimeUpdateRequest
import com.sandro.asterumscheduler.event.application.EventAllTitleUpdateRequest
import com.sandro.asterumscheduler.event.application.EventCreateRequest
import com.sandro.asterumscheduler.event.application.EventInstanceDetail
import com.sandro.asterumscheduler.event.application.EventInstanceSummary
import com.sandro.asterumscheduler.event.application.EventSingleUpdateRequest
import com.sandro.asterumscheduler.event.application.EventThisAndFutureParticipantsUpdateRequest
import com.sandro.asterumscheduler.event.application.EventThisAndFutureTimeUpdateRequest
import com.sandro.asterumscheduler.event.application.EventThisAndFutureTitleUpdateRequest
import com.sandro.asterumscheduler.event.application.EventThisOnlyParticipantsUpdateRequest
import com.sandro.asterumscheduler.event.application.EventThisOnlyUpdateRequest
import com.sandro.asterumscheduler.event.application.MonthlyEventQuery
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/api/events")
@Tag(name = "Events", description = "일정 관리 API")
interface EventApi {

    @Operation(summary = "월간 일정 조회")
    @GetMapping
    fun monthly(@ModelAttribute @Valid query: MonthlyEventQuery): ApiResponse<List<EventInstanceSummary>>

    @Operation(summary = "일정 상세 조회")
    @GetMapping("/instances/{id}")
    fun detail(@PathVariable id: Long): ApiResponse<EventInstanceDetail>

    @Operation(summary = "일정 생성")
    @PostMapping
    fun create(@RequestBody @Valid request: EventCreateRequest): ApiResponse<EventCreateResponse>

    @Operation(summary = "단일 일정 삭제")
    @DeleteMapping("/instances/{id}")
    fun deleteSingle(@PathVariable id: Long): ApiResponse<Unit>

    @Operation(summary = "반복 일정 삭제 — 이 일정만")
    @DeleteMapping("/instances/{id}/this-only")
    fun deleteThisOnly(@PathVariable id: Long): ApiResponse<Unit>

    @Operation(summary = "반복 일정 삭제 — 전체")
    @DeleteMapping("/instances/{id}/all")
    fun deleteAll(@PathVariable id: Long): ApiResponse<Unit>

    @Operation(summary = "반복 일정 삭제 — 이 일정 및 향후")
    @DeleteMapping("/instances/{id}/this-and-future")
    fun deleteThisAndFuture(@PathVariable id: Long): ApiResponse<Unit>

    @Operation(summary = "단일 일정 수정")
    @PatchMapping("/instances/{id}")
    fun updateSingle(@PathVariable id: Long, @RequestBody @Valid request: EventSingleUpdateRequest): ApiResponse<Unit>

    @Operation(summary = "반복 일정 수정 — 이 일정만")
    @PatchMapping("/instances/{id}/this-only")
    fun updateThisOnly(@PathVariable id: Long, @RequestBody @Valid request: EventThisOnlyUpdateRequest): ApiResponse<Unit>

    @Operation(summary = "반복 일정 수정 — 전체 (제목)")
    @PatchMapping("/instances/{id}/all/title")
    fun updateAllTitle(@PathVariable id: Long, @RequestBody @Valid request: EventAllTitleUpdateRequest): ApiResponse<Unit>

    @Operation(summary = "반복 일정 수정 — 전체 (시간/반복규칙)")
    @PatchMapping("/instances/{id}/all/time")
    fun updateAllTime(@PathVariable id: Long, @RequestBody @Valid request: EventAllTimeUpdateRequest): ApiResponse<Unit>

    @Operation(summary = "반복 일정 수정 — 이 일정 및 향후 (제목)")
    @PatchMapping("/instances/{id}/this-and-future/title")
    fun updateTitleThisAndFuture(@PathVariable id: Long, @RequestBody @Valid request: EventThisAndFutureTitleUpdateRequest): ApiResponse<Unit>

    @Operation(summary = "반복 일정 수정 — 이 일정 및 향후 (시간/반복규칙)")
    @PatchMapping("/instances/{id}/this-and-future/time")
    fun updateTimeThisAndFuture(@PathVariable id: Long, @RequestBody @Valid request: EventThisAndFutureTimeUpdateRequest): ApiResponse<Unit>

    @Operation(summary = "참여자 수정 — 전체")
    @PatchMapping("/instances/{id}/participants/all")
    fun updateParticipantsAll(@PathVariable id: Long, @RequestBody @Valid request: EventAllParticipantsUpdateRequest): ApiResponse<Unit>

    @Operation(summary = "참여자 수정 — 이 일정 및 향후")
    @PatchMapping("/instances/{id}/participants/this-and-future")
    fun updateParticipantsThisAndFuture(@PathVariable id: Long, @RequestBody @Valid request: EventThisAndFutureParticipantsUpdateRequest): ApiResponse<Unit>

    @Operation(summary = "참여자 수정 — 이 일정만")
    @PatchMapping("/instances/{id}/participants/this-only")
    fun updateParticipantsThisOnly(@PathVariable id: Long, @RequestBody @Valid request: EventThisOnlyParticipantsUpdateRequest): ApiResponse<Unit>
}
