package com.elevation.mobile

import android.Manifest
import android.accounts.Account
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DailyGpxBackupJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobLock = Any()
    private var runningJob: Job? = null
    private var activeParams: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    DailyGpxBackupRunner(applicationContext).run()
                } finally {
                    val completedNormally =
                        synchronized(jobLock) {
                            if (activeParams !== params) {
                                false
                            } else {
                                activeParams = null
                                runningJob = null
                                true
                            }
                        }
                    if (completedNormally) {
                        if (GpxBackupSettings(applicationContext).enabled) {
                            GpxBackupScheduler.schedule(applicationContext)
                        }
                        jobFinished(params, false)
                    }
                }
            }
        synchronized(jobLock) {
            activeParams = params
            runningJob = job
        }
        job.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val jobToCancel =
            synchronized(jobLock) {
                if (activeParams !== params) {
                    null
                } else {
                    activeParams = null
                    runningJob.also { runningJob = null }
                }
            }
        jobToCancel?.cancel()
        return true
    }

    override fun onDestroy() {
        synchronized(jobLock) {
            activeParams = null
            runningJob = null
        }
        scope.cancel()
        super.onDestroy()
    }
}

class DailyGpxBackupRunner(private val context: Context) {
    suspend fun run() {
        val settings = GpxBackupSettings(context)
        if (!settings.enabled) return

        val accountName = settings.accountName
        if (accountName.isNullOrBlank()) {
            settings.lastStatus = "Googleアカウント未設定のため、自動バックアップを実行できませんでした。"
            notifyUser("GPXバックアップ未完了", settings.lastStatus)
            return
        }

        if (!isNetworkAvailable()) {
            settings.lastStatus = "ネットワーク未接続のため、自動バックアップをスキップしました。"
            return
        }

        val backupDate = LocalDate.now(ZoneId.systemDefault()).minusDays(1)
        if (settings.lastBackedUpDate == backupDate) return

        try {
            when (val built = GpxBackupRepository(context).buildGpxForDate(backupDate)) {
                is GpxBackupBuildResult.Success -> uploadBuiltGpx(settings, accountName, backupDate, built)

                is GpxBackupBuildResult.NoExportableRoutes -> {
                    settings.lastStatus =
                        if (built.consentRequiredCount > 0) {
                            "${backupDate} のルートはHealth Connectの同意が必要なため、自動保存できませんでした。アプリを開いて手動書き出ししてください。"
                        } else {
                            "${backupDate} にGPX化できるルート情報がありませんでした。"
                        }
                    if (built.consentRequiredCount > 0) {
                        notifyUser("GPXバックアップに同意が必要です", settings.lastStatus)
                    }
                }

                GpxBackupBuildResult.HealthConnectUnavailable -> {
                    settings.lastStatus = "Health Connectを利用できないため、自動バックアップを実行できませんでした。"
                }

                GpxBackupBuildResult.MissingHealthPermission -> {
                    settings.lastStatus = "Health Connectの権限がないため、自動バックアップを実行できませんでした。"
                    notifyUser("GPXバックアップ未完了", settings.lastStatus)
                }

                GpxBackupBuildResult.NoWalkingSessions -> {
                    settings.lastStatus = "${backupDate} のウォーキング記録はありませんでした。"
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: UserRecoverableAuthException) {
            settings.lastStatus = "Drive認証の更新が必要です。アプリを開いて自動バックアップを再設定してください。"
            notifyUser("Drive認証が必要です", settings.lastStatus)
        } catch (error: Throwable) {
            settings.lastStatus = "自動バックアップに失敗しました: ${error.safeMessage()}"
            notifyUser("GPXバックアップ失敗", settings.lastStatus)
        }
    }

    private suspend fun uploadBuiltGpx(
        settings: GpxBackupSettings,
        accountName: String,
        backupDate: LocalDate,
        built: GpxBackupBuildResult.Success
    ) {
        var accessToken: String? = null
        try {
            accessToken = GoogleAuthUtil.getToken(
                context,
                Account(accountName, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE),
                "oauth2:$DRIVE_FILE_SCOPE"
            )
            val uploaded = DriveUploader().uploadGpx(accessToken, built.fileName, built.gpx)
            settings.lastBackedUpDate = backupDate
            settings.lastStatus = "${backupDate} のGPXをDriveへ保存しました: ${uploaded.name}"
        } finally {
            accessToken?.let { GoogleAuthUtil.clearToken(context, it) }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun notifyUser(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "GPXバックアップ",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val contentIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
                .setSmallIcon(R.drawable.ic_stat_gpx_backup)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun Throwable.safeMessage(): String {
        return message?.take(120) ?: javaClass.simpleName
    }

    companion object {
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val NOTIFICATION_CHANNEL_ID = "gpx_backup"
        private const val NOTIFICATION_ID = 1001
    }
}
