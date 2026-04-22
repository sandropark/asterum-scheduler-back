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

class RruleShortenerTest {

    private val shortener = RruleShortener()

    @Test
    fun `UNTIL 이 있던 rrule 을 더 이른 시점으로 단축한다`() {
        val until = LocalDateTime.of(2026, 5, 15, 10, 0, 0)

        val shortened = shortener.shorten("FREQ=WEEKLY;UNTIL=20260601T000000", until)

        assertTrue(shortened.contains("FREQ=WEEKLY"))
        assertTrue(shortened.contains("UNTIL=20260515T100000"))
        assertFalse(shortened.contains("COUNT="))
        val reparsed = RecurrenceRule(shortened)
        assertEquals(2026, reparsed.until.year)
        assertEquals(4, reparsed.until.month) // 0-indexed
        assertEquals(15, reparsed.until.dayOfMonth)
        assertEquals(10, reparsed.until.hours)
    }

    @Test
    fun `COUNT 가 있던 rrule 을 UNTIL 로 대체한다`() {
        val until = LocalDateTime.of(2026, 5, 3, 9, 30, 0)

        val shortened = shortener.shorten("FREQ=DAILY;COUNT=10", until)

        assertTrue(shortened.contains("FREQ=DAILY"))
        assertTrue(shortened.contains("UNTIL=20260503T093000"))
        assertFalse(shortened.contains("COUNT="))
    }

    @Test
    fun `무기한 rrule 에 UNTIL 을 추가한다`() {
        val until = LocalDateTime.of(2026, 6, 1, 0, 0, 0)

        val shortened = shortener.shorten("FREQ=MONTHLY", until)

        assertTrue(shortened.contains("FREQ=MONTHLY"))
        assertTrue(shortened.contains("UNTIL=20260601T000000"))
        val reparsed = RecurrenceRule(shortened)
        assertFalse(reparsed.isInfinite)
    }

    @Test
    fun `잘못된 rrule 이면 INVALID_INPUT`() {
        val ex = assertFailsWith<BusinessException> {
            shortener.shorten("NOT_AN_RRULE", LocalDateTime.of(2026, 1, 1, 0, 0))
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }
}
