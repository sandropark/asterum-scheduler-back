package com.sandro.asterumscheduler.event

import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

@SpringBootTest
@Testcontainers
@Transactional
class EventOverlapIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16")
    }

    @Autowired
    lateinit var eventRepository: EventRepository

    @Test
    fun `같은 장소에 시간이 겹치는 이벤트 저장 시 예외가 발생한다`() {
        val base = LocalDateTime.of(2026, 4, 21, 9, 0)
        eventRepository.saveAndFlush(event(base, base.plusHours(1), locationId = 1L))

        assertThrows<DataIntegrityViolationException> {
            eventRepository.saveAndFlush(event(base.plusMinutes(30), base.plusHours(2), locationId = 1L))
        }
    }

    @Test
    fun `같은 장소라도 시간이 겹치지 않으면 저장된다`() {
        val base = LocalDateTime.of(2026, 4, 21, 13, 0)
        eventRepository.saveAndFlush(event(base, base.plusHours(1), locationId = 2L))

        assertDoesNotThrow {
            eventRepository.saveAndFlush(event(base.plusHours(1), base.plusHours(2), locationId = 2L))
        }
    }

    @Test
    fun `장소가 없는 이벤트는 시간이 겹쳐도 저장된다`() {
        val base = LocalDateTime.of(2026, 4, 21, 15, 0)
        eventRepository.saveAndFlush(event(base, base.plusHours(1), locationId = null))

        assertDoesNotThrow {
            eventRepository.saveAndFlush(event(base, base.plusHours(1), locationId = null))
        }
    }

    private fun event(startTime: LocalDateTime, endTime: LocalDateTime, locationId: Long?) =
        Event(title = "테스트", startTime = startTime, endTime = endTime, locationId = locationId, creatorId = 1L)
}
