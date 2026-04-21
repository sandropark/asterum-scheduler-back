package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recurrenceset.OfRuleAndFirst
import org.dmfs.rfc5545.recurrenceset.Within
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object RRuleExpander {

    private val UNTIL_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun expand(
        rrule: String,
        dtStart: LocalDateTime,
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime,
    ): List<LocalDateTime> {
        require(!rangeEnd.isBefore(rangeStart)) { "rangeEnd must be on or after rangeStart" }

        val rule = try {
            RecurrenceRule(rrule)
        } catch (e: InvalidRecurrenceRuleException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, e)
        }
        val start = dtStart.toFloatingDateTime()
        val from = rangeStart.toFloatingDateTime()
        val until = rangeEnd.plusSeconds(1).toFloatingDateTime()

        return Within(from, until, OfRuleAndFirst(rule, start))
            .map { it.toLocalDateTime() }
            .filter { !it.isAfter(rangeEnd) }
    }

    fun truncateUntilBefore(rrule: String, exclusiveDate: LocalDate): String {
        try {
            RecurrenceRule(rrule)
        } catch (e: InvalidRecurrenceRuleException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, e)
        }
        val until = exclusiveDate.minusDays(1).atTime(23, 59, 59)
        val untilPart = "UNTIL=" + until.format(UNTIL_FORMATTER)
        val keptParts = rrule.split(";")
            .filterNot { it.startsWith("UNTIL=") || it.startsWith("COUNT=") }
        return (keptParts + untilPart).joinToString(";")
    }

    private fun LocalDateTime.toFloatingDateTime(): DateTime =
        DateTime(year, monthValue - 1, dayOfMonth, hour, minute, second)

    private fun DateTime.toLocalDateTime(): LocalDateTime =
        LocalDateTime.of(year, month + 1, dayOfMonth, hours, minutes, seconds)
}
