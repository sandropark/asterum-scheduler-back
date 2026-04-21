package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.location.LocationReader
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstances
import com.sandro.asterumscheduler.event.domain.EventInstancesStatus
import com.sandro.asterumscheduler.event.infra.EventInstancesRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class EventServiceTest {

    @Mock
    lateinit var eventRepository: EventRepository

    @Mock
    lateinit var eventInstancesRepository: EventInstancesRepository

    @Mock
    lateinit var locationReader: LocationReader

    lateinit var eventService: EventService

    @BeforeEach
    fun setUp() {
        eventService = EventService(
            eventRepository = eventRepository,
            eventInstancesRepository = eventInstancesRepository,
            locationReader = locationReader,
            maxRecurrenceYears = 10L,
        )
    }

    @Test
    fun `장소가 없는 단일 일정을 생성하면 저장된 일정을 반환한다`() {
        val creatorId = 1L
        val request = EventCreateRequest(
            title = "테스트 일정",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
        )
        val savedEvent = Event(
            id = 1L,
            title = request.title,
            startTime = request.startTime,
            endTime = request.endTime,
            creatorId = creatorId,
        )
        whenever(eventRepository.save(any())).thenReturn(savedEvent)

        val response = eventService.create(creatorId, request)

        assertEquals(1L, response.id)
        assertEquals("테스트 일정", response.title)
        assertEquals(request.startTime, response.startTime)
        assertEquals(request.endTime, response.endTime)
        assertNull(response.locationId)
        assertEquals(creatorId, response.creatorId)

        val instanceCaptor = argumentCaptor<EventInstances>()
        verify(eventInstancesRepository).save(instanceCaptor.capture())
        val instance = instanceCaptor.firstValue
        assertEquals(savedEvent.id, instance.eventId)
        assertEquals(request.startTime.toLocalDate(), instance.dateKey)
        assertEquals(request.startTime, instance.startTime)
        assertEquals(request.endTime, instance.endTime)
        assertEquals(EventInstancesStatus.CONFIRMED, instance.status)
        assertNull(instance.locationId)
    }

    @Test
    fun `존재하지 않는 locationId로 일정 생성 시 NOT_FOUND 예외`() {
        val request = EventCreateRequest(
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            locationId = 999L,
        )
        whenever(locationReader.existsById(999L)).thenReturn(false)

        val ex = assertThrows<BusinessException> { eventService.create(1L, request) }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
        verify(eventRepository, never()).save(any())
        verify(eventInstancesRepository, never()).save(any())
    }

    @Test
    fun `장소가 있는 단일 일정 생성 시 겹치는 일정이 없으면 CONFIRMED`() {
        val creatorId = 1L
        val locationId = 10L
        val request = EventCreateRequest(
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            locationId = locationId,
        )
        val savedEvent = Event(
            id = 1L,
            title = request.title,
            startTime = request.startTime,
            endTime = request.endTime,
            locationId = locationId,
            creatorId = creatorId,
        )
        whenever(locationReader.existsById(locationId)).thenReturn(true)
        whenever(eventRepository.save(any())).thenReturn(savedEvent)
        whenever(
            eventInstancesRepository.existsOverlapByLocation(locationId, request.startTime, request.endTime)
        ).thenReturn(false)

        eventService.create(creatorId, request)

        val instanceCaptor = argumentCaptor<EventInstances>()
        verify(eventInstancesRepository).save(instanceCaptor.capture())
        assertEquals(EventInstancesStatus.CONFIRMED, instanceCaptor.firstValue.status)
        assertEquals(locationId, instanceCaptor.firstValue.locationId)
    }

    @Test
    fun `장소가 있는 단일 일정 생성 시 같은 장소에 겹치는 일정이 있으면 CONFLICT`() {
        val creatorId = 1L
        val locationId = 10L
        val request = EventCreateRequest(
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            locationId = locationId,
        )
        val savedEvent = Event(
            id = 1L,
            title = request.title,
            startTime = request.startTime,
            endTime = request.endTime,
            locationId = locationId,
            creatorId = creatorId,
        )
        whenever(locationReader.existsById(locationId)).thenReturn(true)
        whenever(eventRepository.save(any())).thenReturn(savedEvent)
        whenever(
            eventInstancesRepository.existsOverlapByLocation(locationId, request.startTime, request.endTime)
        ).thenReturn(true)

        eventService.create(creatorId, request)

        val instanceCaptor = argumentCaptor<EventInstances>()
        verify(eventInstancesRepository).save(instanceCaptor.capture())
        assertEquals(EventInstancesStatus.CONFLICT, instanceCaptor.firstValue.status)
        assertEquals(locationId, instanceCaptor.firstValue.locationId)
    }

    @Test
    fun `장소 없는 반복 일정 생성 시 rrule 전개하여 발생일마다 EventInstances 생성`() {
        val creatorId = 1L
        val request = EventCreateRequest(
            title = "데일리 스크럼",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 10, 30),
            rrule = "FREQ=DAILY;COUNT=3",
        )
        val savedEvent = Event(
            id = 1L,
            title = request.title,
            startTime = request.startTime,
            endTime = request.endTime,
            rrule = request.rrule,
            creatorId = creatorId,
        )
        whenever(eventRepository.save(any())).thenReturn(savedEvent)

        eventService.create(creatorId, request)

        val captor = argumentCaptor<EventInstances>()
        verify(eventInstancesRepository, times(3)).save(captor.capture())
        val instances = captor.allValues.sortedBy { it.startTime }
        assertEquals(LocalDateTime.of(2026, 4, 20, 10, 0), instances[0].startTime)
        assertEquals(LocalDateTime.of(2026, 4, 21, 10, 0), instances[1].startTime)
        assertEquals(LocalDateTime.of(2026, 4, 22, 10, 0), instances[2].startTime)
        instances.forEach {
            assertEquals(EventInstancesStatus.CONFIRMED, it.status)
            assertEquals(30, Duration.between(it.startTime, it.endTime).toMinutes())
            assertNull(it.locationId)
        }
    }

    @Test
    fun `무기한 rrule은 최대 기간 10년 내 발생일만 전개`() {
        val creatorId = 1L
        val startTime = LocalDateTime.of(2026, 4, 20, 10, 0)
        val request = EventCreateRequest(
            title = "무기한 데일리",
            startTime = startTime,
            endTime = startTime.plusMinutes(30),
            rrule = "FREQ=DAILY",
        )
        val savedEvent = Event(
            id = 1L,
            title = request.title,
            startTime = request.startTime,
            endTime = request.endTime,
            rrule = request.rrule,
            creatorId = creatorId,
        )
        whenever(eventRepository.save(any())).thenReturn(savedEvent)

        eventService.create(creatorId, request)

        val captor = argumentCaptor<EventInstances>()
        verify(eventInstancesRepository, org.mockito.kotlin.atLeastOnce()).save(captor.capture())
        val cutoff = startTime.plusYears(10)
        assertTrue(captor.allValues.all { !it.startTime.isAfter(cutoff) })
        assertTrue(captor.allValues.any { it.startTime.toLocalDate() == cutoff.toLocalDate() })
    }

    @Test
    fun `장소 있는 반복 일정 - 겹침 없으면 모든 인스턴스 CONFIRMED`() {
        val creatorId = 1L
        val locationId = 10L
        val request = EventCreateRequest(
            title = "매일 회의",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            locationId = locationId,
            rrule = "FREQ=DAILY;COUNT=3",
        )
        val savedEvent = Event(
            id = 1L,
            title = request.title,
            startTime = request.startTime,
            endTime = request.endTime,
            locationId = locationId,
            rrule = request.rrule,
            creatorId = creatorId,
        )
        whenever(locationReader.existsById(locationId)).thenReturn(true)
        whenever(eventRepository.save(any())).thenReturn(savedEvent)
        whenever(eventInstancesRepository.existsOverlapByLocation(eq(locationId), any(), any())).thenReturn(false)

        eventService.create(creatorId, request)

        val captor = argumentCaptor<EventInstances>()
        verify(eventInstancesRepository, times(3)).save(captor.capture())
        captor.allValues.forEach {
            assertEquals(EventInstancesStatus.CONFIRMED, it.status)
            assertEquals(locationId, it.locationId)
        }
    }

    @Test
    fun `장소 있는 반복 일정 - 일부 날짜만 겹치면 해당 인스턴스만 CONFLICT`() {
        val creatorId = 1L
        val locationId = 10L
        val conflictStart = LocalDateTime.of(2026, 4, 21, 10, 0)
        val conflictEnd = LocalDateTime.of(2026, 4, 21, 11, 0)
        val request = EventCreateRequest(
            title = "매일 회의",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            locationId = locationId,
            rrule = "FREQ=DAILY;COUNT=3",
        )
        val savedEvent = Event(
            id = 1L,
            title = request.title,
            startTime = request.startTime,
            endTime = request.endTime,
            locationId = locationId,
            rrule = request.rrule,
            creatorId = creatorId,
        )
        whenever(locationReader.existsById(locationId)).thenReturn(true)
        whenever(eventRepository.save(any())).thenReturn(savedEvent)
        whenever(eventInstancesRepository.existsOverlapByLocation(eq(locationId), any(), any())).thenReturn(false)
        whenever(eventInstancesRepository.existsOverlapByLocation(locationId, conflictStart, conflictEnd)).thenReturn(true)

        eventService.create(creatorId, request)

        val captor = argumentCaptor<EventInstances>()
        verify(eventInstancesRepository, times(3)).save(captor.capture())
        val byDate = captor.allValues.associateBy { it.startTime.toLocalDate() }
        assertEquals(EventInstancesStatus.CONFIRMED, byDate[LocalDate.of(2026, 4, 20)]!!.status)
        assertEquals(EventInstancesStatus.CONFLICT, byDate[LocalDate.of(2026, 4, 21)]!!.status)
        assertEquals(EventInstancesStatus.CONFIRMED, byDate[LocalDate.of(2026, 4, 22)]!!.status)
    }

    @Test
    fun `단일 일정 제목 수정 시 Events 제목만 변경되고 EventInstances는 그대로`() {
        val originalStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val originalEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val existingEvent = Event(
            id = 1L,
            title = "원래 제목",
            startTime = originalStart,
            endTime = originalEnd,
            creatorId = 1L,
        )
        val existingInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = originalStart.toLocalDate(),
            startTime = originalStart,
            endTime = originalEnd,
            status = EventInstancesStatus.CONFIRMED,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findFirstByEventIdAndOverrideIdIsNull(1L)).thenReturn(existingInstance)

        eventService.update(
            1L,
            EventUpdateRequest(title = "새 제목", startTime = originalStart, endTime = originalEnd),
        )

        assertEquals("새 제목", existingEvent.title)
        assertEquals(originalStart, existingEvent.startTime)
        assertEquals(originalEnd, existingEvent.endTime)
        assertEquals(originalStart, existingInstance.startTime)
        assertEquals(originalEnd, existingInstance.endTime)
    }

    @Test
    fun `존재하지 않는 일정을 수정하면 NOT_FOUND 예외`() {
        whenever(eventRepository.findById(999L)).thenReturn(Optional.empty())

        val ex = assertThrows<BusinessException> {
            eventService.update(
                999L,
                EventUpdateRequest(
                    title = "새 제목",
                    startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
                    endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
                ),
            )
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
        verify(eventInstancesRepository, never()).save(any())
    }

    @Test
    fun `단일 일정 notes 수정 시 Event notes만 변경`() {
        val originalStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val originalEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = originalStart,
            endTime = originalEnd,
            notes = "기존 메모",
            creatorId = 1L,
        )
        val existingInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = originalStart.toLocalDate(),
            startTime = originalStart,
            endTime = originalEnd,
            status = EventInstancesStatus.CONFIRMED,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findFirstByEventIdAndOverrideIdIsNull(1L)).thenReturn(existingInstance)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "회의",
                startTime = originalStart,
                endTime = originalEnd,
                notes = "새 메모",
            ),
        )

        assertEquals("새 메모", existingEvent.notes)
    }

    @Test
    fun `단일 일정 notes를 null로 수정하면 Event notes가 null로 변경`() {
        val originalStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val originalEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = originalStart,
            endTime = originalEnd,
            notes = "기존 메모",
            creatorId = 1L,
        )
        val existingInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = originalStart.toLocalDate(),
            startTime = originalStart,
            endTime = originalEnd,
            status = EventInstancesStatus.CONFIRMED,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findFirstByEventIdAndOverrideIdIsNull(1L)).thenReturn(existingInstance)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "회의",
                startTime = originalStart,
                endTime = originalEnd,
                notes = null,
            ),
        )

        assertNull(existingEvent.notes)
    }

    @Test
    fun `장소 없는 단일 일정 시간 수정 시 Event와 EventInstance 시간이 모두 변경`() {
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            creatorId = 1L,
        )
        val existingInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = LocalDate.of(2026, 4, 20),
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            status = EventInstancesStatus.CONFIRMED,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findFirstByEventIdAndOverrideIdIsNull(1L)).thenReturn(existingInstance)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "회의",
                startTime = LocalDateTime.of(2026, 4, 21, 14, 0),
                endTime = LocalDateTime.of(2026, 4, 21, 15, 0),
            ),
        )

        assertEquals(LocalDateTime.of(2026, 4, 21, 14, 0), existingEvent.startTime)
        assertEquals(LocalDateTime.of(2026, 4, 21, 15, 0), existingEvent.endTime)
        assertEquals(LocalDateTime.of(2026, 4, 21, 14, 0), existingInstance.startTime)
        assertEquals(LocalDateTime.of(2026, 4, 21, 15, 0), existingInstance.endTime)
        assertEquals(LocalDate.of(2026, 4, 21), existingInstance.dateKey)
    }
}
