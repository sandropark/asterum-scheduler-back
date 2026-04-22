package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RruleSuccessor {
    fun succeed(rrule: String, start: LocalDateTime, newStart: LocalDateTime): String {
        val rule = try {
            RecurrenceRule(rrule)
        } catch (e: InvalidRecurrenceRuleException) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        val oldCount = rule.count ?: return rule.toString()

        val newStartDt = newStart.toDmfsDateTime()
        val iter = rule.iterator(start.toDmfsDateTime())
        var consumed = 0
        while (iter.hasNext()) {
            val next = iter.nextDateTime()
            if (!next.before(newStartDt)) break
            consumed++
        }
        rule.count = oldCount - consumed
        return rule.toString()
    }

    private fun LocalDateTime.toDmfsDateTime(): DateTime =
        DateTime(year, monthValue - 1, dayOfMonth, hour, minute, second)
}
