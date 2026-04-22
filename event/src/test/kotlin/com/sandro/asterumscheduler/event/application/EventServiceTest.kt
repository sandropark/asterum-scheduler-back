package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstance
import com.sandro.asterumscheduler.event.domain.assignIdForTest
import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class EventServiceTest {

    private val eventRepository = mockk<EventRepository>()
    private val eventInstanceRepository = mockk<EventInstanceRepository>()
    private val service = EventService(eventRepository, eventInstanceRepository)

    @Test
    fun `일정을 생성하면 events 와 events_instances 가 동시에 저장된다`() {
        val title = "회의"
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = LocalDateTime.of(2026, 5, 1, 11, 0)

        every { eventRepository.save(any<Event>()) } answers {
            val e = firstArg<Event>()
            e.assignIdForTest(1L)
            e
        }
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }

        val event = service.create(EventCreateRequest(title, startAt, endAt))

        assertEquals(1L, event.id)
        assertEquals(title, event.title)
        assertEquals(startAt, event.startAt)
        assertEquals(endAt, event.endAt)

        verify {
            eventInstanceRepository.save(
                match<EventInstance> {
                    it.eventId == 1L &&
                        it.title == null &&
                        it.startAt == startAt &&
                        it.endAt == endAt
                }
            )
        }
    }

}
