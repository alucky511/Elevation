package com.elevation.mobile

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.readRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import java.time.LocalDate
import java.time.ZoneId

class GpxBackupRepository(private val context: Context) {
    suspend fun buildGpxForDate(date: LocalDate): GpxBackupBuildResult {
        val client = healthConnectClientOrNull()
            ?: return GpxBackupBuildResult.HealthConnectUnavailable

        val granted = client.permissionController.getGrantedPermissions()
        val exerciseReadPermission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        if (!granted.contains(exerciseReadPermission)) {
            return GpxBackupBuildResult.MissingHealthPermission
        }

        val zone = ZoneId.systemDefault()
        val window =
            TimeWindow(
                start = date.atStartOfDay(zone).toInstant(),
                end = date.plusDays(1).atStartOfDay(zone).toInstant()
            )

        val sessions = readWalkingSessions(client, window)
        if (sessions.isEmpty()) {
            return GpxBackupBuildResult.NoWalkingSessions
        }

        val segments = mutableListOf<GpxTrackSegment>()
        var consentRequiredCount = 0
        var noRouteCount = 0

        sessions.forEachIndexed { index, session ->
            val recordId = session.metadata.id
            if (recordId.isBlank()) {
                noRouteCount += 1
                return@forEachIndexed
            }

            val detailedSession = client.readRecord<ExerciseSessionRecord>(recordId).record
            when (val routeResult = detailedSession.exerciseRouteResult) {
                is ExerciseRouteResult.Data -> {
                    segments += GpxTrackSegment(
                        name = session.title ?: "Walking ${index + 1}",
                        route = routeResult.exerciseRoute
                    )
                }

                is ExerciseRouteResult.ConsentRequired -> {
                    consentRequiredCount += 1
                }

                is ExerciseRouteResult.NoData -> {
                    noRouteCount += 1
                }
            }
        }

        val segmentsWithPoints = segments.filter { it.route.route.orEmpty().isNotEmpty() }
        if (segmentsWithPoints.isEmpty()) {
            return GpxBackupBuildResult.NoExportableRoutes(
                walkingCount = sessions.size,
                consentRequiredCount = consentRequiredCount,
                noRouteCount = noRouteCount
            )
        }

        return GpxBackupBuildResult.Success(
            fileName = GpxExporter.fileNameFor(date),
            gpx = GpxExporter.build(date, segmentsWithPoints),
            walkingCount = sessions.size,
            exportedRouteCount = segmentsWithPoints.size,
            consentRequiredCount = consentRequiredCount,
            noRouteCount = noRouteCount
        )
    }

    private suspend fun healthConnectClientOrNull(): HealthConnectClient? {
        val status = HealthConnectClient.getSdkStatus(
            context,
            HEALTH_CONNECT_PROVIDER_PACKAGE
        )
        return if (status == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    private suspend fun readWalkingSessions(
        client: HealthConnectClient,
        window: TimeWindow
    ): List<ExerciseSessionRecord> = WalkingSessionReader(client).read(window)

}

private const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

sealed interface GpxBackupBuildResult {
    data class Success(
        val fileName: String,
        val gpx: String,
        val walkingCount: Int,
        val exportedRouteCount: Int,
        val consentRequiredCount: Int,
        val noRouteCount: Int
    ) : GpxBackupBuildResult

    data class NoExportableRoutes(
        val walkingCount: Int,
        val consentRequiredCount: Int,
        val noRouteCount: Int
    ) : GpxBackupBuildResult

    data object HealthConnectUnavailable : GpxBackupBuildResult
    data object MissingHealthPermission : GpxBackupBuildResult
    data object NoWalkingSessions : GpxBackupBuildResult
}
