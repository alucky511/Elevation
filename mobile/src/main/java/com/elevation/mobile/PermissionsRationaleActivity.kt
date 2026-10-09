package com.elevation.mobile

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.rgb(6, 19, 28))
            addView(TextView(context).apply {
                text = "健康データについて"
                textSize = 28f
                setTextColor(Color.rgb(245, 255, 249))
            })
            addView(TextView(context).apply {
                text = "このアプリは、毎日のウォーキングの累積標高を表示するために、Health Connectの運動記録と獲得標高だけを読み取ります。データは端末内で処理され、外部サーバーへ送信されません。Pixel Watchへの表示には、ペアリング済み端末間のData Layerを使用します。"
                textSize = 17f
                setTextColor(Color.rgb(157, 183, 176))
                setPadding(0, 32, 0, 32)
            })
            addView(Button(context).apply { text = "閉じる"; setOnClickListener { finish() } })
        })
    }
}
