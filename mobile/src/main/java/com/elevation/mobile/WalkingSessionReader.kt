package com.elevation.mobile

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter

/** Shared by summaries, manual export and scheduled backup. */
class WalkingSessionReader(private val client: HealthConnectClient) {
    suspend fun read(window: TimeWindow): List<ExerciseSessionRecord> {
        if (window.start >= window.end) return emptyList()
        val records = mutableListOf<ExerciseSessionRecord>()
        var token: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
                    ascendingOrder = true,
                    pageSize = 1000,
                    pageToken = token,
                )
            )
            records += page.records.filter {
                it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            }
            token = page.pageToken
        } while (token != null)
        return records
    }
}
