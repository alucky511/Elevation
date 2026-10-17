package com.elevation.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/** Presentation only: Health Connect, export and synchronization remain in the activity. */
class MobileDashboardView(context: Context) : ScrollView(context) {
    private val ink = Color.rgb(239, 247, 243)
    private val muted = Color.rgb(167, 190, 181)
    private val mint = Color.rgb(158, 243, 201)
    val elevationText = label("-- m", 56, ink, true).apply {
        setAutoSizeTextTypeUniformWithConfiguration(20, 56, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
        maxLines = 1
        contentDescription = null
    }
    val detailText = label("今日のウォーキングを読み込みます", 15)
    val healthPermissionStatusText = label("権限を確認しています…", 14)
    val watchSyncStatusText = label("同期状態を確認します", 14)
    val statusText = label("", 14).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    val progressBar = ProgressBar(context).apply {
        visibility = View.INVISIBLE
        indeterminateTintList = ColorStateList.valueOf(mint)
        contentDescription = "処理中"
    }
    val refreshButton = action("今日の標高を更新", true)
    val watchSyncButton = action("Watchに再送")
    val healthPermissionButton = action("Health Connectの権限を許可")
    val exportGpxButton = action("今日のGPXを保存")
    val exportYesterdayGpxButton = action("昨日のGPXを保存")
    val autoBackupButton = action("自動バックアップを設定")
    val homeTab = action("ホーム")
    val settingsTab = action("保存・設定")
    private val home = column()
    private val settings = column()
    var selectedPage: Int = 0
        private set

    init {
        setBackgroundColor(Color.rgb(7, 20, 27))
        isFillViewport = true
        val content = column().apply { setPadding(dp(20), dp(24), dp(20), dp(24)) }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val brand = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(context).apply {
            setImageResource(R.mipmap.ic_elevation_launcher)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
        brand.addView(label("Elevation", 26, ink, true), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        content.addView(brand)
        content.addView(label("歩いた分だけ、高く。", 14), space(8))
        val tabs = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(homeTab, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            addView(settingsTab, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(tabs, space(24))
        content.addView(home, space(20))
        content.addView(settings, space(20))

        home.addView(card().apply {
            addView(label("TODAY  /  今日", 12, mint, true))
            addView(label("累積上昇標高", 20, ink, true), space(10))
            addView(elevationText, space(8))
            addView(detailText, space(4))
            addView(label("Health Connectのウォーキング記録から計算", 12), space(12))
            addView(refreshButton, space(20))
        })
        home.addView(card().apply {
            addView(label("Pixel Watch", 20, ink, true))
            addView(watchSyncStatusText, space(8))
            addView(watchSyncButton, space(16))
        }, space(16))
        home.addView(label("記録の保存と接続権限は「保存・設定」から変更できます。", 13), space(16))

        settings.addView(card().apply {
            addView(label("ルートを保存", 20, ink, true))
            addView(label("GPX形式で書き出します。保存先は端末やGoogle Driveから選べます。", 14), space(8))
            addView(exportGpxButton, space(16))
            addView(exportYesterdayGpxButton, space(10))
        })
        settings.addView(card().apply {
            addView(label("自動バックアップ", 20, ink, true))
            addView(label("前日のルートをGoogle Driveに保存する設定です。", 14), space(8))
            addView(autoBackupButton, space(16))
        }, space(16))
        settings.addView(card().apply {
            addView(label("Health Connect", 20, ink, true))
            addView(healthPermissionStatusText, space(8))
            addView(healthPermissionButton, space(16))
        }, space(16))

        content.addView(progressBar, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(16)
        })
        content.addView(statusText, space(8))
        homeTab.setOnClickListener { showPage(0) }
        settingsTab.setOnClickListener { showPage(1) }
        showPage(0)
        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
    }

    fun showPage(page: Int) {
        selectedPage = if (page == 1) 1 else 0
        home.visibility = if (selectedPage == 0) View.VISIBLE else View.GONE
        settings.visibility = if (selectedPage == 1) View.VISIBLE else View.GONE
        listOf(homeTab, settingsTab).forEachIndexed { index, button ->
            button.isSelected = index == selectedPage
            button.setTextColor(if (button.isSelected) Color.rgb(6, 32, 42) else ink)
            button.background = surface(if (button.isSelected) mint else Color.rgb(22, 43, 52), 18)
        }
        scrollTo(0, 0)
    }

    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun card() = column().apply {
        setPadding(dp(20), dp(22), dp(20), dp(22))
        background = surface(Color.rgb(15, 36, 44), 26)
    }
    private fun label(value: String, size: Int, color: Int = muted, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setLineSpacing(0f, 1.12f)
        }
    private fun action(value: String, primary: Boolean = false) = Button(context).apply {
        text = value
        isAllCaps = false
        textSize = 15f
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(if (primary) Color.rgb(6, 32, 42) else ink)
        minHeight = dp(52)
        minimumHeight = dp(52)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        stateListAnimator = null
        background = RippleDrawable(ColorStateList.valueOf(0x3379D4B2),
            surface(if (primary) mint else Color.rgb(27, 53, 63), 18), null)
    }
    private fun surface(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
    }
    private fun space(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
