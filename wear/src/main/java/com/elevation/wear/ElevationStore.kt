package com.elevation.wear

import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.DataMap

object ElevationStore {
    private const val PREFS = "elevation"
    const val ACTION_UPDATED = "com.elevation.wear.UPDATED"

    fun decode(map: DataMap): WearElevation? = runCatching {
        if (!listOf("date", "meters", "walkingCount", "updatedAt").all(map::containsKey)) return null
        WearElevation(
            map.getString("date") ?: return null,
            map.getDouble("meters"),
            map.getInt("walkingCount"),
            map.getLong("updatedAt"),
        ).takeIf { it.isValid() }
    }.getOrNull()

    fun save(context: Context, map: DataMap) {
        decode(map)?.let { save(context, it) }
    }

    @Synchronized
    fun save(context: Context, incoming: WearElevation) {
        if (!incoming.isNewerThan(read(context))) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("date", incoming.date)
            .putLong("meters", incoming.meters.toBits())
            .putInt("walkingCount", incoming.walkingCount)
            .putLong("updatedAt", incoming.updatedAt)
            .apply()
        context.sendBroadcast(Intent(ACTION_UPDATED).setPackage(context.packageName))
    }

    fun read(context: Context): WearElevation? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val date = prefs.getString("date", null) ?: return null
        return WearElevation(
            date,
            Double.fromBits(prefs.getLong("meters", 0L)),
            prefs.getInt("walkingCount", 0),
            prefs.getLong("updatedAt", 0L),
        ).takeIf { it.isValid() }
    }
}
