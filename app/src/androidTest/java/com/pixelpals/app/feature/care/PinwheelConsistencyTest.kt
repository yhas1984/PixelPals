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
    @Test fun taroDoesNotDragThePinwheelSupportDuringCare(): Unit = kotlinx.coroutines.runBlocking {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val pack = CarePoseLoader.load(context.assets, PetType.TARO)
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val renderer = SpeciesCareRenderer()
        try {
            for (desktop in listOf(false, true)) {
                var firstFoot: Pair<Int, Int>? = null
                for (elapsed in listOf(0L, 900L, 1_800L, 2_700L, 3_600L, 4_500L)) {
                    val scene = com.pixelpals.app.core.care.scene.CareSceneController(CareSceneAction.PLAY,
                        com.pixelpals.app.core.care.scene.CareSceneMode.AUTOMATIC, pack.spec.timings.getValue(CareSceneAction.PLAY))
                    scene.advance(elapsed)
                    bitmap.eraseColor(Color.TRANSPARENT)
                    renderer.draw(Canvas(bitmap), pack, scene, false, false, desktopSize = if (desktop) 160 else null)
                    val pixels = IntArray(320 * 320)
                    bitmap.getPixels(pixels, 0, 320, 0, 0, 320, 320)
                    val pole = pixels.indices.filter { pixels[it] == 0xFFA78365.toInt() }
                    assertTrue("Visible wooden support at $elapsed", pole.isNotEmpty())
                    val bottom = pole.maxOf { it / 320 }
                    val foot = pole.filter { it / 320 == bottom }.map { it % 320 }.average().toInt() to bottom
                    firstFoot?.let { assertEquals("Support must not follow Taro", it, foot) }
                    firstFoot = foot
                    val expectedGround = if (desktop) 160f + 160f * .46f else 320f * .88f
                    assertTrue("Support reaches the ground", kotlin.math.abs(bottom - expectedGround) <= 2f)
                }
            }
        } finally { bitmap.recycle(); pack.bitmap.recycle() }
    }

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
