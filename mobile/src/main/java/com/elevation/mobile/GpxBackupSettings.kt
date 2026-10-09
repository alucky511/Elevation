package com.elevation.mobile

import android.content.Context
import java.time.LocalDate

class GpxBackupSettings(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("gpx_backup_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    var accountName: String?
        get() = prefs.getString(KEY_ACCOUNT_NAME, null)
        set(value) {
            prefs.edit().putString(KEY_ACCOUNT_NAME, value).apply()
        }

    var lastStatus: String
        get() = prefs.getString(KEY_LAST_STATUS, "自動バックアップは未実行です。").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LAST_STATUS, value).apply()
        }

    var lastBackedUpDate: LocalDate?
        get() = prefs.getString(KEY_LAST_BACKED_UP_DATE, null)?.let(LocalDate::parse)
        set(value) {
            prefs.edit().putString(KEY_LAST_BACKED_UP_DATE, value?.toString()).apply()
        }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_LAST_STATUS = "last_status"
        private const val KEY_LAST_BACKED_UP_DATE = "last_backed_up_date"
    }
}
