package com.elevation.mobile

/** A route fallback estimate; Health Connect's recorded gain remains the primary source. */
object RouteElevationCalculator {
    private const val SMOOTHING_RADIUS = 2
    private const val MAX_VERTICAL_ACCURACY_METERS = 30.0
    private const val MIN_STEP_METERS = 0.75
    private const val MAX_STEP_METERS = 30.0

    data class Sample(val altitude: Double?, val verticalAccuracy: Double? = null)

    fun calculate(samples: List<Sample>): Double {
        var total = 0.0
        val segment = mutableListOf<Double>()
        fun flush() {
            total += calculateSegment(segment)
            segment.clear()
        }
        for (sample in samples) {
            val altitude = sample.altitude
            val accuracy = sample.verticalAccuracy
            if (altitude == null || !altitude.isFinite() ||
                (accuracy != null && (!accuracy.isFinite() || accuracy < 0 ||
                    accuracy > MAX_VERTICAL_ACCURACY_METERS))
            ) {
                // Do not infer a climb across missing or rejected altitude points.
                flush()
            } else {
                segment += altitude
            }
        }
        flush()
        return total
    }

    private fun calculateSegment(altitudes: List<Double>): Double {
        val smoothed = altitudes.indices.map { index ->
            altitudes.subList(
                maxOf(0, index - SMOOTHING_RADIUS),
                minOf(altitudes.size, index + SMOOTHING_RADIUS + 1),
            ).average()
        }
        return smoothed.zipWithNext().sumOf { (previous, current) ->
            (current - previous).takeIf { it in MIN_STEP_METERS..MAX_STEP_METERS } ?: 0.0
        }
    }
}
