package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.location.LocationReader
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstances
import com.sandro.asterumscheduler.event.domain.EventInstancesStatus
import com.sandro.asterumscheduler.event.infra.EventInstancesRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class EventServiceTest {

    @Mock
    lateinit var eventRepository: EventRepository

    @Mock
    lateinit var eventInstancesRepository: EventInstancesRepository

    @Mock
    lateinit var locationReader: LocationReader

    @InjectMocks
    lateinit var eventService: EventService

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
}
