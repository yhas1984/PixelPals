package com.pixelpals.app.feature.care

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.R
import com.pixelpals.app.core.care.scene.CareSceneAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CarePanelTextLayoutTest {
    @Test
    fun localizedCareActionsRemainCompleteAtLargeTextAndNarrowWidths() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (fontScale: Float in listOf(1f, 2f)) {
                for (locale: Locale in listOf(Locale.ENGLISH, Locale.forLanguageTag("es"))) {
                    for (widthDp: Int in listOf(280, 360)) {
                        val context: Context = createContext(instrumentation.targetContext, locale, fontScale)
                        val panel: CareScenePanel = CareScenePanel(context)
                        measureAndLayout(panel, dp(context, widthDp))
                        val actionButtons: List<Button> = descendants(panel).filterIsInstance<Button>().filter {
                            it.text in CareSceneAction.entries.map { action -> action.getStringForAction(context) }
                        }
                        assertEquals(6, actionButtons.size)
                        val grid: View = panel.getChildAt(2)
                        for (button: Button in actionButtons) {
                            assertTrue(button.height >= dp(context, 62))
                            assertTrue(button.bottom <= grid.height)
                            assertTrue(button.right <= grid.width)
                            assertNotNull(button.compoundDrawables[1])
                            assertEquals(button.text.length, button.layout.getLineEnd(button.layout.lineCount - 1))
                            assertEquals(0, button.layout.getEllipsisCount(button.layout.lineCount - 1))
                            assertTrue("The label must fit below its tool icon", button.layout.height <=
                                button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                        }
                        val footer: LinearLayout = panel.getChildAt(panel.childCount - 1) as LinearLayout
                        val footerButtons: List<Button> = descendants(footer).filterIsInstance<Button>()
                        assertTrue(footerButtons.filter { it.visibility == View.VISIBLE }.all { it.height >= dp(context, 48) && it.bottom <= footer.height })
                        panel.findViewByText(context.getString(R.string.dashboard_retry)).visibility = View.VISIBLE
                        measureAndLayout(panel, dp(context, widthDp))
                        val visibleFooterButtons: List<Button> = descendants(footer).filterIsInstance<Button>().filter { it.visibility == View.VISIBLE }
                        assertEquals(2, visibleFooterButtons.size)
                        visibleFooterButtons.forEach {
                            assertTrue("${it.text}: height=${it.height}, bottom=${it.bottom}, footer=${footer.height}, font=$fontScale, width=$widthDp, locale=$locale",
                                it.height >= dp(context, 48) && it.bottom <= footer.height)
                        }
                        assertTrue(visibleFooterButtons.all { it.layout.height <= it.height - it.compoundPaddingTop - it.compoundPaddingBottom })
                    }
                }
            }
        }
    }

    private fun createContext(base: Context, locale: Locale, fontScale: Float): Context {
        val configuration = Configuration(base.resources.configuration).apply { this.fontScale = fontScale; setLocale(locale) }
        return ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.Theme_PixelPals)
    }

    private fun descendants(view: View): List<View> {
        if (view !is ViewGroup) return listOf(view)
        return listOf(view) + (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    }

    private fun measureAndLayout(view: View, width: Int): Unit {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun CareSceneAction.getStringForAction(context: Context): String = context.getString(CareScenePanel.label(this))

    private fun CareScenePanel.findViewByText(text: String): Button = descendants(this).filterIsInstance<Button>().first { it.text == text }
}
