package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PiruBodyRenderingTest {
    @Test
    fun happySamplesKeepRigidBodyScaleAndExportRealRendererSheet(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: PiruBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.PIRU)
            behavior = PiruBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                setMode(behavior, "HAPPY")
                setFloat(behavior, "modeTimer", 0f)
                setFloat(behavior, "modeDuration", 2f)
                setFloat(behavior, "animClock", 0f)
                behavior.updateIdle(0f)
                val sheet: Bitmap = Bitmap.createBitmap(900, 220, Bitmap.Config.ARGB_8888)
                try {
                    val canvas: Canvas = Canvas(sheet)
                    canvas.drawColor(Color.rgb(248, 244, 232))
                    val background: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 214, 193) }
                    for (sample: Int in 0..4) {
                        if (sample > 0) repeat(12) { behavior.updateIdle(1f / 60f) }
                        assertEquals("Piru HAPPY changed vertical scale at sample $sample", 1f, bridge.animScaleY, 0f)
                        assertEquals("Piru HAPPY changed horizontal scale at sample $sample", 1f, kotlin.math.abs(bridge.animScaleX), 0f)
                        val centerX: Float = 90f + sample * 180f
                        canvas.drawRect(centerX - 80f, 0f, centerX + 80f, 220f, background)
                        behavior.onDraw(canvas, centerX, 120f)
                    }
                    val output = java.io.File(instrumentation.targetContext.cacheDir, "piru-happy.png")
                    output.outputStream().use { stream -> assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
                } finally {
                    sheet.recycle()
                }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test
    fun dragClearsWaddleOffsetsBeforeRendering(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: PiruBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.PIRU)
            behavior = PiruBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                setMode(behavior, "WADDLE")
                setFloat(behavior, "swimStartX", 0f)
                setFloat(behavior, "swimTargetX", bridge.petSpriteSize * .55f)
                setFloat(behavior, "swimDuration", 1f)
                setFloat(behavior, "modeTimer", .5f)
                bridge.getWindowParams().y = bridge.groundY
                behavior.updateIdle(0f)
                assertTrue("Waddle phase must leave a vertical bob to clear", bridge.animOffsetY < 0f)

                behavior.updateDrag(1f / 60f)

                assertEquals("Drag must clear inherited horizontal offset", 0f, bridge.animOffsetX, 0f)
                assertEquals("Drag must clear inherited waddle bob", 0f, bridge.animOffsetY, 0f)
                assertEquals("Drag must remain upright", 0f, bridge.animRotation, 0f)
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private fun waitForAssets(behavior: PiruBehavior, instrumentation: android.app.Instrumentation): Unit {
        val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        repeat(40) {
            instrumentation.waitForIdleSync()
            if (!loading.getBoolean(behavior)) return
            Thread.sleep(50)
        }
        assertFalse("Piru artwork did not load", loading.getBoolean(behavior))
    }

    private fun setMode(behavior: PiruBehavior, name: String): Unit {
        val field = behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }
        field.set(behavior, field.type.enumConstants!!.first { it.toString() == name })
    }

    private fun setFloat(behavior: PiruBehavior, name: String, value: Float): Unit {
        val field = behavior.javaClass.getDeclaredField(name).apply { isAccessible = true }
        field.setFloat(behavior, value)
    }

    private fun deterministicRandom(): PetRandom = object : PetRandom {
        override fun nextFloat(): Float = .5f
        override fun nextInt(from: Int, until: Int): Int = from
    }
}
