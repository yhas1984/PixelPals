package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetDreamRenderingTest {
    @Test fun desktopDreamsStayInsideSmallWindowsAndRespectReducedMotion(): Unit {
        val painter: PetDreamPainter = PetDreamPainter()
        for (width: Int in listOf(64, 160, 320)) {
            val bitmap: Bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888)
            try {
                fun render(seconds: Float, reduced: Boolean): IntArray {
                    bitmap.eraseColor(Color.TRANSPARENT)
                    painter.drawDesktop(Canvas(bitmap), 0f, width.toFloat(), width * .8f, seconds, reduced)
                    return IntArray(width * width).also { bitmap.getPixels(it, 0, width, 0, 0, width, width) }
                }
                val initial: IntArray = render(0f, false)
                assertTrue(initial.all { Color.alpha(it) == 0 })
                val visible: IntArray = render(3f, false)
                assertTrue(visible.count { Color.alpha(it) > 128 } > 30)
                for (index: Int in 0 until width) {
                    assertEquals(0, Color.alpha(visible[index]))
                    assertEquals(0, Color.alpha(visible[(width - 1) * width + index]))
                    assertEquals(0, Color.alpha(visible[index * width]))
                    assertEquals(0, Color.alpha(visible[index * width + width - 1]))
                }
                assertArrayEquals(render(0f, true), render(20f, true))
            } finally { bitmap.recycle() }
        }
    }
}
