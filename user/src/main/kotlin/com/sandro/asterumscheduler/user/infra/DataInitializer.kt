package com.sandro.asterumscheduler.user.infra

import com.sandro.asterumscheduler.user.domain.Team
import com.sandro.asterumscheduler.user.domain.User
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Profile("dev")
@Component
class DataInitializer(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (teamRepository.count() > 0) return

        val devTeam = teamRepository.save(Team(name = "개발팀"))
        val designTeam = teamRepository.save(Team(name = "디자인팀"))
        val planningTeam = teamRepository.save(Team(name = "기획팀"))

        userRepository.saveAll(
            listOf(
                User(name = "김철수", email = "kim.cs@example.com", team = devTeam),
                User(name = "이영희", email = "lee.yh@example.com", team = devTeam),
                User(name = "박지수", email = "park.js@example.com", team = designTeam),
                User(name = "최민준", email = "choi.mj@example.com", team = planningTeam),
            )
        )
    }
}
