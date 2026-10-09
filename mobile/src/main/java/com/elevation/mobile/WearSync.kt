package com.elevation.mobile

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object WearSync {
    const val PATH = "/daily_elevation"

    suspend fun send(context: Context, value: DailyElevation): WearSyncResult {
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putString("date", value.date.toString())
            dataMap.putDouble("meters", value.meters)
            dataMap.putInt("walkingCount", value.walkingCount)
            dataMap.putLong("updatedAt", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).awaitCompletion()
        val connectedNodes = Wearable.getNodeClient(context).connectedNodes.awaitCompletion()
        Log.i(TAG, "Data item stored; connectedNodes=${connectedNodes.size}")
        return WearSyncResult(connectedNodes.size)
    }

    private suspend fun <T> Task<T>.awaitCompletion(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                continuation.resume(result)
            }
            addOnFailureListener { error ->
                continuation.resumeWithException(error)
            }
            addOnCanceledListener {
                continuation.cancel()
            }
        }

    private const val TAG = "ElevationWearSync"
}

data class WearSyncResult(val connectedNodeCount: Int)
