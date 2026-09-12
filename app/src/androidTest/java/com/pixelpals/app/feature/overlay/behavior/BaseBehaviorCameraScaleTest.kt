package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.domain.PetType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaseBehaviorCameraScaleTest {
    @Test
    fun hitTestAndCareBaselineFollowFrameCameraScale() {
        val bridge = TestPetBridge(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, PetType.TELA)
        val behavior = CameraScaleBehavior(bridge)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 20 until 44) for (x in 20 until 44) bitmap.setPixel(x, y, Color.WHITE)
        val spec = PetAtlasSpec.fromJson(JSONObject("""
            {"version":2,"petId":"fixture","atlasPath":"fixture.png","frameWidth":64,"frameHeight":64,
             "columns":1,"rows":1,"frameCount":1,"pivot":{"x":32,"y":32},
             "clips":[{"id":"idle","frames":[0],"loop":true,"frameDurationMs":100}],
             "frames":[{"index":0,"name":"idle"}]}
        """))
        behavior.install(spec, PetAlphaHitMask.fromBitmap(bitmap, spec))

        behavior.factor = .5f
        assertTrue(behavior.hitTest(40f, 57.2f, 80, 80) == true)
        assertTrue(behavior.hitTest(40f, 40f, 80, 80) == false)
        assertEquals(24.7f, behavior.baseline(), .01f)

        behavior.factor = 1.5f
        assertTrue(behavior.hitTest(40f, 22.8f, 80, 80) == true)
        assertTrue(behavior.hitTest(40f, 74f, 80, 80) == false)
        assertNotEquals(24.7f, behavior.baseline(), .01f)
        assertEquals(5.3f, behavior.baseline(), .01f)
        val asymmetric = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 20 until 44) for (x in 8 until 24) asymmetric.setPixel(x, y, Color.WHITE)
        behavior.install(spec, PetAlphaHitMask.fromBitmap(asymmetric, spec))
        behavior.factor = 1f
        behavior.mirrored = true
        assertTrue(behavior.hitTest(60f, 40f, 80, 80) == true)
        assertTrue(behavior.hitTest(20f, 40f, 80, 80) == false)
        behavior.destroy()
        bitmap.recycle()
        asymmetric.recycle()
    }

    private class CameraScaleBehavior(bridge: PetViewBridge) : BaseBehavior(bridge, object : com.pixelpals.app.core.motion.PetRandom {
        override fun nextFloat(): Float = .5f
        override fun nextInt(from: Int, until: Int): Int = from
    }) {
        var factor: Float = 1f
        var mirrored: Boolean = false
        override val resourceIds: List<Int> = emptyList()
        override fun getFrameCameraScale(index: Int): Float = factor
        override fun isFrameMirrored(index: Int): Boolean = mirrored
        override val frameGround: Float = .93f

        fun install(spec: PetAtlasSpec, mask: PetAlphaHitMask) {
            spriteSheetSpec = spec
            BaseBehavior::class.java.getDeclaredField("spriteHitMask").apply {
                isAccessible = true
                set(this@CameraScaleBehavior, mask)
            }
        }

        fun baseline(): Float = requireNotNull(careBaselineOffsetY)
    }
}
