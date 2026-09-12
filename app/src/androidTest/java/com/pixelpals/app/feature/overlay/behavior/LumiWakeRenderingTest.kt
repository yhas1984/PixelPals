package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.SeededPetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A real loaded LumiBehavior, including its normal bridge transforms and source camera. */
@RunWith(AndroidJUnit4::class)
class LumiWakeRenderingTest {
    @Test fun emergesBeforeRespondingToAffectionAndExportsTheSequence(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: LumiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.LUMI, 180, PetArtworkScale.forPet(PetType.LUMI))
            behavior = LumiBehavior(bridge, SeededPetRandom(12))
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            for (attempt: Int in 0 until 60) {
                instrumentation.waitForIdleSync()
                if (!loading.getBoolean(behavior)) break
                Thread.sleep(50)
            }
            assertFalse("Lumi atlas must load before the review", loading.getBoolean(behavior))
            instrumentation.runOnMainSync {
                behavior.onScheduledRestRequested(true)
                repeat(600) { behavior.updateIdle(1f / 60f) }
                assertEquals("A long sleep holds the curled pose", 35, bridge.currentFrame)
                val x: Int = bridge.windowX
                val y: Int = bridge.windowY
                val sheet: Bitmap = Bitmap.createBitmap(1800, 320, Bitmap.Config.ARGB_8888)
                val canvas: Canvas = Canvas(sheet)
                val label: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 16f }
                val metadata: StringBuilder = StringBuilder("sample,frame,x,y,scaleX,scaleY\n")
                try {
                    canvas.drawColor(Color.rgb(250, 247, 239))
                    for (sample: Int in 0..5) {
                        when (sample) {
                            1 -> {
                                behavior.onScheduledRestRequested(false)
                                behavior.updateIdle(1f / 60f)
                                behavior.onInteract()
                            }
                            in 2..5 -> repeat(13) { behavior.updateInteracting(1f / 60f) }
                        }
                        val expected: Int = listOf(35, 35, 34, 33, 32, 24)[sample]
                        assertEquals("Wake sample $sample", expected, bridge.currentFrame)
                        assertEquals("Lumi wakes in place", x, bridge.windowX)
                        assertEquals("Lumi stays supported", y, bridge.windowY)
                        assertEquals("No artificial width change", 1f, kotlin.math.abs(bridge.animScaleX), 0f)
                        assertEquals("No artificial height change", 1f, bridge.animScaleY, 0f)
                        val left: Float = sample * 300f
                        canvas.drawLine(left + 15f, 250f, left + 285f, 250f, label)
                        behavior.onDraw(canvas, left + 150f, 250f)
                        canvas.drawText("${if (sample == 0) "sleep" else if (sample == 5) "affection" else "wake"} / frame ${bridge.currentFrame}", left + 8f, 294f, label)
                        metadata.append("$sample,${bridge.currentFrame},${bridge.windowX},${bridge.windowY},${bridge.animScaleX},${bridge.animScaleY}\n")
                    }
                    val directory: File = instrumentation.targetContext.cacheDir
                    File(directory, "lumi-wake-review.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    File(directory, "lumi-wake-review.csv").writeText(metadata.toString())
                } finally { sheet.recycle() }
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
}
