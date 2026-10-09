package com.elevation.mobile

import androidx.health.connect.client.records.ExerciseRoute
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class GpxTrackSegment(
    val name: String,
    val route: ExerciseRoute
)

object GpxExporter {
    fun fileNameFor(date: LocalDate): String {
        return "walking-${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}.gpx"
    }

    fun build(date: LocalDate, segments: List<GpxTrackSegment>): String {
        val trackName = "${date.format(DateTimeFormatter.ISO_LOCAL_DATE)} Walking"
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="Elevation" xmlns="http://www.topografix.com/GPX/1/1">""")
            appendLine("  <metadata>")
            appendLine("    <name>${trackName.escapeXml()}</name>")
            appendLine("  </metadata>")
            appendLine("  <trk>")
            appendLine("    <name>${trackName.escapeXml()}</name>")

            segments.forEachIndexed { index, segment ->
                val points = segment.route.route.orEmpty()
                if (points.isNotEmpty()) {
                    appendLine("    <trkseg>")
                    points.forEach { location ->
                        append("""      <trkpt lat="${location.latitude}" lon="${location.longitude}">""")
                        appendLine()
                        location.altitude?.let { altitude ->
                            appendLine("        <ele>${altitude.inMeters}</ele>")
                        }
                        appendLine("        <time>${location.time}</time>")
                        appendLine("      </trkpt>")
                    }
                    appendLine("    </trkseg>")
                } else {
                    appendLine("    <!-- ${"Segment ${index + 1}: ${segment.name} has no route points.".escapeXml()} -->")
                }
            }

            appendLine("  </trk>")
            appendLine("</gpx>")
        }
    }

    private fun String.escapeXml(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
