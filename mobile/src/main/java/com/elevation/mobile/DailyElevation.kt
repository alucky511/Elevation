package com.elevation.mobile

import java.time.Instant
import java.time.LocalDate

data class DailyElevation(val date: LocalDate, val meters: Double, val walkingCount: Int)
data class TimeWindow(val start: Instant, val end: Instant)

/** Merges overlaps so duplicated Health Connect sessions are not counted twice. */
object WalkingWindows {
    fun merge(input: List<TimeWindow>): List<TimeWindow> {
        val sorted = input.filter { it.start < it.end }.sortedBy { it.start }
        if (sorted.isEmpty()) return emptyList()
        val result = mutableListOf<TimeWindow>()
        var current = sorted.first()
        for (next in sorted.drop(1)) {
            if (!next.start.isAfter(current.end)) {
                current = TimeWindow(current.start, maxOf(current.end, next.end))
            } else {
                result += current
                current = next
            }
        }
        result += current
        return result
    }
}
