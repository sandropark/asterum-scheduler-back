package com.sandro.asterumscheduler.location.presentation

import com.sandro.asterumscheduler.common.advice.GlobalExceptionHandler
import com.sandro.asterumscheduler.location.application.LocationAvailabilityRequest
import com.sandro.asterumscheduler.location.application.LocationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@WebMvcTest(LocationController::class)
@Import(GlobalExceptionHandler::class)
class LocationControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var locationService: LocationService

    @Test
    fun `GET locations - 전체 장소 목록을 예약 가능 여부와 함께 반환`() {
        whenever(locationService.findAll(any<LocationAvailabilityRequest>())).thenReturn(
            listOf(
                LocationResponse(1L, "회의실 A", 10, available = true),
                LocationResponse(2L, "회의실 B", 20, available = false),
                LocationResponse(3L, "회의실 C", 30, available = true),
            )
        )

        mockMvc.get("/locations?start=2026-04-21T09:00:00&end=2026-04-21T10:00:00")
            .andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.length()") { value(3) }
                jsonPath("$.data[0].id") { value(1) }
                jsonPath("$.data[0].available") { value(true) }
                jsonPath("$.data[1].id") { value(2) }
                jsonPath("$.data[1].available") { value(false) }
                jsonPath("$.data[2].available") { value(true) }
            }
    }

    @Test
    fun `GET locations - 종료 시간이 시작 시간 이하면 400을 반환한다`() {
        mockMvc.get("/locations?start=2026-04-21T10:00:00&end=2026-04-21T09:00:00")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }
}
