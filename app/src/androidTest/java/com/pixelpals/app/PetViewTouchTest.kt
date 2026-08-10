package com.pixelpals.app

import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
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
            // El view es 2x el sprite (espacio para cosméticos); lo anclamos en el
            // origen y tocamos en el CENTRO del view = centro del sprite, que es
            // donde el radio de captura (0.55x del sprite) acepta el toque.
            view.layoutParams = WindowManager.LayoutParams(
                560,
                560,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                x = 0
                y = 0
            }
            // Medir y layoutear el view: el radio de captura del toque usa width/height
            // reales del view, que sin attach son 0 y rechazarían cualquier toque.
            val spec = View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY)
            view.measure(spec, spec)
            view.layout(0, 0, 560, 560)
            val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 280f, 280f, 0)
            val up = MotionEvent.obtain(0L, 40L, MotionEvent.ACTION_UP, 280f, 280f, 0)
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
