package com.elevation.wear

import java.time.LocalDate

data class WearElevation(val date: String, val meters: Double, val walkingCount: Int, val updatedAt: Long) {
    fun isValid(): Boolean =
        runCatching { LocalDate.parse(date) }.isSuccess &&
            meters.isFinite() && meters >= 0 && walkingCount >= 0 && updatedAt > 0

    fun isForDay(day: LocalDate): Boolean = date == day.toString()

    fun isNewerThan(current: WearElevation?): Boolean =
        isValid() && (current == null || updatedAt > current.updatedAt)
}
