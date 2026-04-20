package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.location.LocationReader
import com.sandro.asterumscheduler.common.user.UserReader
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class EventServiceTest {

    @Mock
    lateinit var eventRepository: EventRepository

    @Mock
    lateinit var locationReader: LocationReader

    @Mock
    lateinit var userReader: UserReader

    private lateinit var eventService: EventService

    @BeforeEach
    fun setup() {
        eventService = EventService(eventRepository, locationReader, userReader)
    }

    @Test
    fun `일정을 생성하면 저장된 일정을 반환한다`() {
        val request = CreateEventRequest(
            title = "팀 회의",
            startTime = LocalDateTime.of(2026, 4, 21, 9, 0),
            endTime = LocalDateTime.of(2026, 4, 21, 10, 0),
            locationId = 1L,
            notes = "주간 회의",
            creatorId = 1L,
            participantIds = listOf(2L, 3L),
        )
        val savedEvent = Event(
            id = 1L,
            title = request.title,
            startTime = request.startTime,
            endTime = request.endTime,
            locationId = request.locationId,
            notes = request.notes,
            creatorId = request.creatorId,
        )
        `when`(locationReader.existsById(1L)).thenReturn(true)
        `when`(userReader.findNotExistingIds(listOf(2L, 3L))).thenReturn(emptyList())
        `when`(eventRepository.save(any(Event::class.java))).thenReturn(savedEvent)

        val result = eventService.create(request)

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.title).isEqualTo("팀 회의")
        assertThat(result.locationId).isEqualTo(1L)
        assertThat(result.participants).isEmpty()
    }

    @Test
    fun `존재하지 않는 장소로 일정 생성 시 NOT_FOUND 예외를 던진다`() {
        val request = CreateEventRequest(
            title = "팀 회의",
            startTime = LocalDateTime.of(2026, 4, 21, 9, 0),
            endTime = LocalDateTime.of(2026, 4, 21, 10, 0),
            locationId = 999L,
            notes = null,
            creatorId = 1L,
        )
        `when`(locationReader.existsById(999L)).thenReturn(false)

        val exception = assertThrows<BusinessException> { eventService.create(request) }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `존재하지 않는 참여자로 일정 생성 시 NOT_FOUND 예외를 던진다`() {
        val request = CreateEventRequest(
            title = "팀 회의",
            startTime = LocalDateTime.of(2026, 4, 21, 9, 0),
            endTime = LocalDateTime.of(2026, 4, 21, 10, 0),
            creatorId = 1L,
            participantIds = listOf(2L, 999L),
        )
        `when`(userReader.findNotExistingIds(listOf(2L, 999L))).thenReturn(listOf(999L))

        val exception = assertThrows<BusinessException> { eventService.create(request) }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `장소 중복 예약 시 CONFLICT 예외를 던진다`() {
        val request = CreateEventRequest(
            title = "팀 회의",
            startTime = LocalDateTime.of(2026, 4, 21, 9, 0),
            endTime = LocalDateTime.of(2026, 4, 21, 10, 0),
            locationId = 1L,
            notes = null,
            creatorId = 1L,
            participantIds = emptyList(),
        )
        `when`(locationReader.existsById(1L)).thenReturn(true)
        `when`(eventRepository.save(any(Event::class.java)))
            .thenThrow(DataIntegrityViolationException("exclusion constraint violation"))

        val exception = assertThrows<BusinessException> { eventService.create(request) }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.CONFLICT)
    }

    @Test
    fun `이벤트를 삭제한다`() {
        val event = Event(
            id = 1L,
            title = "팀 회의",
            startTime = LocalDateTime.now(),
            endTime = LocalDateTime.now().plusHours(1),
            creatorId = 1L
        )
        `when`(eventRepository.findById(1L)).thenReturn(Optional.of(event))

        eventService.delete(1L)

        verify(eventRepository).delete(event)
    }

    @Test
    fun `존재하지 않는 이벤트 삭제 시 NOT_FOUND 예외를 던진다`() {
        `when`(eventRepository.findById(999L)).thenReturn(Optional.empty())

        val exception = assertThrows<BusinessException> { eventService.delete(999L) }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }
}
