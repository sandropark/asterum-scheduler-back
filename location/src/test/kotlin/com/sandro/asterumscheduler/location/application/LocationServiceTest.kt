package com.sandro.asterumscheduler.location.application

import com.sandro.asterumscheduler.common.event.EventReader
import com.sandro.asterumscheduler.location.domain.Location
import com.sandro.asterumscheduler.location.infra.LocationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class LocationServiceTest {

    @Mock lateinit var locationRepository: LocationRepository
    @Mock lateinit var eventReader: EventReader

    private lateinit var service: LocationService

    @BeforeEach
    fun setup() {
        service = LocationService(locationRepository, eventReader)
    }

    @Test
    fun `모든 장소를 반환하며 예약된 장소는 available=false로 표시한다`() {
        val start = LocalDateTime.of(2026, 4, 21, 9, 0)
        val end = LocalDateTime.of(2026, 4, 21, 10, 0)
        val a = Location(id = 1L, name = "A", capacity = 10)
        val b = Location(id = 2L, name = "B", capacity = 20)
        val c = Location(id = 3L, name = "C", capacity = 30)
        `when`(eventReader.findReservedLocationIds(start, end)).thenReturn(listOf(2L))
        `when`(locationRepository.findAll()).thenReturn(listOf(a, b, c))

        val result = service.findAll(LocationAvailabilityRequest(start, end))

        assertThat(result).hasSize(3)
        assertThat(result.single { it.id == 1L }.available).isTrue()
        assertThat(result.single { it.id == 2L }.available).isFalse()
        assertThat(result.single { it.id == 3L }.available).isTrue()
    }
}
