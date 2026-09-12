package com.pixelpals.app

import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LanguageSelectorLayoutTest {
    @Test
    fun largeTextKeepsEveryLanguageOptionFullyAccessible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (locale: Locale in listOf(Locale.ENGLISH, Locale.forLanguageTag("es"))) {
                val base: Context = instrumentation.targetContext
                val configuration = Configuration(base.resources.configuration).apply {
                    fontScale = 2f
                    setLocale(locale)
                }
                val context: Context = ContextThemeWrapper(
                    base.createConfigurationContext(configuration),
                    R.style.Theme_PixelPals,
                )
                val root: View = LayoutInflater.from(context).inflate(R.layout.activity_main, null, false)
                val selector: LinearLayout = root.findViewById(R.id.languageSelector)
                val title: TextView = root.findViewById(R.id.txtLanguageTitle)
                val options: RadioGroup = root.findViewById(R.id.languageOptions)
                val radios: List<RadioButton> = listOf(
                    root.findViewById(R.id.btnLanguageSystem),
                    root.findViewById(R.id.btnLanguageEnglish),
                    root.findViewById(R.id.btnLanguageSpanish),
                )

                measureAndLayout(selector, dp(context, 280))
                assertEquals(LinearLayout.VERTICAL, selector.orientation)
                assertEquals(LinearLayout.VERTICAL, options.orientation)
                assertTrue(title.bottom <= options.top)
                for (radio: RadioButton in radios) {
                    assertTrue(radio.height >= dp(context, 48))
                    assertTrue(radio.left >= 0)
                    assertTrue(radio.right <= options.width)
                    assertTrue(radio.top >= 0)
                    assertTrue(radio.bottom <= options.height)
                    assertEquals(radio.text.length, radio.layout.getLineEnd(radio.layout.lineCount - 1))
                    assertEquals(0, radio.layout.getEllipsisCount(radio.layout.lineCount - 1))
                }
            }
        }
    }

    private fun measureAndLayout(view: View, width: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
