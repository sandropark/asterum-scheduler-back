package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.user.UserReader
import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventParticipantRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import com.sandro.asterumscheduler.event.infra.InstanceParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class EventQueryService(
    private val eventRepository: EventRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventParticipantRepository: EventParticipantRepository,
    private val instanceParticipantRepository: InstanceParticipantRepository,
    private val userReader: UserReader,
) {
    fun findDetail(instanceId: Long): EventInstanceDetail {
        val instance = eventInstanceRepository.findById(instanceId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND)
        }
        val event = eventRepository.findById(instance.eventId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND)
        }
        val userIds = if (instance.hasOverrideParticipants) {
            instanceParticipantRepository.findAllByInstanceId(instanceId).map { it.userId }
        } else {
            eventParticipantRepository.findAllByEventId(event.id!!).map { it.userId }
        }
        val userInfos = if (userIds.isEmpty()) emptyList() else userReader.findByIds(userIds.toSet())
        val teamIds = userInfos.filter { it.isTeam }.map { it.id }.toSet()
        val membersByTeamId = if (teamIds.isEmpty()) emptyMap() else userReader.findMembersByTeamIds(teamIds)
        val participants = userInfos.map { info ->
            if (info.isTeam) {
                val members = membersByTeamId[info.id].orEmpty()
                    .map { ParticipantSummary(id = it.id, name = it.name) }
                ParticipantSummary(id = info.id, name = info.name, isTeam = true, members = members)
            } else {
                ParticipantSummary(id = info.id, name = info.name)
            }
        }
        return EventInstanceDetail(
            id = instance.id!!,
            title = instance.title ?: event.title,
            startAt = instance.startAt,
            endAt = instance.endAt,
            eventStartAt = event.startAt,
            rrule = event.rrule,
            participants = participants,
        )
    }

    fun findMonthly(query: MonthlyEventQuery): List<EventInstanceSummary> {
        // TODO: 추후 조인으로 처리할지 고민
        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(query.from, query.to)
        val eventIds = instances.map { it.eventId }.toSet()
        val eventTitleById = eventRepository.findAllById(eventIds).associate { it.id!! to it.title }
        return instances.map { instance ->
            EventInstanceSummary(
                id = instance.id!!,
                title = instance.title ?: eventTitleById.getValue(instance.eventId),
                startAt = instance.startAt,
                endAt = instance.endAt,
            )
        }
    }
}
