package com.pixelpals.app

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.care.scene.CorgiFetchPlan
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.care.DesktopCarePlayback
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.PetBehavior
import com.pixelpals.app.feature.overlay.behavior.PetBehaviorFactory
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopCareTransitionTest {
    @Test fun baselineMatchesRenderedFeetForEveryProductionPet(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (pet: PetType in PetType.entries) {
            lateinit var behavior: PetBehavior
            instrumentation.runOnMainSync {
                behavior = PetBehaviorFactory.create(pet, TestPetBridge(instrumentation.targetContext, pet), SeededPetRandom(12))
            }
            try {
                var baseline: Float? = null
                repeat(40) {
                    if (baseline == null) {
                        Thread.sleep(50)
                        instrumentation.runOnMainSync { baseline = behavior.careBaselineOffsetY }
                    }
                }
                assertNotNull("$pet baseline was unavailable after loading", baseline)
                instrumentation.runOnMainSync {
                    val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
                    try {
                        behavior.onDraw(Canvas(bitmap), 160f, 160f)
                        var bottom: Int = 0
                        for (y in 0 until 320) for (x in 0 until 320) {
                            if (Color.alpha(bitmap.getPixel(x, y)) >= 32) bottom = maxOf(bottom, y + 1)
                        }
                        assertTrue("$pet did not render", bottom > 0)
                        assertEquals("$pet care would jump away from its feet", bottom.toFloat(), 160f + baseline!!, 2f)
                    } finally { bitmap.recycle() }
                }
            } finally { instrumentation.runOnMainSync { behavior.destroy() } }
        }
    }

    @Test fun semanticFacingAccountsForGingersMirroredArtwork(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (pet: PetType in PetType.entries) {
                val bridge = TestPetBridge(instrumentation.targetContext, pet)
                val behavior = PetBehaviorFactory.create(pet, bridge, SeededPetRandom(12))
                try {
                    bridge.animScaleX = 1f
                    assertEquals("$pet native facing", pet == PetType.GINGER, behavior.facingLeft)
                    bridge.animScaleX = -1f
                    assertEquals("$pet mirrored facing", pet != PetType.GINGER, behavior.facingLeft)
                } finally { behavior.destroy() }
            }
        }
    }

    @Test fun loadingCareKeepsPoseAndPositionAndCompletionDoesNotForceFrameZero(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Overlay permission required on disposable emulator", Settings.canDrawOverlays(context))
        lateinit var view: PetView
        val playback = DeferredCare()
        val manager = context.getSystemService(WindowManager::class.java)
        var resets: Int = 0
        instrumentation.runOnMainSync {
            view = PetView(context, 1080, 2400, 80, PetType.CORGI)
            val behaviorField = PetView::class.java.getDeclaredField("behaviorLazy").apply { isAccessible = true }
            (behaviorField.get(view) as? PetBehavior)?.destroy()
            val behavior = object : BaseBehavior(view, SeededPetRandom(2)) {
                override val resourceIds: List<Int> = emptyList()
                override val careBaselineOffsetY: Float = -17f
                override fun reset() {
                    resets++
                    view.currentFrame = 12
                    view.windowY = 999
                }
            }
            behaviorField.set(view, behavior)
            PetView::class.java.getDeclaredField("desktopCare").apply { isAccessible = true }.set(view, playback)
            val params = WindowManager.LayoutParams(160, 160, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 300; y = 600
            }
            manager.addView(view, params)
        }
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue("Test window was not attached", view.isAttachedToWindow)
                view.resumeAnimation()
                view.currentFrame = 10
                view.animScaleX = -1f
                view.windowY = 600
                view.startDesktopCare(CareSceneAction.FEED)
                assertTrue("Care did not start", playback.isActive)
                assertTrue("Facing was lost", playback.facingLeft)
                assertEquals("Loading flashed a different frame", 10, view.currentFrame)
                assertEquals("Loading reset the movement controller", 0, resets)
                assertEquals("Loading teleported the pet", 600, view.windowY)
                assertEquals(-1f, view.animScaleX, .001f)
                val output = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(output))
                output.recycle()
                assertEquals("Care discarded the rendered ground anchor", -17f, playback.baselineOffsetY, .001f)
                playback.cancel()
                PetView::class.java.getDeclaredMethod("finishDesktopCare", CareSceneAction::class.java,
                    CareSceneResult::class.java).apply { isAccessible = true }.invoke(view, CareSceneAction.FEED, null)
                assertEquals(1, resets)
                assertEquals("Completion overwrote the controller's pose", 12, view.currentFrame)
            }
        } finally {
            instrumentation.runOnMainSync {
                view.pauseAnimation()
                manager.removeViewImmediate(view)
            }
        }
    }

    private class DeferredCare : DesktopCarePlayback {
        override var isActive: Boolean = false
        override val isMovingPet: Boolean = false
        var facingLeft: Boolean = false
        var baselineOffsetY: Float = Float.NaN
        override fun start(action: CareSceneAction, facingLeft: Boolean, fetchPlan: CorgiFetchPlan?) {
            this.facingLeft = facingLeft
            isActive = true
        }
        override fun advance(deltaSeconds: Float): Unit = Unit
        override fun draw(canvas: Canvas, spriteSize: Int, baselineOffsetY: Float, colorFilter: android.graphics.ColorFilter?): Boolean {
            this.baselineOffsetY = baselineOffsetY
            return false
        }
        override fun cancel() { isActive = false }
    }
}
