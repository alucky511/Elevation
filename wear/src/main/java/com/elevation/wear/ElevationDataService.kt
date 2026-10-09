package com.elevation.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class ElevationDataService : WearableListenerService() {
    override fun onDataChanged(events: com.google.android.gms.wearable.DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == PATH }
            .forEach {
                ElevationStore.save(this, DataMapItem.fromDataItem(it.dataItem).dataMap)
                Log.i("ElevationWearSync", "Received update from phone")
            }
    }

    companion object { const val PATH = "/daily_elevation" }
}
