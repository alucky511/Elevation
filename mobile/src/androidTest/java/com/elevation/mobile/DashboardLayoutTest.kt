package com.elevation.mobile

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardLayoutTest {
    @Test fun textAndActionsFitAtDifferentWidthsAndFontScales() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (widthDp in listOf(320, 412, 760)) {
                for (scale in listOf(1f, 1.3f, 2f)) {
                    val configuration = Configuration(instrumentation.targetContext.resources.configuration)
                    configuration.fontScale = scale
                    val context = ContextThemeWrapper(
                        instrumentation.targetContext.createConfigurationContext(configuration),
                        android.R.style.Theme_Material_NoActionBar
                    )
                    val dashboard = MobileDashboardView(context)
                    dashboard.elevationText.text = "12,345 m"
                    dashboard.detailText.text = "ウォーキング 12件 • 今日の累積上昇標高"
                    dashboard.statusText.text = "Health Connectの権限を許可してください。保存・設定から権限を変更できます。"
                    dashboard.autoBackupButton.text = "毎日のGPX自動バックアップ: ON\nwalker.long.account@example.com"
                    val density = context.resources.displayMetrics.density
                    val width = (widthDp * density).toInt()
                    val height = (800 * density).toInt()
                    for (page in 0..1) {
                        if (page == 0) dashboard.homeTab.performClick() else dashboard.settingsTab.performClick()
                        assertEquals(page, dashboard.selectedPage)
                        dashboard.measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                        )
                        dashboard.layout(0, 0, width, height)
                        verifyText(dashboard, density)
                        val content = dashboard.getChildAt(0)
                        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.rgb(7, 20, 27))
                        content.draw(canvas)
                        // Let Gradle collect images before it uninstalls the test app.
                        val directory = InstrumentationRegistry.getArguments()
                            .getString("additionalTestOutputDir")?.let { File(it) }
                            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "ui-previews")
                        directory.mkdirs()
                        File(directory, "page-${page}-width-${widthDp}-font-${scale}.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun verifyText(view: View, density: Float) {
        if (view.visibility != View.VISIBLE) return
        if (view is TextView && view.text.isNotEmpty()) {
            val layout = requireNotNull(view.layout)
            assertTrue("Text height clipped: ${view.text}",
                layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
            for (line in 0 until layout.lineCount) {
                assertEquals("Ellipsized: ${view.text}", 0, layout.getEllipsisCount(line))
                assertTrue("Text width clipped: ${view.text}", layout.getLineWidth(line) <= layout.width + 1)
            }
            if (view is Button) assertTrue("Touch target too small", view.height >= 48 * density)
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) verifyText(view.getChildAt(index), density)
    }

    @Test fun launcherArtworkStaysInsideCircularSafeArea() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (id in listOf(R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome)) {
                val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
                val drawable = requireNotNull(instrumentation.targetContext.getDrawable(id))
                drawable.setBounds(0, 0, 432, 432)
                drawable.draw(Canvas(bitmap))
                var visiblePixels = 0
                for (y in 0 until 432) for (x in 0 until 432) {
                    if (Color.alpha(bitmap.getPixel(x, y)) > 128) {
                        visiblePixels++
                        val dx = x + 0.5 - 216
                        val dy = y + 0.5 - 216
                        assertTrue("Artwork outside 66dp safe circle", dx * dx + dy * dy <= 132 * 132)
                    }
                }
                assertTrue(visiblePixels > 0)
                bitmap.recycle()
            }
        }
    }
}
