package com.elevation.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var value: TextView
    private lateinit var detail: TextView
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        render()
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, updateFilter(), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, updateFilter())
        }
    }

    private fun updateFilter() = IntentFilter(ElevationStore.ACTION_UPDATED).apply {
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_TIME_TICK)
    }

    override fun onResume() {
        super.onResume()
        render()
        loadLatestSyncedValue()
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }

    private fun render() {
        val data = ElevationStore.read(this)
        if (data == null) {
            value.text = "-- m"
            detail.text = "スマホで更新すると\n自動で同期します"
            return
        }
        val sameDay = data.isForDay(LocalDate.now())
        value.text = if (sameDay) "${data.meters.roundToInt()} m" else "-- m"
        detail.text = if (sameDay) {
            "ウォーキング ${data.walkingCount}件  •  ${formatTime(data.updatedAt)}更新"
        } else {
            "最終記録 ${data.date}\nスマホで今日の値を更新"
        }
    }

    private fun loadLatestSyncedValue() {
        Wearable.getDataClient(this).dataItems
            .addOnSuccessListener { items ->
                try {
                    val latest =
                        items
                            .filter { it.uri.path == ElevationDataService.PATH }
                            .mapNotNull { ElevationStore.decode(DataMapItem.fromDataItem(it).dataMap) }
                            .maxByOrNull { it.updatedAt }
                    if (latest != null) {
                        ElevationStore.save(
                            this,
                            latest
                        )
                    }
                    Log.i("ElevationWearSync", "Startup data items=${items.count}")
                } finally {
                    items.release()
                }
                render()
            }
            .addOnFailureListener { error ->
                Log.e("ElevationWearSync", "Startup data query failed", error)
            }
    }

    private fun formatTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("H:mm"))

    private fun buildContent(): FrameLayout {
        val card =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(17), dp(15), dp(15))
                background =
                    roundedBackground(
                        fillColor = Color.parseColor("#0D222D"),
                        radiusDp = 24,
                        strokeColor = Color.parseColor("#1D3A44")
                    )
                addView(
                    TextView(context).apply {
                        text = "TODAY"
                        textSize = 10f
                        letterSpacing = .16f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#79D4B2"))
                    }
                )
                addView(
                    TextView(context).apply {
                        text = "今日の累積標高"
                        textSize = 15f
                        gravity = Gravity.CENTER
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                        setTextColor(Color.parseColor("#DCECE7"))
                    }
                )
                value =
                    TextView(context).apply {
                        text = "-- m"
                        textSize = 39f
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                        setTextColor(Color.parseColor("#F5FFF9"))
                        setPadding(0, dp(5), 0, dp(6))
                    }
                addView(value)
                detail =
                    TextView(context).apply {
                        textSize = 11f
                        gravity = Gravity.CENTER
                        maxLines = 2
                        setLineSpacing(0f, 1.15f)
                        setTextColor(Color.parseColor("#9DB7B0"))
                    }
                addView(detail)
                addView(
                    TextView(context).apply {
                        text = "PHONE SYNC"
                        textSize = 9f
                        letterSpacing = .1f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#79D4B2"))
                        setPadding(dp(11), dp(5), dp(11), dp(5))
                        background =
                            roundedBackground(
                                fillColor = Color.parseColor("#142D38"),
                                radiusDp = 12,
                                strokeColor = Color.parseColor("#31515C")
                            )
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(10)
                    }
                )
            }

        return FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#06131C"))
            setPadding(dp(17), dp(17), dp(17), dp(17))
            addView(
                card,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
    }

    private fun roundedBackground(
        fillColor: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
