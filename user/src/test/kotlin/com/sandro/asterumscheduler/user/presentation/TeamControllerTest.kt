package com.sandro.asterumscheduler.user.presentation

import com.sandro.asterumscheduler.common.response.SliceResponse
import com.sandro.asterumscheduler.user.application.TeamService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class TeamControllerTest {

    @Mock
    lateinit var teamService: TeamService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(TeamController(teamService)).build()
    }

    @Test
    fun `GET teams - 팀 목록 반환`() {
        `when`(teamService.getTeams(0, 10)).thenReturn(
            SliceResponse(listOf(TeamResponse(1L, "개발팀")), hasNext = false)
        )

        mockMvc.get("/teams?page=0&size=10")
            .andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.content[0].name") { value("개발팀") }
                jsonPath("$.data.hasNext") { value(false) }
            }
    }
}
