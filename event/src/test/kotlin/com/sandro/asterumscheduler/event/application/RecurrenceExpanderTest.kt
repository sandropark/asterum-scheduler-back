package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecurrenceExpanderTest {

    private val expander = RecurrenceExpander(SchedulerPolicyProperties(expansionYears = 10))

    @Test
    fun `FREQ=DAILY COUNT=5 - 5개 occurrence 를 하루 간격으로 반환`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val end = start.plusHours(1)

        val occurrences = expander.expand("FREQ=DAILY;COUNT=5", start, end)

        assertEquals(5, occurrences.size)
        assertEquals(start, occurrences[0].startAt)
        assertEquals(start.plusDays(1), occurrences[1].startAt)
        assertEquals(start.plusDays(2), occurrences[2].startAt)
        assertEquals(start.plusDays(3), occurrences[3].startAt)
        assertEquals(start.plusDays(4), occurrences[4].startAt)
    }

    @Test
    fun `FREQ=WEEKLY UNTIL - UNTIL 이전 회차만 반환`() {
        val start = LocalDateTime.of(2026, 5, 4, 10, 0) // Mon
        val end = start.plusHours(1)

        val occurrences = expander.expand(
            "FREQ=WEEKLY;UNTIL=20260601T000000",
            start,
            end,
        )

        // 5/4, 5/11, 5/18, 5/25 — 6/1 10:00 은 UNTIL 00:00 이후라 제외
        assertEquals(4, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 5, 4, 10, 0), occurrences[0].startAt)
        assertEquals(LocalDateTime.of(2026, 5, 11, 10, 0), occurrences[1].startAt)
        assertEquals(LocalDateTime.of(2026, 5, 18, 10, 0), occurrences[2].startAt)
        assertEquals(LocalDateTime.of(2026, 5, 25, 10, 0), occurrences[3].startAt)
    }

    @Test
    fun `회차의 endAt 은 startAt 에 duration 을 더한 값`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val end = start.plusHours(3).plusMinutes(30) // 3h30m duration

        val occurrences = expander.expand("FREQ=DAILY;COUNT=2", start, end)

        assertEquals(2, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 5, 1, 13, 30), occurrences[0].endAt)
        assertEquals(LocalDateTime.of(2026, 5, 2, 13, 30), occurrences[1].endAt)
    }

    @Test
    fun `FREQ=MONTHLY 무기한 - policy 상한 (now + expansionYears) 까지만 반환`() {
        val shortExpander = RecurrenceExpander(SchedulerPolicyProperties(expansionYears = 1))
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val end = start.plusHours(1)
        val now = LocalDateTime.of(2026, 4, 22, 0, 0) // policyLimit = 2027-04-22

        val occurrences = shortExpander.expand("FREQ=MONTHLY", start, end, now = now)

        // 2026-05-01 부터 매월 1일 → 2027-04-01 까지 (2027-05-01 은 policyLimit 초과). 총 12개
        assertEquals(12, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 5, 1, 10, 0), occurrences.first().startAt)
        assertEquals(LocalDateTime.of(2027, 4, 1, 10, 0), occurrences.last().startAt)
    }

    @Test
    fun `잘못된 rrule 은 BusinessException_INVALID_INPUT 을 던진다`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val end = start.plusHours(1)

        val ex = assertFailsWith<BusinessException> {
            expander.expand("NOT_A_VALID_RRULE", start, end)
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }

    @Test
    fun `COUNT 이 policy 상한을 초과하면 상한에서 끊긴다`() {
        val zeroExpander = RecurrenceExpander(SchedulerPolicyProperties(expansionYears = 0))
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val end = start.plusHours(1)

        // expansionYears=0, now=start → policyLimit == start, DAILY 다음 회차는 policyLimit 초과 → 1개만
        val occurrences = zeroExpander.expand("FREQ=DAILY;COUNT=1000", start, end, now = start)

        assertEquals(1, occurrences.size)
        assertEquals(start, occurrences.first().startAt)
        assertTrue(occurrences.size < 1000, "policy 상한으로 끊겨야 한다")
    }
}
