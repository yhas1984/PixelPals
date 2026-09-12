package com.pixelpals.app.feature.home

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecorationPreviewMeasurementTest {
    @Test fun dialogPreviewLeavesRoomForActionsAndRespectsCompactContainers(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val preview = DecorationPreview(context, DecorationCatalog.all.first())
            val width: Int = View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
            preview.measure(width, View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST))
            assertEquals(HomeUi.dp(context, 150), preview.measuredHeight)
            assertEquals(900, preview.measuredWidth)
            preview.measure(width, View.MeasureSpec.makeMeasureSpec(90, View.MeasureSpec.AT_MOST))
            assertEquals(90, preview.measuredHeight)
            preview.measure(width, View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY))
            assertEquals(100, preview.measuredHeight)
        }
    }
}
