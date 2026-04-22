package com.sandro.asterumscheduler.event.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandro.asterumscheduler.event.application.EventCreateRequest
import com.sandro.asterumscheduler.event.application.EventService
import com.sandro.asterumscheduler.user.domain.User
import com.sandro.asterumscheduler.user.domain.UserMembership
import com.sandro.asterumscheduler.user.infra.UserMembershipRepository
import com.sandro.asterumscheduler.user.infra.UserRepository
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
    private val userRepository: UserRepository,
    private val userMembershipRepository: UserMembershipRepository,
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
            .andExpect(jsonPath("$.data.participants").isArray)
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

    @Test
    fun `POST api_events - startAt 이 endAt 이상이면 400 INVALID_INPUT`() {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "title" to "x",
                "startAt" to "2028-01-01T11:00:00",
                "endAt" to "2028-01-01T10:00:00",
            )
        )

        mockMvc.perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
    }

    @Test
    fun `PATCH api_events_instances_id_all_time - startAt 이 endAt 이상이면 400 INVALID_INPUT`() {
        val start = LocalDateTime.of(2028, 2, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=2")
        )
        em.flush(); em.clear()
        val id = firstInstanceId(event.id!!)

        val body = objectMapper.writeValueAsString(
            mapOf(
                "startAt" to "2028-03-01T15:00:00",
                "endAt" to "2028-03-01T14:00:00",
                "rrule" to "FREQ=WEEKLY;COUNT=2",
            )
        )
        mockMvc.perform(
            patch("/api/events/instances/$id/all/time")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
    }

    @Test
    fun `POST api_events - title 이 255자를 초과하면 400 INVALID_INPUT`() {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "title" to "a".repeat(256),
                "startAt" to "2028-01-01T10:00:00",
                "endAt" to "2028-01-01T11:00:00",
            )
        )

        mockMvc.perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
    }

    @Test
    fun `POST api_events - rrule 이 500자를 초과하면 400 INVALID_INPUT`() {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "title" to "x",
                "startAt" to "2028-01-01T10:00:00",
                "endAt" to "2028-01-01T11:00:00",
                "rrule" to "a".repeat(501),
            )
        )

        mockMvc.perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
    }

    @Test
    fun `GET api_events - from 이 to 이상이면 400 INVALID_INPUT`() {
        mockMvc.perform(
            get("/api/events")
                .param("from", "2028-06-01T00:00:00")
                .param("to", "2028-05-01T00:00:00")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
    }

    @Test
    fun `PATCH participants this-and-future — target 이후 participants 반영, 이전은 그대로`() {
        val user1 = userRepository.save(User(email = "tf1@test.com", name = "TfUser1"))
        val user2 = userRepository.save(User(email = "tf2@test.com", name = "TfUser2"))
        em.flush(); em.clear()

        val start = LocalDateTime.of(2031, 1, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "반복", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()
        val ids = orderedInstanceIds(event.id!!)

        val body = objectMapper.writeValueAsString(mapOf("userIds" to listOf(user1.id, user2.id)))
        mockMvc.perform(
            patch("/api/events/instances/${ids[1]}/participants/this-and-future")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        // 이전 instance — participants 없음
        mockMvc.perform(get("/api/events/instances/${ids[0]}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.participants.length()").value(0))

        // target 이후 — 새 participants 반영
        mockMvc.perform(get("/api/events/instances/${ids[1]}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.participants.length()").value(2))
            .andExpect(jsonPath("$.data.participants[?(@.name == 'TfUser1')]").exists())
            .andExpect(jsonPath("$.data.participants[?(@.name == 'TfUser2')]").exists())
        mockMvc.perform(get("/api/events/instances/${ids[2]}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.participants.length()").value(2))
    }

    @Test
    fun `PATCH participants this-and-future — 단일 일정이면 400 INVALID_INPUT`() {
        val start = LocalDateTime.of(2031, 2, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(EventCreateRequest(title = "단일", startAt = start, endAt = end))
        em.flush(); em.clear()
        val instanceId = singleInstanceId(event.id!!)

        val body = objectMapper.writeValueAsString(mapOf("userIds" to emptyList<Long>()))
        mockMvc.perform(
            patch("/api/events/instances/$instanceId/participants/this-and-future")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
    }

    @Test
    fun `PATCH participants all — 참여자 교체 후 상세 조회에 반영 (단일 일정)`() {
        val user1 = userRepository.save(User(email = "all1@test.com", name = "AllUser1"))
        val user2 = userRepository.save(User(email = "all2@test.com", name = "AllUser2"))
        em.flush(); em.clear()

        val start = LocalDateTime.of(2030, 1, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(EventCreateRequest(title = "단일", startAt = start, endAt = end))
        em.flush(); em.clear()
        val instanceId = singleInstanceId(event.id!!)

        val body = objectMapper.writeValueAsString(mapOf("userIds" to listOf(user1.id, user2.id)))
        mockMvc.perform(
            patch("/api/events/instances/$instanceId/participants/all")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        mockMvc.perform(get("/api/events/instances/$instanceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.participants.length()").value(2))
            .andExpect(jsonPath("$.data.participants[?(@.name == 'AllUser1')]").exists())
            .andExpect(jsonPath("$.data.participants[?(@.name == 'AllUser2')]").exists())
    }

    @Test
    fun `PATCH participants all — 반복 일정 instance_participants 오버라이드 리셋 후 event_participants 반영`() {
        val user1 = userRepository.save(User(email = "allr1@test.com", name = "RecurAll1"))
        val user2 = userRepository.save(User(email = "allr2@test.com", name = "RecurAll2"))
        em.flush(); em.clear()

        val start = LocalDateTime.of(2030, 2, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "반복", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()
        val ids = orderedInstanceIds(event.id!!)

        // 먼저 첫 번째 인스턴스에 this-only 오버라이드
        val overrideBody = objectMapper.writeValueAsString(mapOf("userIds" to listOf(user1.id)))
        mockMvc.perform(
            patch("/api/events/instances/${ids[0]}/participants/this-only")
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        // all로 교체 — 오버라이드 리셋되고 새 참여자 반영
        val allBody = objectMapper.writeValueAsString(mapOf("userIds" to listOf(user1.id, user2.id)))
        mockMvc.perform(
            patch("/api/events/instances/${ids[1]}/participants/all")
                .contentType(MediaType.APPLICATION_JSON)
                .content(allBody)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        // 모든 인스턴스에서 새 참여자 보여야 함
        for (id in ids) {
            mockMvc.perform(get("/api/events/instances/$id"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.participants.length()").value(2))
        }
    }

    @Test
    fun `PATCH participants all — 존재하지 않는 instance 는 404 NOT_FOUND`() {
        val body = objectMapper.writeValueAsString(mapOf("userIds" to emptyList<Long>()))
        mockMvc.perform(
            patch("/api/events/instances/999999/participants/all")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `PATCH participants this-only — 반복 일정 instance 참여자 오버라이드 후 상세 조회에 반영`() {
        val user1 = userRepository.save(User(email = "p1@test.com", name = "Player1"))
        val user2 = userRepository.save(User(email = "p2@test.com", name = "Player2"))
        em.flush(); em.clear()

        val start = LocalDateTime.of(2029, 3, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(
            EventCreateRequest(title = "반복", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()
        val instanceId = firstInstanceId(event.id!!)

        val body = objectMapper.writeValueAsString(mapOf("userIds" to listOf(user1.id, user2.id)))
        mockMvc.perform(
            patch("/api/events/instances/$instanceId/participants/this-only")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
        em.flush(); em.clear()

        mockMvc.perform(get("/api/events/instances/$instanceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.participants.length()").value(2))
            .andExpect(jsonPath("$.data.participants[?(@.name == 'Player1')]").exists())
            .andExpect(jsonPath("$.data.participants[?(@.name == 'Player2')]").exists())
    }

    @Test
    fun `PATCH participants this-only — 단일 일정이면 400 INVALID_INPUT`() {
        val start = LocalDateTime.of(2029, 4, 1, 10, 0)
        val end = start.plusHours(1)
        val event = eventService.create(EventCreateRequest(title = "단일", startAt = start, endAt = end))
        em.flush(); em.clear()
        val instanceId = singleInstanceId(event.id!!)

        val body = objectMapper.writeValueAsString(mapOf("userIds" to emptyList<Long>()))
        mockMvc.perform(
            patch("/api/events/instances/$instanceId/participants/this-only")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
    }

    @Test
    fun `PATCH participants this-only — 존재하지 않는 instance 는 404 NOT_FOUND`() {
        val body = objectMapper.writeValueAsString(mapOf("userIds" to emptyList<Long>()))
        mockMvc.perform(
            patch("/api/events/instances/999999/participants/this-only")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `POST 참여자 포함 생성 — 상세 조회 participants 에 이름 포함하여 반영`() {
        val user1 = userRepository.save(User(email = "a@test.com", name = "Alice"))
        val user2 = userRepository.save(User(email = "b@test.com", name = "Bob"))
        em.flush(); em.clear()

        val start = LocalDateTime.of(2029, 1, 1, 10, 0)
        val end = start.plusHours(1)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "title" to "참여자 회의",
                "startAt" to start.toString(),
                "endAt" to end.toString(),
                "userIds" to listOf(user1.id, user2.id),
            )
        )
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
        em.flush(); em.clear()

        val eventId = (em.createNativeQuery("SELECT id FROM events ORDER BY id DESC LIMIT 1")
            .singleResult as Number).toLong()
        val instanceId = firstInstanceId(eventId)

        mockMvc.perform(get("/api/events/instances/$instanceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.participants.length()").value(2))
            .andExpect(jsonPath("$.data.participants[?(@.name == 'Alice')]").exists())
            .andExpect(jsonPath("$.data.participants[?(@.name == 'Bob')]").exists())
    }

    @Test
    fun `GET api_events_instances_id — 팀이 참여자이면 팀 항목과 members 배열로 반환된다`() {
        val team = userRepository.save(User(email = "team@b.com", name = "팀B", isTeam = true))
        val member = userRepository.save(User(email = "m@b.com", name = "멤버X"))
        userMembershipRepository.save(UserMembership(teamId = team.id!!, memberId = member.id!!))

        val start = LocalDateTime.of(2027, 1, 1, 10, 0)
        val event = eventService.create(
            EventCreateRequest(title = "팀 일정", startAt = start, endAt = start.plusHours(1), userIds = setOf(team.id!!))
        )
        em.flush(); em.clear()
        val instanceId = firstInstanceId(event.id!!)

        mockMvc.perform(get("/api/events/instances/$instanceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.participants.length()").value(1))
            .andExpect(jsonPath("$.data.participants[0].id").value(team.id!!))
            .andExpect(jsonPath("$.data.participants[0].isTeam").value(true))
            .andExpect(jsonPath("$.data.participants[0].members.length()").value(1))
            .andExpect(jsonPath("$.data.participants[0].members[0].name").value("멤버X"))
    }

    @Test
    fun `GET api_events_instances_id — 팀과 개인이 혼재하면 각각 올바른 계층 구조로 반환된다`() {
        val team = userRepository.save(User(email = "team@c.com", name = "팀C", isTeam = true))
        val member = userRepository.save(User(email = "m@c.com", name = "멤버Y"))
        val individual = userRepository.save(User(email = "ind@c.com", name = "개인Z"))
        userMembershipRepository.save(UserMembership(teamId = team.id!!, memberId = member.id!!))

        val start = LocalDateTime.of(2027, 2, 1, 10, 0)
        val event = eventService.create(
            EventCreateRequest(
                title = "혼합 일정",
                startAt = start,
                endAt = start.plusHours(1),
                userIds = setOf(team.id!!, individual.id!!),
            )
        )
        em.flush(); em.clear()
        val instanceId = firstInstanceId(event.id!!)

        mockMvc.perform(get("/api/events/instances/$instanceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.participants.length()").value(2))
            .andExpect(jsonPath("$.data.participants[?(@.id == ${team.id!!})].isTeam").value(true))
            .andExpect(jsonPath("$.data.participants[?(@.id == ${team.id!!})].members[0].name").value("멤버Y"))
            .andExpect(jsonPath("$.data.participants[?(@.id == ${individual.id!!})].isTeam").value(false))
            .andExpect(jsonPath("$.data.participants[?(@.id == ${individual.id!!})].members[0]").isEmpty)
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
