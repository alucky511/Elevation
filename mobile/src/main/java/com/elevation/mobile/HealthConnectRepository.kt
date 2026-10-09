package com.elevation.mobile

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectRepository(context: Context) {
    private val client = HealthConnectClient.getOrCreate(context)
    val permissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
    )

    suspend fun hasPermissions() =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun readLastSevenDays(
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) = (0L..6L).map { readDay(today.minusDays(it), zone) }

    private suspend fun readDay(date: LocalDate, zone: ZoneId): DailyElevation {
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val sessions = readWalkingSessions(dayStart, dayEnd)
        val windows = WalkingWindows.merge(sessions.map {
            TimeWindow(maxOf(it.startTime, dayStart), minOf(it.endTime, dayEnd))
        })
        return DailyElevation(
            date = date,
            meters = windows.sumOf { aggregateElevation(it) }.coerceAtLeast(0.0),
            walkingCount = sessions.size,
        )
    }

    private suspend fun readWalkingSessions(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        val result = mutableListOf<ExerciseSessionRecord>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = token,
                )
            )
            result += response.records.filter {
                it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            }
            token = response.pageToken
        } while (token != null)
        return result
    }

    private suspend fun aggregateElevation(window: TimeWindow): Double {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(ElevationGainedRecord.ELEVATION_GAINED_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
            )
        )
        return response[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters ?: 0.0
    }

    companion object {
        fun sdkStatus(context: Context) = HealthConnectClient.getSdkStatus(context)
    }
}
