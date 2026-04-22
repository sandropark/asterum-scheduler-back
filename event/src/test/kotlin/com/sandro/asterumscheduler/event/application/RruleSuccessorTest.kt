package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RruleSuccessorTest {

    private val successor = RruleSuccessor()

    @Test
    fun `COUNT 가 있는 rrule 을 newStart 이후 남은 횟수로 차감한다`() {
        // start=5/1 기준 DAILY COUNT=5 → 5/1, 5/2, 5/3, 5/4, 5/5
        // newStart=5/3 이면 이미 소비된 건 2 개, 남은 건 3 개.
        val start = LocalDateTime.of(2026, 5, 1, 10, 0, 0)
        val newStart = LocalDateTime.of(2026, 5, 3, 10, 0, 0)

        val succeeded = successor.succeed("FREQ=DAILY;COUNT=5", start, newStart)

        val reparsed = RecurrenceRule(succeeded)
        assertEquals(3, reparsed.count)
        assertTrue(succeeded.contains("FREQ=DAILY"))
        assertFalse(succeeded.contains("UNTIL="))
    }

    @Test
    fun `UNTIL 이 있는 rrule 은 그대로 반환한다`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0, 0)
        val newStart = LocalDateTime.of(2026, 5, 15, 10, 0, 0)

        val succeeded = successor.succeed("FREQ=WEEKLY;UNTIL=20260601T000000", start, newStart)

        val reparsed = RecurrenceRule(succeeded)
        assertTrue(succeeded.contains("FREQ=WEEKLY"))
        assertEquals(2026, reparsed.until.year)
        assertEquals(5, reparsed.until.month) // 0-indexed → 6월
        assertEquals(1, reparsed.until.dayOfMonth)
        assertFalse(succeeded.contains("COUNT="))
    }

    @Test
    fun `무기한 rrule 은 그대로 반환한다`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0, 0)
        val newStart = LocalDateTime.of(2026, 7, 1, 10, 0, 0)

        val succeeded = successor.succeed("FREQ=MONTHLY", start, newStart)

        assertTrue(succeeded.contains("FREQ=MONTHLY"))
        assertFalse(succeeded.contains("COUNT="))
        assertFalse(succeeded.contains("UNTIL="))
        val reparsed = RecurrenceRule(succeeded)
        assertTrue(reparsed.isInfinite)
    }

    @Test
    fun `잘못된 rrule 이면 INVALID_INPUT`() {
        val start = LocalDateTime.of(2026, 1, 1, 0, 0)
        val ex = assertFailsWith<BusinessException> {
            successor.succeed("NOT_AN_RRULE", start, start.plusDays(1))
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }
}
