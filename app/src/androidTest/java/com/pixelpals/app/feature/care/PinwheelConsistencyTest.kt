package com.pixelpals.app.feature.care

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.home.DecorationCatalog
import com.pixelpals.app.feature.home.HomeScenePainter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinwheelConsistencyTest {
    @Test fun homeAndCareShareThePinwheelAndOnlyItsBladesRotate() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val home = HomeScenePainter()
        val care = CarePropPainter()
        fun render(angle: Float, inCare: Boolean, homeId: String = "pinwheel"): IntArray {
            bitmap.eraseColor(Color.TRANSPARENT)
            if (inCare) {
                care.toyRotation = angle
                care.draw(Canvas(bitmap), CareSceneAction.PLAY, 100f, 120f, 134f, pet = PetType.TARO)
            } else home.drawObject(Canvas(bitmap), requireNotNull(DecorationCatalog.find(homeId)), RectF(0f, 0f, 200f, 200f), pet = PetType.TARO, toyRotation = angle)
            return IntArray(40_000).also { bitmap.getPixels(it, 0, 200, 0, 0, 200, 200) }
        }
        try {
            val still = render(0f, false)
            val rotated = render(35f, false)
            assertTrue(still.indices.count { still[it] != rotated[it] } > 100)
            // The blade sweep reaches y=151; compare the exposed stem below that sweep.
            for (index in 160 * 200 until 40_000) assertEquals("Support and shadow stay still", still[index], rotated[index])
            for (angle in listOf(0f, 35f)) {
                val homePixels = render(angle, false)
                val carePixels = render(angle, true)
                val starterPixels = render(angle, false, "ball")
                assertTrue("Starter toy matches the placed pinwheel, including its shadow", homePixels.indices.count { homePixels[it] != starterPixels[it] } < 200)
                assertTrue("Same drawing across coordinate spaces", homePixels.indices.count { homePixels[it] != carePixels[it] } < 200)
                care.toyDecorationId = "pinwheel"
                assertArrayEquals(carePixels, render(angle, true))
                care.toyDecorationId = null
            }
        } finally { bitmap.recycle() }
    }
}
