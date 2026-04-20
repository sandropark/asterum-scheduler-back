package com.sandro.asterumscheduler.event.presentation

import com.sandro.asterumscheduler.common.advice.GlobalExceptionHandler
import com.sandro.asterumscheduler.event.application.EventResponse
import com.sandro.asterumscheduler.event.application.EventService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime

@WebMvcTest(EventController::class)
@Import(GlobalExceptionHandler::class)
class EventControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var eventService: EventService

    @Test
    fun `POST events - 일정 생성 성공 시 201을 반환한다`() {
        val response = EventResponse(
            id = 1L,
            title = "팀 회의",
            startTime = LocalDateTime.of(2026, 4, 21, 9, 0),
            endTime = LocalDateTime.of(2026, 4, 21, 10, 0),
            locationId = 1L,
            notes = "주간 회의",
            creatorId = 1L,
            participants = emptyList(),
        )
        whenever(eventService.create(any())).thenReturn(response)

        mockMvc.post("/events") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"title":"팀 회의",
                "startTime":"2026-04-21T09:00:00",
                "endTime":"2026-04-21T10:00:00",
                "locationId":1,
                "notes":"주간 회의",
                "creatorId":1}""".trimMargin()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.id") { value(1) }
            jsonPath("$.data.title") { value("팀 회의") }
        }.andDo { print() }
    }

    @Test
    fun `POST events - 필수 파라미터 누락`() {
        mockMvc.post("/events") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"title": null,
                "startTime":"2026-04-21T09:00:00",
                "endTime":"2026-04-21T10:00:00",
                "locationId":1,
                "notes":"주간 회의",
                "creatorId":1}""".trimMargin()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value("INVALID_INPUT") }
            jsonPath("$.error.message") { exists() }
        }.andDo { print() }
    }

    @Test
    fun `POST events - 종료시간이 시작시간보다 빠르면 400을 반환한다`() {
        mockMvc.post("/events") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"title":"팀 회의",
                "startTime":"2026-04-21T10:00:00",
                "endTime":"2026-04-21T09:00:00",
                "locationId":1,
                "creatorId":1}""".trimMargin()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value("INVALID_INPUT") }
            jsonPath("$.error.message") {
                value(org.hamcrest.Matchers.containsString("종료 시간은 시작 시간과 같거나 이후여야 합니다."))
            }
        }.andDo { print() }
    }

    @Test
    fun `PUT events - 이벤트 수정 성공 시 200을 반환한다`() {
        val response = EventResponse(
            id = 1L,
            title = "수정된 제목",
            startTime = LocalDateTime.of(2026, 4, 22, 11, 0),
            endTime = LocalDateTime.of(2026, 4, 22, 12, 0),
            locationId = 2L,
            notes = "수정된 노트",
            creatorId = 1L,
            participants = emptyList(),
        )
        whenever(eventService.update(org.mockito.kotlin.eq(1L), any())).thenReturn(response)

        mockMvc.put("/events/1") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"title":"수정된 제목",
                "startTime":"2026-04-22T11:00:00",
                "endTime":"2026-04-22T12:00:00",
                "locationId":2,
                "notes":"수정된 노트"}""".trimMargin()
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.title") { value("수정된 제목") }
            jsonPath("$.data.locationId") { value(2) }
        }.andDo { print() }
    }

    @Test
    fun `DELETE events - 이벤트 삭제 성공 시 204를 반환한다`() {
        mockMvc.delete("/events/1").andExpect {
            status { isNoContent() }
        }.andDo { print() }
    }
}
