package com.sandro.asterumscheduler.event.application

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.time.YearMonth

data class MonthlyEventQuery(
    @field:NotNull @field:Min(2000) @field:Max(2100)
    val year: Int,
    @field:NotNull @field:Min(1) @field:Max(12)
    val month: Int,
) {
    @get:JsonIgnore
    val from: LocalDateTime get() = YearMonth.of(year, month).atDay(1).atStartOfDay()

    @get:JsonIgnore
    val to: LocalDateTime get() = from.plusMonths(1)
}
