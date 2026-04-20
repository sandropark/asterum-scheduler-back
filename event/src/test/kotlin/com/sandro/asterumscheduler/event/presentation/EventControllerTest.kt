package com.sandro.asterumscheduler.event.presentation

import com.sandro.asterumscheduler.event.application.EventResponse
import com.sandro.asterumscheduler.event.application.EventService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@WebMvcTest(EventController::class)
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
        }.andDo { print() }
    }

    @Test
    fun `DELETE events - 이벤트 삭제 성공 시 204를 반환한다`() {
        mockMvc.delete("/events/1").andExpect {
            status { isNoContent() }
        }.andDo { print() }
    }
}
