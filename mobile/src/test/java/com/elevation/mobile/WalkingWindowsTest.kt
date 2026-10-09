package com.elevation.mobile

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WalkingWindowsTest {
    @Test fun mergesOverlappingAndTouchingWindows() {
        fun t(second: Long) = Instant.ofEpochSecond(second)
        val merged = WalkingWindows.merge(listOf(
            TimeWindow(t(30), t(40)),
            TimeWindow(t(0), t(20)),
            TimeWindow(t(15), t(30)),
        ))
        assertEquals(listOf(TimeWindow(t(0), t(40))), merged)
    }
}
