package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

@Component
class RecurrenceExpander(
    private val policy: SchedulerPolicyProperties,
) {
    data class Occurrence(val startAt: LocalDateTime, val endAt: LocalDateTime)

    fun expand(
        rrule: String,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<Occurrence> {
        val duration = Duration.between(startAt, endAt)
        val policyLimit = now.plusYears(policy.expansionYears.toLong())
        val rule = try {
            RecurrenceRule(rrule)
        } catch (e: InvalidRecurrenceRuleException) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        val iter = rule.iterator(startAt.toDmfsDateTime())
        val result = mutableListOf<Occurrence>()
        while (iter.hasNext()) {
            val next = iter.nextDateTime().toLocalDateTime()
            if (next.isAfter(policyLimit)) break
            result += Occurrence(next, next.plus(duration))
        }
        return result
    }

    private fun LocalDateTime.toDmfsDateTime(): DateTime =
        DateTime(year, monthValue - 1, dayOfMonth, hour, minute, second)

    private fun DateTime.toLocalDateTime(): LocalDateTime =
        LocalDateTime.of(year, month + 1, dayOfMonth, hours, minutes, seconds)
}
