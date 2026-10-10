package com.elevation.wear

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WearElevationTest {
    private val value = WearElevation("2026-10-09", 123.0, 2, 2000)

    @Test fun rejectsMalformedPayloads() {
        assertTrue(value.isValid())
        assertFalse(value.copy(date = "invalid").isValid())
        assertFalse(value.copy(meters = Double.NaN).isValid())
        assertFalse(value.copy(meters = Double.POSITIVE_INFINITY).isValid())
        assertFalse(value.copy(meters = -1.0).isValid())
        assertFalse(value.copy(walkingCount = -1).isValid())
        assertFalse(value.copy(updatedAt = 0).isValid())
    }

    @Test fun oldAndDuplicateEventsCannotOverwriteNewData() {
        assertFalse(value.copy(updatedAt = 1000).isNewerThan(value))
        assertFalse(value.isNewerThan(value))
        assertTrue(value.copy(updatedAt = 3000).isNewerThan(value))
        assertTrue(value.isNewerThan(null))
    }

    @Test fun yesterdayIsNotTodayEvenWhenItIsTheLatestSync() {
        assertTrue(value.isForDay(LocalDate.of(2026, 10, 9)))
        assertFalse(value.isForDay(LocalDate.of(2026, 10, 10)))
    }
}
