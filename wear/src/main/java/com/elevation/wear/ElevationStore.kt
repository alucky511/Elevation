package com.elevation.wear

import android.content.Context
import com.google.android.gms.wearable.DataMap

data class WearElevation(val date: String, val meters: Double, val walkingCount: Int, val updatedAt: Long)

object ElevationStore {
    private const val PREFS = "elevation"
    const val ACTION_UPDATED = "com.elevation.wear.UPDATED"

    fun save(context: Context, map: DataMap) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("date", map.getString("date"))
            .putLong("meters", map.getDouble("meters").toBits())
            .putInt("walkingCount", map.getInt("walkingCount"))
            .putLong("updatedAt", map.getLong("updatedAt"))
            .apply()
        context.sendBroadcast(android.content.Intent(ACTION_UPDATED).setPackage(context.packageName))
    }

    fun read(context: Context): WearElevation? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val date = prefs.getString("date", null) ?: return null
        return WearElevation(
            date,
            Double.fromBits(prefs.getLong("meters", 0L)),
            prefs.getInt("walkingCount", 0),
            prefs.getLong("updatedAt", 0L),
        )
    }
}
