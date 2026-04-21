package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RRuleExpanderTest {

    private fun ldt(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): LocalDateTime =
        LocalDateTime.of(y, m, d, h, min, 0)

    @Test
    fun `DAILY - 5일 범위에서 5개 발생일을 반환한다`() {
        val result = RRuleExpander.expand(
            rrule = "FREQ=DAILY",
            dtStart = ldt(2026, 4, 20, 9, 0),
            rangeStart = ldt(2026, 4, 20, 0, 0),
            rangeEnd = ldt(2026, 4, 25, 0, 0),
        )

        assertEquals(5, result.size)
        assertEquals(ldt(2026, 4, 20, 9, 0), result.first())
        assertEquals(ldt(2026, 4, 24, 9, 0), result.last())
    }

    @Test
    fun `DAILY INTERVAL=2 - 격일로 반환한다`() {
        val result = RRuleExpander.expand(
            rrule = "FREQ=DAILY;INTERVAL=2",
            dtStart = ldt(2026, 4, 20, 9, 0),
            rangeStart = ldt(2026, 4, 20, 0, 0),
            rangeEnd = ldt(2026, 4, 27, 0, 0),
        )

        assertEquals(listOf(20, 22, 24, 26), result.map { it.dayOfMonth })
    }

    @Test
    fun `WEEKLY BYDAY - 월_수_금에만 발생한다`() {
        val result = RRuleExpander.expand(
            rrule = "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            dtStart = ldt(2026, 4, 20, 9, 0),
            rangeStart = ldt(2026, 4, 20, 0, 0),
            rangeEnd = ldt(2026, 4, 27, 0, 0),
        )

        assertEquals(listOf(20, 22, 24), result.map { it.dayOfMonth })
    }

    @Test
    fun `UNTIL - 종료일 이후 발생은 제외된다`() {
        val result = RRuleExpander.expand(
            rrule = "FREQ=DAILY;UNTIL=20260422T235959",
            dtStart = ldt(2026, 4, 20, 9, 0),
            rangeStart = ldt(2026, 4, 20, 0, 0),
            rangeEnd = ldt(2026, 4, 30, 0, 0),
        )

        assertEquals(listOf(20, 21, 22), result.map { it.dayOfMonth })
    }

    @Test
    fun `COUNT - 지정 개수만큼만 반환한다`() {
        val result = RRuleExpander.expand(
            rrule = "FREQ=DAILY;COUNT=3",
            dtStart = ldt(2026, 4, 20, 9, 0),
            rangeStart = ldt(2026, 4, 20, 0, 0),
            rangeEnd = ldt(2026, 4, 30, 0, 0),
        )

        assertEquals(3, result.size)
    }

    @Test
    fun `DTSTART - RFC 5545 에 따라 첫 발생으로 포함된다`() {
        val dtStart = ldt(2026, 4, 20, 9, 0)
        val result = RRuleExpander.expand(
            rrule = "FREQ=WEEKLY;BYDAY=WE",
            dtStart = dtStart,
            rangeStart = ldt(2026, 4, 20, 0, 0),
            rangeEnd = ldt(2026, 4, 30, 0, 0),
        )

        assertContains(result, dtStart)
    }

    @Test
    fun `범위가 DTSTART 이전이면 빈 리스트를 반환한다`() {
        val result = RRuleExpander.expand(
            rrule = "FREQ=DAILY",
            dtStart = ldt(2026, 4, 20, 9, 0),
            rangeStart = ldt(2026, 4, 10, 0, 0),
            rangeEnd = ldt(2026, 4, 15, 0, 0),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `무한 반복 규칙도 rangeEnd 에서 종료된다`() {
        val result = RRuleExpander.expand(
            rrule = "FREQ=DAILY",
            dtStart = ldt(2026, 4, 20, 9, 0),
            rangeStart = ldt(2026, 4, 20, 0, 0),
            rangeEnd = ldt(2026, 5, 20, 0, 0),
        )

        assertEquals(30, result.size)
    }

    @Test
    fun `rangeEnd 는 닫힌 구간이라 경계값도 포함된다`() {
        val result = RRuleExpander.expand(
            rrule = "FREQ=DAILY",
            dtStart = ldt(2026, 4, 20, 9, 0),
            rangeStart = ldt(2026, 4, 20, 0, 0),
            rangeEnd = ldt(2026, 4, 22, 9, 0),
        )

        assertEquals(listOf(20, 21, 22), result.map { it.dayOfMonth })
    }

    @Test
    fun `rangeEnd 가 rangeStart 보다 앞이면 IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            RRuleExpander.expand(
                rrule = "FREQ=DAILY",
                dtStart = ldt(2026, 4, 20, 9, 0),
                rangeStart = ldt(2026, 4, 25, 0, 0),
                rangeEnd = ldt(2026, 4, 20, 0, 0),
            )
        }
    }

    @Test
    fun `잘못된 RRULE 은 BusinessException INVALID_INPUT 을 던진다`() {
        val ex = assertThrows<BusinessException> {
            RRuleExpander.expand(
                rrule = "NOT_A_VALID_RULE",
                dtStart = ldt(2026, 4, 20),
                rangeStart = ldt(2026, 4, 20),
                rangeEnd = ldt(2026, 4, 21),
            )
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }

    @Test
    fun `truncateUntilBefore - 무기한 rrule 에 UNTIL 부착`() {
        val result = RRuleExpander.truncateUntilBefore("FREQ=DAILY", LocalDate.of(2026, 4, 22))

        assertEquals("FREQ=DAILY;UNTIL=20260421T235959", result)
    }

    @Test
    fun `truncateUntilBefore - 기존 UNTIL 은 새 UNTIL 로 교체된다`() {
        val result = RRuleExpander.truncateUntilBefore(
            "FREQ=DAILY;UNTIL=20260501T235959",
            LocalDate.of(2026, 4, 22),
        )

        assertEquals("FREQ=DAILY;UNTIL=20260421T235959", result)
    }

    @Test
    fun `truncateUntilBefore - 기존 COUNT 는 제거되고 UNTIL 이 부착된다`() {
        val result = RRuleExpander.truncateUntilBefore(
            "FREQ=DAILY;COUNT=10",
            LocalDate.of(2026, 4, 22),
        )

        assertEquals("FREQ=DAILY;UNTIL=20260421T235959", result)
    }

    @Test
    fun `truncateUntilBefore - 잘못된 rrule 은 INVALID_INPUT`() {
        val ex = assertThrows<BusinessException> {
            RRuleExpander.truncateUntilBefore("NOT_A_VALID_RULE", LocalDate.of(2026, 4, 22))
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }

    @Test
    fun `forNewSeriesFrom - 무기한 rrule 은 입력 그대로 반환`() {
        val result = RRuleExpander.forNewSeriesFrom(
            "FREQ=DAILY",
            ldt(2026, 4, 20, 10, 0),
            LocalDate.of(2026, 4, 22),
        )

        assertEquals("FREQ=DAILY", result)
    }

    @Test
    fun `forNewSeriesFrom - UNTIL rrule 은 입력 그대로 반환`() {
        val result = RRuleExpander.forNewSeriesFrom(
            "FREQ=DAILY;UNTIL=20260501T235959",
            ldt(2026, 4, 20, 10, 0),
            LocalDate.of(2026, 4, 22),
        )

        assertEquals("FREQ=DAILY;UNTIL=20260501T235959", result)
    }

    @Test
    fun `forNewSeriesFrom - DAILY COUNT 는 선택일 이전 발생 횟수만큼 차감된다`() {
        val result = RRuleExpander.forNewSeriesFrom(
            "FREQ=DAILY;COUNT=5",
            ldt(2026, 4, 20, 10, 0),
            LocalDate.of(2026, 4, 22),
        )

        assertEquals("FREQ=DAILY;COUNT=3", result)
    }

    @Test
    fun `forNewSeriesFrom - WEEKLY BYDAY COUNT 는 BYDAY 기준 발생 횟수로 계산된다`() {
        val result = RRuleExpander.forNewSeriesFrom(
            "FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=10",
            ldt(2026, 4, 20, 10, 0),
            LocalDate.of(2026, 4, 27),
        )

        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=7", result)
    }

    @Test
    fun `forNewSeriesFrom - 남은 횟수가 0 이하면 INVALID_INPUT`() {
        val ex = assertThrows<BusinessException> {
            RRuleExpander.forNewSeriesFrom(
                "FREQ=DAILY;COUNT=3",
                ldt(2026, 4, 20, 10, 0),
                LocalDate.of(2026, 4, 25),
            )
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }

    @Test
    fun `forNewSeriesFrom - 잘못된 rrule 은 INVALID_INPUT`() {
        val ex = assertThrows<BusinessException> {
            RRuleExpander.forNewSeriesFrom(
                "NOT_A_VALID_RULE",
                ldt(2026, 4, 20, 10, 0),
                LocalDate.of(2026, 4, 22),
            )
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }
}
