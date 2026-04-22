package com.sandro.asterumscheduler.user.application

import com.sandro.asterumscheduler.common.user.UserInfo
import com.sandro.asterumscheduler.user.domain.User
import com.sandro.asterumscheduler.user.domain.UserMembership
import com.sandro.asterumscheduler.user.domain.assignIdForTest
import com.sandro.asterumscheduler.user.infra.UserMembershipRepository
import com.sandro.asterumscheduler.user.infra.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserReaderImplTest {

    private val userRepository = mockk<UserRepository>()
    private val userMembershipRepository = mockk<UserMembershipRepository>()
    private val reader = UserReaderImpl(userRepository, userMembershipRepository)

    @Test
    fun `findByIds — isTeam=true 유저 포함 시 UserInfo에 isTeam 반영`() {
        val team = User(email = "team@a.com", name = "팀A", isTeam = true).also { it.assignIdForTest(10L) }
        val member = User(email = "m@a.com", name = "멤버1").also { it.assignIdForTest(1L) }

        every { userRepository.findAllById(setOf(10L, 1L)) } returns listOf(team, member)

        val result = reader.findByIds(setOf(10L, 1L))

        assertEquals(UserInfo(10L, "팀A", isTeam = true), result.first { it.id == 10L })
        assertEquals(UserInfo(1L, "멤버1", isTeam = false), result.first { it.id == 1L })
    }

    @Test
    fun `findMembersByTeamIds — 팀 ID에 멤버 있으면 teamId 키로 UserInfo 목록 반환`() {
        val member1 = User(email = "m1@a.com", name = "멤버1").also { it.assignIdForTest(1L) }
        val member2 = User(email = "m2@a.com", name = "멤버2").also { it.assignIdForTest(2L) }

        every { userMembershipRepository.findAllByTeamIdIn(setOf(10L)) } returns listOf(
            UserMembership(teamId = 10L, memberId = 1L),
            UserMembership(teamId = 10L, memberId = 2L),
        )
        every { userRepository.findAllById(setOf(1L, 2L)) } returns listOf(member1, member2)

        val result = reader.findMembersByTeamIds(setOf(10L))

        assertEquals(1, result.size)
        val members = result[10L]!!
        assertEquals(2, members.size)
        assertTrue(members.any { it == UserInfo(1L, "멤버1") })
        assertTrue(members.any { it == UserInfo(2L, "멤버2") })
    }

    @Test
    fun `findMembersByTeamIds — 멤버 없는 팀 ID이면 빈 맵 반환`() {
        every { userMembershipRepository.findAllByTeamIdIn(setOf(99L)) } returns emptyList()

        val result = reader.findMembersByTeamIds(setOf(99L))

        assertTrue(result.isEmpty())
    }
}
