package com.sandro.asterumscheduler.event.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandro.asterumscheduler.event.application.EventCreateRequest
import com.sandro.asterumscheduler.event.application.EventService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.context.bean.`override`.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class EventControllerIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val eventService: EventService,
    private val em: EntityManager,
) {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
    }

    @Test
    fun `GET api_events - 월간 조회 200 성공 응답`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val end = start.plusHours(1)
        eventService.create(EventCreateRequest(title = "회의", startAt = start, endAt = end))
        em.flush(); em.clear()

        mockMvc.perform(
            get("/api/events")
                .param("from", "2026-05-01T00:00:00")
                .param("to", "2026-06-01T00:00:00")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].title").value("회의"))
    }

    @Test
    fun `GET api_events_instances_id - 상세 조회 200 성공 응답`() {
        val start = LocalDateTime.of(2026, 7, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "상세", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=2")
        )
        em.flush(); em.clear()

        val instanceId = em
            .createNativeQuery("SELECT id FROM events_instances WHERE event_id = :eid ORDER BY start_at LIMIT 1")
            .setParameter("eid", event.id)
            .singleResult as Number

        mockMvc.perform(get("/api/events/instances/${instanceId.toLong()}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("상세"))
            .andExpect(jsonPath("$.data.rrule").value("FREQ=DAILY;COUNT=2"))
    }

    @Test
    fun `GET api_events_instances_id - 존재하지 않으면 404 NOT_FOUND`() {
        mockMvc.perform(get("/api/events/instances/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `POST api_events - 생성 200 성공 응답에 eventId`() {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "title" to "새 이벤트",
                "startAt" to "2026-08-01T10:00:00",
                "endAt" to "2026-08-01T11:00:00",
            )
        )

        mockMvc.perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.eventId").isNumber)
    }

    @Test
    fun `DELETE api_events_instances_id - 단일 일정 삭제 시 상세 조회가 404`() {
        val start = LocalDateTime.of(2026, 9, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(EventCreateRequest(title = "단일", startAt = start, endAt = end))
        em.flush(); em.clear()
        val instanceId = singleInstanceId(event.id!!)

        mockMvc.perform(delete("/api/events/instances/$instanceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
        em.flush(); em.clear()

        mockMvc.perform(get("/api/events/instances/$instanceId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE api_events_instances_id_this-only - 해당 instance 만 404, event 는 살아있다`() {
        val start = LocalDateTime.of(2026, 10, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "일일", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()
        val middle = middleInstanceId(event.id!!)
        val firstId = firstInstanceId(event.id!!)

        mockMvc.perform(delete("/api/events/instances/$middle/this-only"))
            .andExpect(status().isOk)
        em.flush(); em.clear()

        mockMvc.perform(get("/api/events/instances/$middle"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/events/instances/$firstId"))
            .andExpect(status().isOk)
    }

    @Test
    fun `DELETE api_events_instances_id_all - 모든 인스턴스 404`() {
        val start = LocalDateTime.of(2026, 11, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "모두", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=2")
        )
        em.flush(); em.clear()
        val firstId = firstInstanceId(event.id!!)
        val lastId = lastInstanceId(event.id!!)

        mockMvc.perform(delete("/api/events/instances/$firstId/all"))
            .andExpect(status().isOk)
        em.flush(); em.clear()

        mockMvc.perform(get("/api/events/instances/$firstId"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/events/instances/$lastId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE api_events_instances_id_this-and-future - target 이후 404, 이전은 살아있다`() {
        val start = LocalDateTime.of(2026, 12, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "앞뒤", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=4")
        )
        em.flush(); em.clear()
        val ids = orderedInstanceIds(event.id!!)

        mockMvc.perform(delete("/api/events/instances/${ids[2]}/this-and-future"))
            .andExpect(status().isOk)
        em.flush(); em.clear()

        mockMvc.perform(get("/api/events/instances/${ids[1]}")).andExpect(status().isOk)
        mockMvc.perform(get("/api/events/instances/${ids[2]}")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/events/instances/${ids[3]}")).andExpect(status().isNotFound)
    }

    @Test
    fun `POST api_events - title 이 빈 문자열이면 400 INVALID_INPUT`() {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "title" to "",
                "startAt" to "2026-08-01T10:00:00",
                "endAt" to "2026-08-01T11:00:00",
            )
        )

        mockMvc.perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
    }

    @Test
    fun `PATCH api_events_instances_id - 단일 일정 제목 변경 후 상세에 반영`() {
        val start = LocalDateTime.of(2027, 1, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(EventCreateRequest(title = "전", startAt = start, endAt = end))
        em.flush(); em.clear()
        val id = singleInstanceId(event.id!!)

        val body = objectMapper.writeValueAsString(
            mapOf("title" to "후", "startAt" to start.toString(), "endAt" to end.toString())
        )
        mockMvc.perform(
            patch("/api/events/instances/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        mockMvc.perform(get("/api/events/instances/$id"))
            .andExpect(jsonPath("$.data.title").value("후"))
    }

    @Test
    fun `PATCH api_events_instances_id_this-only - 해당 instance 만 오버라이드`() {
        val start = LocalDateTime.of(2027, 2, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()
        val ids = orderedInstanceIds(event.id!!)

        val middleStart = start.plusDays(1)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "title" to "오버라이드",
                "startAt" to middleStart.toString(),
                "endAt" to middleStart.plusHours(1).toString(),
            )
        )
        mockMvc.perform(
            patch("/api/events/instances/${ids[1]}/this-only")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        mockMvc.perform(get("/api/events/instances/${ids[1]}"))
            .andExpect(jsonPath("$.data.title").value("오버라이드"))
        mockMvc.perform(get("/api/events/instances/${ids[0]}"))
            .andExpect(jsonPath("$.data.title").value("원본"))
    }

    @Test
    fun `PATCH api_events_instances_id_all_title - 모든 instance 의 제목이 새 값으로 보인다`() {
        val start = LocalDateTime.of(2027, 3, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=2")
        )
        em.flush(); em.clear()
        val ids = orderedInstanceIds(event.id!!)

        val body = objectMapper.writeValueAsString(mapOf("title" to "새 제목"))
        mockMvc.perform(
            patch("/api/events/instances/${ids[0]}/all/title")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        mockMvc.perform(get("/api/events/instances/${ids[0]}"))
            .andExpect(jsonPath("$.data.title").value("새 제목"))
        mockMvc.perform(get("/api/events/instances/${ids[1]}"))
            .andExpect(jsonPath("$.data.title").value("새 제목"))
    }

    @Test
    fun `PATCH api_events_instances_id_all_time - rrule 과 시간 모두 교체된다`() {
        val start = LocalDateTime.of(2027, 4, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()
        val first = firstInstanceId(event.id!!)

        val newStart = LocalDateTime.of(2027, 5, 1, 14, 0)
        val newEnd = newStart.plusHours(2)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "startAt" to newStart.toString(),
                "endAt" to newEnd.toString(),
                "rrule" to "FREQ=WEEKLY;COUNT=2",
            )
        )
        mockMvc.perform(
            patch("/api/events/instances/$first/all/time")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        val newIds = orderedInstanceIds(event.id!!)
        kotlin.test.assertEquals(2, newIds.size)
        mockMvc.perform(get("/api/events/instances/${newIds[0]}"))
            .andExpect(jsonPath("$.data.startAt").value("2027-05-01T14:00:00"))
            .andExpect(jsonPath("$.data.rrule").value("FREQ=WEEKLY;COUNT=2"))
    }

    @Test
    fun `PATCH api_events_instances_id_this-and-future_title - 새 event 가 생기고 target 이후 제목이 새 값으로 보인다`() {
        val start = LocalDateTime.of(2027, 6, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "구", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()
        val ids = orderedInstanceIds(event.id!!)

        val body = objectMapper.writeValueAsString(mapOf("title" to "신"))
        mockMvc.perform(
            patch("/api/events/instances/${ids[1]}/this-and-future/title")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        mockMvc.perform(get("/api/events/instances/${ids[0]}"))
            .andExpect(jsonPath("$.data.title").value("구"))
        mockMvc.perform(get("/api/events/instances/${ids[1]}"))
            .andExpect(jsonPath("$.data.title").value("신"))
        mockMvc.perform(get("/api/events/instances/${ids[2]}"))
            .andExpect(jsonPath("$.data.title").value("신"))
    }

    @Test
    fun `PATCH api_events_instances_id_this-and-future_time - 새 event 가 생기고 target 이후 새 시간으로 재생성`() {
        val start = LocalDateTime.of(2027, 7, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()
        val ids = orderedInstanceIds(event.id!!)

        val newStart = LocalDateTime.of(2027, 8, 1, 14, 0)
        val newEnd = newStart.plusHours(2)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "startAt" to newStart.toString(),
                "endAt" to newEnd.toString(),
                "rrule" to "FREQ=WEEKLY;COUNT=2",
            )
        )
        mockMvc.perform(
            patch("/api/events/instances/${ids[1]}/this-and-future/time")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        // 첫 번째 instance 는 old event 로 유지
        mockMvc.perform(get("/api/events/instances/${ids[0]}"))
            .andExpect(status().isOk)
        // target 원본 (ids[1]) 은 hard-delete 되어 404
        mockMvc.perform(get("/api/events/instances/${ids[1]}"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH api_events_instances_id_all_title - title 이 빈 문자열이면 400 INVALID_INPUT`() {
        val start = LocalDateTime.of(2027, 9, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=2")
        )
        em.flush(); em.clear()
        val id = firstInstanceId(event.id!!)

        val body = objectMapper.writeValueAsString(mapOf("title" to ""))
        mockMvc.perform(
            patch("/api/events/instances/$id/all/title")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
    }

    private fun singleInstanceId(eventId: Long): Long =
        (em.createNativeQuery("SELECT id FROM events_instances WHERE event_id = :eid")
            .setParameter("eid", eventId)
            .singleResult as Number).toLong()

    private fun firstInstanceId(eventId: Long): Long = orderedInstanceIds(eventId).first()

    private fun lastInstanceId(eventId: Long): Long = orderedInstanceIds(eventId).last()

    private fun middleInstanceId(eventId: Long): Long {
        val ids = orderedInstanceIds(eventId)
        return ids[ids.size / 2]
    }

    @Suppress("UNCHECKED_CAST")
    private fun orderedInstanceIds(eventId: Long): List<Long> =
        (em.createNativeQuery("SELECT id FROM events_instances WHERE event_id = :eid ORDER BY start_at")
            .setParameter("eid", eventId)
            .resultList as List<Number>).map { it.toLong() }
}
