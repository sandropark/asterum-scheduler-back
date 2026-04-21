package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.location.LocationReader
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstances
import com.sandro.asterumscheduler.event.domain.EventInstancesStatus
import com.sandro.asterumscheduler.event.domain.EventOverride
import com.sandro.asterumscheduler.event.domain.RecurrenceScope
import com.sandro.asterumscheduler.event.infra.EventInstancesRepository
import com.sandro.asterumscheduler.event.infra.EventOverrideRepository
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    @Mock
    lateinit var eventOverrideRepository: EventOverrideRepository

    lateinit var eventService: EventService

    @BeforeEach
    fun setUp() {
        eventService = EventService(
            eventRepository = eventRepository,
            eventInstancesRepository = eventInstancesRepository,
            locationReader = locationReader,
            eventOverrideRepository = eventOverrideRepository,
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
    fun `단일 일정 장소를 존재하지 않는 locationId로 수정하면 NOT_FOUND`() {
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            creatorId = 1L,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(locationReader.existsById(999L)).thenReturn(false)

        val ex = assertThrows<BusinessException> {
            eventService.update(
                1L,
                EventUpdateRequest(
                    title = "회의",
                    startTime = existingEvent.startTime,
                    endTime = existingEvent.endTime,
                    locationId = 999L,
                ),
            )
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `단일 일정 장소 추가 시 겹침 없으면 CONFIRMED + instance locationId 갱신`() {
        val originalStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val originalEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val newLocationId = 10L
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = originalStart,
            endTime = originalEnd,
            locationId = null,
            creatorId = 1L,
        )
        val existingInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = originalStart.toLocalDate(),
            startTime = originalStart,
            endTime = originalEnd,
            locationId = null,
            status = EventInstancesStatus.CONFIRMED,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(locationReader.existsById(newLocationId)).thenReturn(true)
        whenever(
            eventInstancesRepository.existsOverlapByLocationExcludingInstance(newLocationId, originalStart, originalEnd, 100L)
        ).thenReturn(false)
        whenever(eventInstancesRepository.findFirstByEventIdAndOverrideIdIsNull(1L)).thenReturn(existingInstance)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "회의",
                startTime = originalStart,
                endTime = originalEnd,
                locationId = newLocationId,
            ),
        )

        assertEquals(newLocationId, existingEvent.locationId)
        assertEquals(newLocationId, existingInstance.locationId)
        assertEquals(EventInstancesStatus.CONFIRMED, existingInstance.status)
    }

    @Test
    fun `반복 일정 scope=THIS_ONLY 제목 수정 시 Override 생성하고 Instance에 overrideId 연결`() {
        val start = LocalDateTime.of(2026, 4, 20, 10, 0)
        val end = LocalDateTime.of(2026, 4, 20, 11, 0)
        val targetDate = LocalDate.of(2026, 4, 22)
        val instanceStart = LocalDateTime.of(2026, 4, 22, 10, 0)
        val instanceEnd = LocalDateTime.of(2026, 4, 22, 11, 0)

        val existingEvent = Event(
            id = 1L,
            title = "원래 제목",
            startTime = start,
            endTime = end,
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        val targetInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = targetDate,
            startTime = instanceStart,
            endTime = instanceEnd,
            status = EventInstancesStatus.CONFIRMED,
        )
        val savedOverride = EventOverride(
            id = 500L,
            eventId = 1L,
            overrideDate = targetDate,
            title = "새 제목",
            startTime = instanceStart,
            endTime = instanceEnd,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findByEventIdAndDateKey(1L, targetDate)).thenReturn(targetInstance)
        whenever(eventOverrideRepository.save(any())).thenReturn(savedOverride)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "새 제목",
                startTime = instanceStart,
                endTime = instanceEnd,
                targetDate = targetDate,
            ),
            RecurrenceScope.THIS_ONLY,
        )

        val overrideCaptor = argumentCaptor<EventOverride>()
        verify(eventOverrideRepository).save(overrideCaptor.capture())
        assertEquals("새 제목", overrideCaptor.firstValue.title)
        assertEquals(targetDate, overrideCaptor.firstValue.overrideDate)
        assertEquals(1L, overrideCaptor.firstValue.eventId)
        assertEquals(500L, targetInstance.overrideId)
        assertEquals("원래 제목", existingEvent.title)
    }

    @Test
    fun `scope=THIS_ONLY 인데 targetDate 누락 시 INVALID_INPUT`() {
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))

        val ex = assertThrows<BusinessException> {
            eventService.update(
                1L,
                EventUpdateRequest(
                    title = "회의",
                    startTime = existingEvent.startTime,
                    endTime = existingEvent.endTime,
                    targetDate = null,
                ),
                RecurrenceScope.THIS_ONLY,
            )
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }

    @Test
    fun `단일 일정 장소 수정 시 다른 일정과 겹치면 CONFLICT`() {
        val originalStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val originalEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val newLocationId = 10L
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = originalStart,
            endTime = originalEnd,
            locationId = 5L,
            creatorId = 1L,
        )
        val existingInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = originalStart.toLocalDate(),
            startTime = originalStart,
            endTime = originalEnd,
            locationId = 5L,
            status = EventInstancesStatus.CONFIRMED,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(locationReader.existsById(newLocationId)).thenReturn(true)
        whenever(
            eventInstancesRepository.existsOverlapByLocationExcludingInstance(newLocationId, originalStart, originalEnd, 100L)
        ).thenReturn(true)
        whenever(eventInstancesRepository.findFirstByEventIdAndOverrideIdIsNull(1L)).thenReturn(existingInstance)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "회의",
                startTime = originalStart,
                endTime = originalEnd,
                locationId = newLocationId,
            ),
        )

        assertEquals(newLocationId, existingEvent.locationId)
        assertEquals(newLocationId, existingInstance.locationId)
        assertEquals(EventInstancesStatus.CONFLICT, existingInstance.status)
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

    @Test
    fun `반복 일정 scope=THIS_ONLY 시간 수정 시 Override 생성하고 Instance 시간도 갱신`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val targetDate = LocalDate.of(2026, 4, 22)
        val instanceStart = LocalDateTime.of(2026, 4, 22, 10, 0)
        val instanceEnd = LocalDateTime.of(2026, 4, 22, 11, 0)
        val newStart = LocalDateTime.of(2026, 4, 23, 14, 0)
        val newEnd = LocalDateTime.of(2026, 4, 23, 15, 0)

        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = seriesStart,
            endTime = seriesEnd,
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        val targetInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = targetDate,
            startTime = instanceStart,
            endTime = instanceEnd,
            status = EventInstancesStatus.CONFIRMED,
        )
        val savedOverride = EventOverride(
            id = 500L,
            eventId = 1L,
            overrideDate = targetDate,
            title = "회의",
            startTime = newStart,
            endTime = newEnd,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findByEventIdAndDateKey(1L, targetDate)).thenReturn(targetInstance)
        whenever(eventOverrideRepository.save(any())).thenReturn(savedOverride)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "회의",
                startTime = newStart,
                endTime = newEnd,
                targetDate = targetDate,
            ),
            RecurrenceScope.THIS_ONLY,
        )

        val overrideCaptor = argumentCaptor<EventOverride>()
        verify(eventOverrideRepository).save(overrideCaptor.capture())
        assertEquals(newStart, overrideCaptor.firstValue.startTime)
        assertEquals(newEnd, overrideCaptor.firstValue.endTime)
        assertEquals(500L, targetInstance.overrideId)
        assertEquals(newStart, targetInstance.startTime)
        assertEquals(newEnd, targetInstance.endTime)
        assertEquals(LocalDate.of(2026, 4, 23), targetInstance.dateKey)
        assertEquals(seriesStart, existingEvent.startTime)
        assertEquals(seriesEnd, existingEvent.endTime)
    }

    @Test
    fun `반복 일정 scope=THIS_ONLY 장소 수정 시 Override 생성하고 Instance 장소도 갱신`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val targetDate = LocalDate.of(2026, 4, 22)
        val instanceStart = LocalDateTime.of(2026, 4, 22, 10, 0)
        val instanceEnd = LocalDateTime.of(2026, 4, 22, 11, 0)
        val newLocationId = 10L

        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = seriesStart,
            endTime = seriesEnd,
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        val targetInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = targetDate,
            startTime = instanceStart,
            endTime = instanceEnd,
            locationId = null,
            status = EventInstancesStatus.CONFIRMED,
        )
        val savedOverride = EventOverride(
            id = 500L,
            eventId = 1L,
            overrideDate = targetDate,
            title = "회의",
            startTime = instanceStart,
            endTime = instanceEnd,
            locationId = newLocationId,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findByEventIdAndDateKey(1L, targetDate)).thenReturn(targetInstance)
        whenever(locationReader.existsById(newLocationId)).thenReturn(true)
        whenever(
            eventInstancesRepository.existsOverlapByLocationExcludingInstance(newLocationId, instanceStart, instanceEnd, 100L)
        ).thenReturn(false)
        whenever(eventOverrideRepository.save(any())).thenReturn(savedOverride)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "회의",
                startTime = instanceStart,
                endTime = instanceEnd,
                locationId = newLocationId,
                targetDate = targetDate,
            ),
            RecurrenceScope.THIS_ONLY,
        )

        val overrideCaptor = argumentCaptor<EventOverride>()
        verify(eventOverrideRepository).save(overrideCaptor.capture())
        assertEquals(newLocationId, overrideCaptor.firstValue.locationId)
        assertEquals(500L, targetInstance.overrideId)
        assertEquals(newLocationId, targetInstance.locationId)
        assertEquals(EventInstancesStatus.CONFIRMED, targetInstance.status)
        assertNull(existingEvent.locationId)
    }

    @Test
    fun `반복 일정 scope=THIS_ONLY 장소 변경 시 같은 반복 일정의 다른 회차와 충돌하면 CONFLICT`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val targetDate = LocalDate.of(2026, 4, 22)
        val instanceStart = LocalDateTime.of(2026, 4, 22, 10, 0)
        val instanceEnd = LocalDateTime.of(2026, 4, 22, 11, 0)
        val newLocationId = 10L

        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = seriesStart,
            endTime = seriesEnd,
            locationId = newLocationId,
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        val targetInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = targetDate,
            startTime = instanceStart,
            endTime = instanceEnd,
            locationId = newLocationId,
            status = EventInstancesStatus.CONFIRMED,
        )
        val savedOverride = EventOverride(
            id = 500L,
            eventId = 1L,
            overrideDate = targetDate,
            title = "회의",
            startTime = instanceStart,
            endTime = instanceEnd,
            locationId = newLocationId,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findByEventIdAndDateKey(1L, targetDate)).thenReturn(targetInstance)
        whenever(locationReader.existsById(newLocationId)).thenReturn(true)
        whenever(
            eventInstancesRepository.existsOverlapByLocationExcludingInstance(newLocationId, instanceStart, instanceEnd, 100L)
        ).thenReturn(true)
        whenever(eventOverrideRepository.save(any())).thenReturn(savedOverride)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "회의",
                startTime = instanceStart,
                endTime = instanceEnd,
                locationId = newLocationId,
                targetDate = targetDate,
            ),
            RecurrenceScope.THIS_ONLY,
        )

        assertEquals(EventInstancesStatus.CONFLICT, targetInstance.status)
    }

    @Test
    fun `단일 일정 삭제 시 Event와 EventInstance에 deletedAt 설정`() {
        val start = LocalDateTime.of(2026, 4, 20, 10, 0)
        val end = LocalDateTime.of(2026, 4, 20, 11, 0)
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = start,
            endTime = end,
            creatorId = 1L,
        )
        val existingInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = start.toLocalDate(),
            startTime = start,
            endTime = end,
            status = EventInstancesStatus.CONFIRMED,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findFirstByEventIdAndOverrideIdIsNull(1L)).thenReturn(existingInstance)

        eventService.delete(1L)

        assertNotNull(existingEvent.deletedAt)
        assertNotNull(existingInstance.deletedAt)
        verify(eventOverrideRepository, never()).save(any())
    }

    @Test
    fun `존재하지 않는 일정을 삭제하면 NOT_FOUND 예외`() {
        whenever(eventRepository.findById(999L)).thenReturn(Optional.empty())

        val ex = assertThrows<BusinessException> { eventService.delete(999L) }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `반복 일정 scope=THIS_ONLY 삭제 시 deletedAt 설정된 Override 생성하고 Instance에도 deletedAt 설정`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val targetDate = LocalDate.of(2026, 4, 22)
        val instanceStart = LocalDateTime.of(2026, 4, 22, 10, 0)
        val instanceEnd = LocalDateTime.of(2026, 4, 22, 11, 0)
        val locationId = 10L

        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = seriesStart,
            endTime = seriesEnd,
            locationId = locationId,
            notes = "원본 메모",
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        val targetInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = targetDate,
            startTime = instanceStart,
            endTime = instanceEnd,
            locationId = locationId,
            status = EventInstancesStatus.CONFIRMED,
        )
        val savedOverride = EventOverride(
            id = 500L,
            eventId = 1L,
            overrideDate = targetDate,
            title = "회의",
            startTime = instanceStart,
            endTime = instanceEnd,
            locationId = locationId,
            notes = "원본 메모",
            deletedAt = LocalDateTime.now(),
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findByEventIdAndDateKey(1L, targetDate)).thenReturn(targetInstance)
        whenever(eventOverrideRepository.save(any())).thenReturn(savedOverride)

        eventService.delete(1L, RecurrenceScope.THIS_ONLY, targetDate)

        val overrideCaptor = argumentCaptor<EventOverride>()
        verify(eventOverrideRepository).save(overrideCaptor.capture())
        assertNotNull(overrideCaptor.firstValue.deletedAt)
        assertEquals(targetDate, overrideCaptor.firstValue.overrideDate)
        assertEquals(1L, overrideCaptor.firstValue.eventId)
        assertEquals(instanceStart, overrideCaptor.firstValue.startTime)
        assertEquals(instanceEnd, overrideCaptor.firstValue.endTime)
        assertEquals(locationId, overrideCaptor.firstValue.locationId)
        assertEquals(500L, targetInstance.overrideId)
        assertNotNull(targetInstance.deletedAt)
        assertNull(existingEvent.deletedAt)
    }

    @Test
    fun `반복 일정 scope=ALL 삭제 시 Event 모든 Overrides 모든 Instances 에 deletedAt 설정`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val locationId = 5L

        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = seriesStart,
            endTime = seriesEnd,
            locationId = locationId,
            rrule = "FREQ=DAILY;COUNT=3",
            creatorId = 1L,
        )
        val override1 = EventOverride(
            id = 500L,
            eventId = 1L,
            overrideDate = LocalDate.of(2026, 4, 21),
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 21, 14, 0),
            endTime = LocalDateTime.of(2026, 4, 21, 15, 0),
            locationId = 7L,
        )
        val override2 = EventOverride(
            id = 501L,
            eventId = 1L,
            overrideDate = LocalDate.of(2026, 4, 22),
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 22, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 22, 11, 0),
            locationId = locationId,
        )
        val instance1 = EventInstances(
            id = 1000L,
            eventId = 1L,
            dateKey = LocalDate.of(2026, 4, 20),
            startTime = seriesStart,
            endTime = seriesEnd,
            locationId = locationId,
            status = EventInstancesStatus.CONFIRMED,
        )
        val instance2 = EventInstances(
            id = 1001L,
            eventId = 1L,
            dateKey = LocalDate.of(2026, 4, 21),
            startTime = LocalDateTime.of(2026, 4, 21, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 21, 11, 0),
            locationId = locationId,
            status = EventInstancesStatus.CONFIRMED,
        )
        val instance3 = EventInstances(
            id = 1002L,
            eventId = 1L,
            dateKey = LocalDate.of(2026, 4, 22),
            startTime = LocalDateTime.of(2026, 4, 22, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 22, 11, 0),
            locationId = locationId,
            status = EventInstancesStatus.CONFIRMED,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventOverrideRepository.findByEventId(1L)).thenReturn(listOf(override1, override2))
        whenever(eventInstancesRepository.findByEventId(1L)).thenReturn(listOf(instance1, instance2, instance3))

        eventService.delete(1L, RecurrenceScope.ALL)

        assertNotNull(existingEvent.deletedAt)
        assertNotNull(override1.deletedAt)
        assertNotNull(override2.deletedAt)
        assertNotNull(instance1.deletedAt)
        assertNotNull(instance2.deletedAt)
        assertNotNull(instance3.deletedAt)
        verify(eventOverrideRepository, never()).save(any())
        verify(eventInstancesRepository, never()).save(any())
    }

    @Test
    fun `반복 일정 scope=THIS_ONLY 삭제 시 targetDate 누락이면 INVALID_INPUT`() {
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))

        val ex = assertThrows<BusinessException> {
            eventService.delete(1L, RecurrenceScope.THIS_ONLY, null)
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
        verify(eventOverrideRepository, never()).save(any())
    }

    @Test
    fun `반복 일정 scope=THIS_ONLY 삭제 시 해당 날짜 인스턴스가 없으면 NOT_FOUND`() {
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 20, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 20, 11, 0),
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        val targetDate = LocalDate.of(2026, 4, 22)
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findByEventIdAndDateKey(1L, targetDate)).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            eventService.delete(1L, RecurrenceScope.THIS_ONLY, targetDate)
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
        verify(eventOverrideRepository, never()).save(any())
    }

    @Test
    fun `반복 일정 scope=THIS_ONLY 수정 시 존재하지 않는 장소면 NOT_FOUND`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val targetDate = LocalDate.of(2026, 4, 22)
        val instanceStart = LocalDateTime.of(2026, 4, 22, 10, 0)
        val instanceEnd = LocalDateTime.of(2026, 4, 22, 11, 0)

        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = seriesStart,
            endTime = seriesEnd,
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        val targetInstance = EventInstances(
            id = 100L,
            eventId = 1L,
            dateKey = targetDate,
            startTime = instanceStart,
            endTime = instanceEnd,
            status = EventInstancesStatus.CONFIRMED,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventInstancesRepository.findByEventIdAndDateKey(1L, targetDate)).thenReturn(targetInstance)
        whenever(locationReader.existsById(999L)).thenReturn(false)

        val ex = assertThrows<BusinessException> {
            eventService.update(
                1L,
                EventUpdateRequest(
                    title = "회의",
                    startTime = instanceStart,
                    endTime = instanceEnd,
                    locationId = 999L,
                    targetDate = targetDate,
                ),
                RecurrenceScope.THIS_ONLY,
            )
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
        verify(eventOverrideRepository, never()).save(any())
    }

    @Test
    fun `반복 일정 scope=ALL 제목·메모 수정 시 Event와 모든 EventOverride가 일괄 갱신되고 EventInstance는 변화 없음`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val locationId = 5L

        val existingEvent = Event(
            id = 1L,
            title = "원래 제목",
            startTime = seriesStart,
            endTime = seriesEnd,
            locationId = locationId,
            notes = "원래 메모",
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        val override1 = EventOverride(
            id = 500L,
            eventId = 1L,
            overrideDate = LocalDate.of(2026, 4, 22),
            title = "원래 제목",
            startTime = LocalDateTime.of(2026, 4, 22, 14, 0),
            endTime = LocalDateTime.of(2026, 4, 22, 15, 0),
            locationId = 7L,
            notes = "원래 메모",
        )
        val override2 = EventOverride(
            id = 501L,
            eventId = 1L,
            overrideDate = LocalDate.of(2026, 4, 24),
            title = "기존에 따로 바뀐 제목",
            startTime = LocalDateTime.of(2026, 4, 24, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 24, 11, 0),
            locationId = locationId,
            notes = "기존에 따로 바뀐 메모",
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(eventOverrideRepository.findByEventId(1L)).thenReturn(listOf(override1, override2))

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "새 제목",
                startTime = seriesStart,
                endTime = seriesEnd,
                locationId = locationId,
                notes = "새 메모",
            ),
            RecurrenceScope.ALL,
        )

        assertEquals("새 제목", existingEvent.title)
        assertEquals("새 메모", existingEvent.notes)
        assertEquals("새 제목", override1.title)
        assertEquals("새 메모", override1.notes)
        assertEquals(LocalDateTime.of(2026, 4, 22, 14, 0), override1.startTime)
        assertEquals(LocalDateTime.of(2026, 4, 22, 15, 0), override1.endTime)
        assertEquals(7L, override1.locationId)
        assertEquals("새 제목", override2.title)
        assertEquals("새 메모", override2.notes)
        assertEquals(LocalDateTime.of(2026, 4, 24, 10, 0), override2.startTime)
        assertEquals(LocalDateTime.of(2026, 4, 24, 11, 0), override2.endTime)
        assertEquals(locationId, override2.locationId)
        verifyNoInteractions(eventInstancesRepository)
    }

    @Test
    fun `반복 일정 scope=ALL 시간 변경 시 INVALID_INPUT`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = seriesStart,
            endTime = seriesEnd,
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))

        val ex = assertThrows<BusinessException> {
            eventService.update(
                1L,
                EventUpdateRequest(
                    title = "회의",
                    startTime = seriesStart.plusHours(1),
                    endTime = seriesEnd.plusHours(1),
                ),
                RecurrenceScope.ALL,
            )
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
        verifyNoInteractions(eventInstancesRepository)
        verify(eventOverrideRepository, never()).findByEventId(any())
    }

    @Test
    fun `반복 일정 scope=ALL 장소 변경 시 모든 Overrides·Instances soft-delete 후 새 장소로 재전개`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val oldLocationId = 5L
        val newLocationId = 99L

        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = seriesStart,
            endTime = seriesEnd,
            locationId = oldLocationId,
            rrule = "FREQ=DAILY;COUNT=3",
            creatorId = 1L,
        )
        val override1 = EventOverride(
            id = 500L,
            eventId = 1L,
            overrideDate = LocalDate.of(2026, 4, 21),
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 21, 14, 0),
            endTime = LocalDateTime.of(2026, 4, 21, 15, 0),
            locationId = 7L,
        )
        val override2 = EventOverride(
            id = 501L,
            eventId = 1L,
            overrideDate = LocalDate.of(2026, 4, 22),
            title = "회의",
            startTime = LocalDateTime.of(2026, 4, 22, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 22, 11, 0),
            locationId = oldLocationId,
        )
        val instance1 = EventInstances(
            id = 1000L,
            eventId = 1L,
            dateKey = LocalDate.of(2026, 4, 20),
            startTime = seriesStart,
            endTime = seriesEnd,
            locationId = oldLocationId,
            status = EventInstancesStatus.CONFIRMED,
        )
        val instance2 = EventInstances(
            id = 1001L,
            eventId = 1L,
            dateKey = LocalDate.of(2026, 4, 21),
            startTime = LocalDateTime.of(2026, 4, 21, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 21, 11, 0),
            locationId = oldLocationId,
            status = EventInstancesStatus.CONFIRMED,
        )
        val instance3 = EventInstances(
            id = 1002L,
            eventId = 1L,
            dateKey = LocalDate.of(2026, 4, 22),
            startTime = LocalDateTime.of(2026, 4, 22, 10, 0),
            endTime = LocalDateTime.of(2026, 4, 22, 11, 0),
            locationId = oldLocationId,
            status = EventInstancesStatus.CONFIRMED,
        )

        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(locationReader.existsById(newLocationId)).thenReturn(true)
        whenever(eventOverrideRepository.findByEventId(1L)).thenReturn(listOf(override1, override2))
        whenever(eventInstancesRepository.findByEventId(1L)).thenReturn(listOf(instance1, instance2, instance3))
        whenever(eventInstancesRepository.existsOverlapByLocation(eq(newLocationId), any(), any())).thenReturn(false)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "회의",
                startTime = seriesStart,
                endTime = seriesEnd,
                locationId = newLocationId,
            ),
            RecurrenceScope.ALL,
        )

        assertNotNull(override1.deletedAt)
        assertNotNull(override2.deletedAt)
        assertNotNull(instance1.deletedAt)
        assertNotNull(instance2.deletedAt)
        assertNotNull(instance3.deletedAt)
        verify(eventInstancesRepository).flush()

        assertEquals(newLocationId, existingEvent.locationId)
        assertEquals(seriesStart, existingEvent.startTime)
        assertEquals(seriesEnd, existingEvent.endTime)
        assertEquals("FREQ=DAILY;COUNT=3", existingEvent.rrule)

        val newInstanceCaptor = argumentCaptor<EventInstances>()
        verify(eventInstancesRepository, times(3)).save(newInstanceCaptor.capture())
        newInstanceCaptor.allValues.forEach {
            assertEquals(newLocationId, it.locationId)
            assertEquals(EventInstancesStatus.CONFIRMED, it.status)
        }
    }

    @Test
    fun `반복 일정 scope=ALL 새 장소가 존재하지 않으면 NOT_FOUND`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = seriesStart,
            endTime = seriesEnd,
            locationId = 5L,
            rrule = "FREQ=DAILY",
            creatorId = 1L,
        )
        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(locationReader.existsById(99L)).thenReturn(false)

        val ex = assertThrows<BusinessException> {
            eventService.update(
                1L,
                EventUpdateRequest(
                    title = "회의",
                    startTime = seriesStart,
                    endTime = seriesEnd,
                    locationId = 99L,
                ),
                RecurrenceScope.ALL,
            )
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
        verify(eventOverrideRepository, never()).findByEventId(any())
        verify(eventInstancesRepository, never()).findByEventId(any())
        verify(eventInstancesRepository, never()).flush()
        verify(eventInstancesRepository, never()).save(any())
    }

    @Test
    fun `반복 일정 scope=ALL 장소 변경 시 새 장소에 다른 일정과 겹치면 CONFLICT 로 재생성`() {
        val seriesStart = LocalDateTime.of(2026, 4, 20, 10, 0)
        val seriesEnd = LocalDateTime.of(2026, 4, 20, 11, 0)
        val newLocationId = 99L

        val existingEvent = Event(
            id = 1L,
            title = "회의",
            startTime = seriesStart,
            endTime = seriesEnd,
            locationId = 5L,
            rrule = "FREQ=DAILY;COUNT=2",
            creatorId = 1L,
        )

        whenever(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent))
        whenever(locationReader.existsById(newLocationId)).thenReturn(true)
        whenever(eventOverrideRepository.findByEventId(1L)).thenReturn(emptyList())
        whenever(eventInstancesRepository.findByEventId(1L)).thenReturn(emptyList())
        whenever(eventInstancesRepository.existsOverlapByLocation(eq(newLocationId), any(), any())).thenReturn(true)

        eventService.update(
            1L,
            EventUpdateRequest(
                title = "회의",
                startTime = seriesStart,
                endTime = seriesEnd,
                locationId = newLocationId,
            ),
            RecurrenceScope.ALL,
        )

        val newInstanceCaptor = argumentCaptor<EventInstances>()
        verify(eventInstancesRepository, times(2)).save(newInstanceCaptor.capture())
        newInstanceCaptor.allValues.forEach {
            assertEquals(EventInstancesStatus.CONFLICT, it.status)
            assertEquals(newLocationId, it.locationId)
        }
    }
}
