package com.pixelpals.app.feature.home

import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecorationTouchPlacementTest {
    @Test fun finishingEditCancelsTheUnplacedObject(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = HomeSceneView(instrumentation.targetContext)
            view.layout(0, 0, 1000, 760)
            var callbackEditing = false
            var placed = false
            view.onEditingChanged = { callbackEditing = it }
            view.onPlace = { _, _, _ -> placed = true }
            view.beginPlacement("ball")
            assertTrue(callbackEditing)
            view.isEditing = false
            assertFalse(callbackEditing)
            for (action: Int in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 0, action, 500f, 470f, 0)
                view.onTouchEvent(event); event.recycle()
            }
            assertFalse(placed)
        }
    }

    @Test fun occupiedDropKeepsTheObjectAvailableForAnotherTap(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = HomeSceneView(instrumentation.targetContext)
            view.layout(0, 0, 1000, 760)
            view.placements = listOf(com.pixelpals.app.database.HomeDecorationEntity("corgi", "bed", 2, 1))
            var placed: String? = null
            view.onPlace = { id, column, row -> placed = id; assertEquals(0, column); assertEquals(0, row) }
            view.beginPlacement("ball")
            fun tap(x: Float, y: Float) {
                for (action: Int in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(0, 0, action, x, y, 0)
                    view.onTouchEvent(event); event.recycle()
                }
            }
            tap(500f, 470f)
            assertNull(placed)
            tap(137f, 403f)
            assertEquals("ball", placed)
        }
    }

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
