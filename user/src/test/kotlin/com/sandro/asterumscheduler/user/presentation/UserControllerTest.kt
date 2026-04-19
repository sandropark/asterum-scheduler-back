package com.sandro.asterumscheduler.user.presentation

import com.sandro.asterumscheduler.common.response.SliceResponse
import com.sandro.asterumscheduler.user.application.UserService
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
class UserControllerTest {

    @Mock
    lateinit var userService: UserService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(UserController(userService)).build()
    }

    @Test
    fun `GET users - 사용자 목록 반환`() {
        `when`(userService.getUsers(0, 10)).thenReturn(
            SliceResponse(listOf(UserResponse(1L, "김철수", "kim@example.com", 1L, "개발팀")), hasNext = false)
        )

        mockMvc.get("/users?page=0&size=10")
            .andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.content[0].name") { value("김철수") }
                jsonPath("$.data.hasNext") { value(false) }
            }
    }

}
