package com.pixelpals.app.feature.home

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HomeActionRowTest {
    @Test fun largeTextKeepsLocalizedActionsReadableAndClickable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (language: String in listOf("es", "en")) {
                val context = instrumentation.targetContext.createConfigurationContext(Configuration(instrumentation.targetContext.resources.configuration).apply {
                    fontScale = 2f
                    setLocale(Locale.forLanguageTag(language))
                })
                var clicks: Int = 0
                val add: Button = HomeUi.button(context, context.getString(R.string.home_add_object)) { clicks++ }
                val done: Button = HomeUi.button(context, context.getString(R.string.home_done), true) { clicks++ }
                val row: LinearLayout = HomeUi.row(context, add, done)
                val width: Int = HomeUi.dp(context, 280)
                measure(row, width)
                assertEquals(LinearLayout.VERTICAL, row.orientation)
                for (button: Button in listOf(add, done)) {
                    assertTrue(button.height >= HomeUi.dp(context, 48))
                    assertTrue(button.width > width * .9f)
                    assertEquals(button.text.length, button.layout.getLineEnd(button.layout.lineCount - 1))
                    assertEquals(0, button.layout.getEllipsisCount(button.layout.lineCount - 1))
                    assertTrue(button.bottom <= row.height)
                    button.performClick()
                }
                assertEquals(2, clicks)
                val bitmap: Bitmap = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
                try {
                    row.draw(Canvas(bitmap))
                    val directory: File = File(context.cacheDir, "home-action-rows").apply { mkdirs() }
                    File(directory, "$language-large.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun resizingRestoresColumnsAndHiddenActionsDoNotReserveSpace() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext.createConfigurationContext(Configuration(instrumentation.targetContext.resources.configuration).apply { fontScale = 1f })
            val first: Button = HomeUi.button(context, "Decorate") {}
            val second: Button = HomeUi.button(context, "Share") {}
            val row: LinearLayout = HomeUi.row(context, first, second)
            measure(row, HomeUi.dp(context, 360))
            assertEquals(LinearLayout.HORIZONTAL, row.orientation)
            measure(row, HomeUi.dp(context, 240))
            assertEquals(LinearLayout.VERTICAL, row.orientation)
            measure(row, HomeUi.dp(context, 360))
            assertEquals(LinearLayout.HORIZONTAL, row.orientation)
            assertTrue(kotlin.math.abs(first.width - second.width) <= 1)
            second.visibility = View.GONE
            measure(row, HomeUi.dp(context, 240))
            assertEquals(LinearLayout.HORIZONTAL, row.orientation)
            assertTrue(first.width > row.width * .9f)
        }
    }

    @Test fun boundaryMarginsAndUnspecifiedWidthKeepActionsMeasurable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext.createConfigurationContext(Configuration(instrumentation.targetContext.resources.configuration).apply { fontScale = 1f })
            val first: Button = HomeUi.button(context, "Decorate") {}
            val second: Button = HomeUi.button(context, "Share") {}
            val row: LinearLayout = HomeUi.row(context, first, second)
            val boundaryWidth: Int = HomeUi.dp(context, 288)
            measure(row, boundaryWidth)
            assertEquals(LinearLayout.VERTICAL, row.orientation)

            row.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            row.layout(0, 0, row.measuredWidth, row.measuredHeight)
            assertEquals(LinearLayout.VERTICAL, row.orientation)
            assertTrue(row.measuredWidth > 0)
            assertTrue(first.measuredWidth > 0)
            assertTrue(second.measuredWidth > 0)
        }
    }

    private fun measure(view: View, width: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}
