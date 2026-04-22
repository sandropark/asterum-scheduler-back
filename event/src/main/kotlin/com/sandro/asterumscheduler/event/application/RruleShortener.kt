package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RruleShortener {
    fun shorten(rrule: String, until: LocalDateTime): String {
        val rule = try {
            RecurrenceRule(rrule)
        } catch (e: InvalidRecurrenceRuleException) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        rule.until = DateTime(
            until.year,
            until.monthValue - 1,
            until.dayOfMonth,
            until.hour,
            until.minute,
            until.second,
        )
        return rule.toString()
    }
}
