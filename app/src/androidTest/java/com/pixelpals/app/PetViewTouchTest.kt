package com.pixelpals.app

import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetViewTouchTest {
    @Test
    fun tappingMokiLeavesDraggingStateAndStartsInteraction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = PetView(
                context = instrumentation.targetContext,
                screenWidth = 1_080,
                screenHeight = 2_400,
                petSpriteSize = 280,
                petType = PetType.MOKI,
            )
            view.layoutParams = WindowManager.LayoutParams(
                392,
                392,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                x = 300
                y = 600
            }
            val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 480f, 780f, 0)
            val up = MotionEvent.obtain(0L, 40L, MotionEvent.ACTION_UP, 480f, 780f, 0)
            try {
                view.onTouchEvent(down)
                view.onTouchEvent(up)
                assertEquals(PetState.INTERACTING, view.state)
            } finally {
                down.recycle()
                up.recycle()
            }
        }
    }
}
