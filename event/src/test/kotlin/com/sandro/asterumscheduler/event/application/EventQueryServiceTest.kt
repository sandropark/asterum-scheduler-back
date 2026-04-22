package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.user.UserInfo
import com.sandro.asterumscheduler.common.user.UserReader
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstance
import com.sandro.asterumscheduler.event.domain.EventParticipant
import com.sandro.asterumscheduler.event.domain.InstanceParticipant
import com.sandro.asterumscheduler.event.domain.assignIdForTest
import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventParticipantRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import com.sandro.asterumscheduler.event.infra.InstanceParticipantRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EventQueryServiceTest {

    private val eventRepository = mockk<EventRepository>()
    private val eventInstanceRepository = mockk<EventInstanceRepository>()
    private val eventParticipantRepository = mockk<EventParticipantRepository>()
    private val instanceParticipantRepository = mockk<InstanceParticipantRepository>()
    private val userReader = mockk<UserReader>()
    private val service = EventQueryService(
        eventRepository, eventInstanceRepository, eventParticipantRepository, instanceParticipantRepository, userReader,
    )

    @Test
    fun `월간 조회 시 instance_title 이 있으면 그 값을, 없으면 events_title 을 반환한다`() {
        val from = LocalDateTime.of(2026, 5, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 1, 0, 0)

        val event1 = Event(title = "시리즈 제목", startAt = from, endAt = from.plusHours(1))
            .also { it.assignIdForTest(10L) }
        val event2 = Event(title = "다른 시리즈", startAt = from.plusDays(1), endAt = from.plusDays(1).plusHours(1))
            .also { it.assignIdForTest(20L) }

        val instance1 = EventInstance(
            eventId = 10L,
            startAt = from,
            endAt = from.plusHours(1),
            title = "오버라이드",
        ).also { it.assignIdForTest(1L) }
        val instance2 = EventInstance(
            eventId = 20L,
            startAt = from.plusDays(1),
            endAt = from.plusDays(1).plusHours(1),
            title = null,
        ).also { it.assignIdForTest(2L) }

        every {
            eventInstanceRepository.findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(from, to)
        } returns listOf(instance1, instance2)
        every {
            eventRepository.findAllById(match<Iterable<Long>> { it.toSet() == setOf(10L, 20L) })
        } returns listOf(event1, event2)

        val result = service.findMonthly(MonthlyEventQuery(year = 2026, month = 5))

        assertEquals(2, result.size)
        assertEquals(
            EventInstanceSummary(id = 1L, title = "오버라이드", startAt = from, endAt = from.plusHours(1)),
            result[0],
        )
        assertEquals(
            EventInstanceSummary(
                id = 2L,
                title = "다른 시리즈",
                startAt = from.plusDays(1),
                endAt = from.plusDays(1).plusHours(1),
            ),
            result[1],
        )
    }

    @Test
    fun `상세 조회 시 instance_title 이 있으면 그 값, 없으면 events_title 을 반환한다`() {
        val startAt = LocalDateTime.of(2026, 5, 10, 9, 0)
        val endAt = startAt.plusHours(1)
        val event = Event(title = "원본 제목", startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(100L) }
        val overridden = EventInstance(eventId = 100L, startAt = startAt, endAt = endAt, title = "오버라이드 제목")
            .also { it.assignIdForTest(1L) }
        val nonOverridden = EventInstance(eventId = 100L, startAt = startAt, endAt = endAt, title = null)
            .also { it.assignIdForTest(2L) }

        every { eventInstanceRepository.findById(1L) } returns Optional.of(overridden)
        every { eventInstanceRepository.findById(2L) } returns Optional.of(nonOverridden)
        every { eventRepository.findById(100L) } returns Optional.of(event)
        every { eventParticipantRepository.findAllByEventId(100L) } returns emptyList()

        assertEquals("오버라이드 제목", service.findDetail(1L).title)
        assertEquals("원본 제목", service.findDetail(2L).title)
    }

    @Test
    fun `상세 조회 응답에 events_rrule 이 포함된다 (반복 일정)`() {
        val startAt = LocalDateTime.of(2026, 5, 10, 9, 0)
        val endAt = startAt.plusHours(1)
        val event = Event(
            title = "주간 회의",
            startAt = startAt,
            endAt = endAt,
            rrule = "FREQ=WEEKLY;BYDAY=MO",
        ).also { it.assignIdForTest(200L) }
        val instance = EventInstance(eventId = 200L, startAt = startAt, endAt = endAt, title = null)
            .also { it.assignIdForTest(10L) }

        every { eventInstanceRepository.findById(10L) } returns Optional.of(instance)
        every { eventRepository.findById(200L) } returns Optional.of(event)
        every { eventParticipantRepository.findAllByEventId(200L) } returns emptyList()

        val result = service.findDetail(10L)

        assertEquals(
            EventInstanceDetail(
                id = 10L,
                title = "주간 회의",
                startAt = startAt,
                endAt = endAt,
                eventStartAt = startAt,
                rrule = "FREQ=WEEKLY;BYDAY=MO",
                participants = emptyList(),
            ),
            result,
        )
    }

    @Test
    fun `상세 조회 응답의 rrule 은 단일 일정이면 null 이다`() {
        val startAt = LocalDateTime.of(2026, 5, 10, 9, 0)
        val endAt = startAt.plusHours(1)
        val event = Event(title = "일회성", startAt = startAt, endAt = endAt, rrule = null)
            .also { it.assignIdForTest(300L) }
        val instance = EventInstance(eventId = 300L, startAt = startAt, endAt = endAt, title = null)
            .also { it.assignIdForTest(20L) }

        every { eventInstanceRepository.findById(20L) } returns Optional.of(instance)
        every { eventRepository.findById(300L) } returns Optional.of(event)
        every { eventParticipantRepository.findAllByEventId(300L) } returns emptyList()

        assertNull(service.findDetail(20L).rrule)
    }

    @Test
    fun `instance 가 없거나 삭제된 경우 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> { service.findDetail(999L) }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `상세 조회 — hasOverrideParticipants=false 면 event_participants 에서 userIds 반환`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 9, 0)
        val event = Event(title = "회의", startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(10L) }
        val instance = EventInstance(eventId = 10L, startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(1L) }

        every { eventInstanceRepository.findById(1L) } returns Optional.of(instance)
        every { eventRepository.findById(10L) } returns Optional.of(event)
        every { eventParticipantRepository.findAllByEventId(10L) } returns listOf(
            EventParticipant(eventId = 10L, userId = 100L),
            EventParticipant(eventId = 10L, userId = 200L),
        )
        every { userReader.findByIds(setOf(100L, 200L)) } returns listOf(UserInfo(100L, "Alice"), UserInfo(200L, "Bob"))

        val result = service.findDetail(1L)

        assertEquals(listOf(ParticipantSummary(100L, "Alice"), ParticipantSummary(200L, "Bob")), result.participants)
    }

    @Test
    fun `상세 조회 — hasOverrideParticipants=true 면 instance_participants 에서 userIds 반환`() {
        val startAt = LocalDateTime.of(2026, 5, 2, 9, 0)
        val event = Event(title = "회의", startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(20L) }
        val instance = EventInstance(
            eventId = 20L,
            startAt = startAt,
            endAt = startAt.plusHours(1),
            hasOverrideParticipants = true,
        ).also { it.assignIdForTest(2L) }

        every { eventInstanceRepository.findById(2L) } returns Optional.of(instance)
        every { eventRepository.findById(20L) } returns Optional.of(event)
        every { instanceParticipantRepository.findAllByInstanceId(2L) } returns listOf(
            InstanceParticipant(instanceId = 2L, userId = 300L),
        )
        every { userReader.findByIds(setOf(300L)) } returns listOf(UserInfo(300L, "Carol"))

        val result = service.findDetail(2L)

        assertEquals(listOf(ParticipantSummary(300L, "Carol")), result.participants)
    }

    @Test
    fun `상세 조회 — 참여자 없으면 빈 리스트 반환`() {
        val startAt = LocalDateTime.of(2026, 5, 3, 9, 0)
        val event = Event(title = "회의", startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(30L) }
        val instance = EventInstance(eventId = 30L, startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(3L) }

        every { eventInstanceRepository.findById(3L) } returns Optional.of(instance)
        every { eventRepository.findById(30L) } returns Optional.of(event)
        every { eventParticipantRepository.findAllByEventId(30L) } returns emptyList()

        val result = service.findDetail(3L)

        assertEquals(emptyList(), result.participants)
    }

    @Test
    fun `상세 조회 — 팀 참여자가 있으면 계층 구조로 반환된다`() {
        val startAt = LocalDateTime.of(2026, 6, 1, 9, 0)
        val event = Event(title = "팀 회의", startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(40L) }
        val instance = EventInstance(eventId = 40L, startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(4L) }

        every { eventInstanceRepository.findById(4L) } returns Optional.of(instance)
        every { eventRepository.findById(40L) } returns Optional.of(event)
        every { eventParticipantRepository.findAllByEventId(40L) } returns listOf(
            EventParticipant(eventId = 40L, userId = 10L),
        )
        every { userReader.findByIds(setOf(10L)) } returns listOf(UserInfo(10L, "팀A", isTeam = true))
        every { userReader.findMembersByTeamIds(setOf(10L)) } returns mapOf(
            10L to listOf(UserInfo(1L, "멤버1"), UserInfo(2L, "멤버2")),
        )

        val result = service.findDetail(4L)

        assertEquals(1, result.participants.size)
        val team = result.participants[0]
        assertEquals(ParticipantSummary(id = 10L, name = "팀A", isTeam = true, members = listOf(
            ParticipantSummary(id = 1L, name = "멤버1"),
            ParticipantSummary(id = 2L, name = "멤버2"),
        )), team)
    }

    @Test
    fun `상세 조회 — 팀과 개인이 혼재하면 각각 올바른 계층 구조로 반환된다`() {
        val startAt = LocalDateTime.of(2026, 6, 2, 9, 0)
        val event = Event(title = "혼합 회의", startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(50L) }
        val instance = EventInstance(eventId = 50L, startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(5L) }

        every { eventInstanceRepository.findById(5L) } returns Optional.of(instance)
        every { eventRepository.findById(50L) } returns Optional.of(event)
        every { eventParticipantRepository.findAllByEventId(50L) } returns listOf(
            EventParticipant(eventId = 50L, userId = 10L),
            EventParticipant(eventId = 50L, userId = 200L),
        )
        every { userReader.findByIds(setOf(10L, 200L)) } returns listOf(
            UserInfo(10L, "팀A", isTeam = true),
            UserInfo(200L, "개인B"),
        )
        every { userReader.findMembersByTeamIds(setOf(10L)) } returns mapOf(
            10L to listOf(UserInfo(1L, "멤버1")),
        )

        val result = service.findDetail(5L)

        assertEquals(2, result.participants.size)
        val team = result.participants.first { it.id == 10L }
        assertEquals(true, team.isTeam)
        assertEquals(1, team.members.size)
        assertEquals("멤버1", team.members[0].name)

        val individual = result.participants.first { it.id == 200L }
        assertEquals(false, individual.isTeam)
        assertEquals(emptyList(), individual.members)
    }

}
