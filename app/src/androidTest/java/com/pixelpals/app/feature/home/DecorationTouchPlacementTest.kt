package com.pixelpals.app.feature.home

import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecorationTouchPlacementTest {
    @Test fun inventoryObjectCanBePlacedWithOneTapWithoutCoordinates(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = HomeSceneView(instrumentation.targetContext)
            view.layout(0, 0, 1000, 760)
            var placed: String? = null
            view.onPlace = { id, column, row -> placed = id; assertEquals(2, column); assertEquals(1, row) }
            view.beginPlacement("ball")
            for (action: Int in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 0, action, 500f, 470f, 0)
                view.onTouchEvent(event); event.recycle()
            }
            assertEquals("ball", placed)
        }
    }
}
