package com.elevation.mobile

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object GpxBackupScheduler {
    private const val JOB_ID = 2401

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val service = ComponentName(context, DailyGpxBackupJobService::class.java)
        val job =
            JobInfo.Builder(JOB_ID, service)
                .setMinimumLatency(delayUntilNextBackupWindowMillis())
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(JOB_ID)
    }

    private fun delayUntilNextBackupWindowMillis(): Long {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val todayWindow = LocalDate.now(ZoneId.systemDefault()).atTime(LocalTime.of(2, 30))
        val nextWindow = if (now.isBefore(todayWindow)) todayWindow else todayWindow.plusDays(1)
        return java.time.Duration.between(now, nextWindow).toMillis()
    }
}
