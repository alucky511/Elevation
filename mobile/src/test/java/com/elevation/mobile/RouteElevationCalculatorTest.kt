package com.elevation.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteElevationCalculatorTest {
    private fun sample(height: Double?) = RouteElevationCalculator.Sample(height)

    @Test fun emptyFlatAndDescendingRoutesHaveNoGain() {
        for (heights in listOf(emptyList(), listOf(100.0), List(10) { 100.0 }, (10 downTo 0).map { it * 5.0 })) {
            assertEquals(0.0, RouteElevationCalculator.calculate(heights.map(::sample)), 0.001)
        }
    }

    @Test fun preservesExistingFivePointSmoothingForValidSamples() {
        assertEquals(30.0, RouteElevationCalculator.calculate((0..8).map { sample(it * 5.0) }), 0.001)
    }

    @Test fun doesNotBridgeMissingOrInaccurateAltitude() {
        val invalid = listOf(sample(null), sample(Double.NaN), RouteElevationCalculator.Sample(20.0, 31.0))
        for (gap in invalid) {
            val samples = List(4) { sample(0.0) } + gap + List(4) { sample(30.0) }
            assertEquals(0.0, RouteElevationCalculator.calculate(samples), 0.001)
        }
    }
}
