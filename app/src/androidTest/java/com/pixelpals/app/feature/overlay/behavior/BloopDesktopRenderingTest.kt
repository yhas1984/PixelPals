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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Exports real Bloop desktop poses while checking only controller facts. */
@RunWith(AndroidJUnit4::class)
class BloopDesktopRenderingTest {
    @Test
    fun exportsFloatEscapeDragAndRestWithRealArtwork(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: BloopBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(
                instrumentation.targetContext,
                PetType.BLOOP,
                180,
                PetArtworkScale.forPet(PetType.BLOOP),
            )
            behavior = BloopBehavior(bridge, SeededPetRandom(12))
        }

        try {
            val frames = BaseBehavior::class.java.getDeclaredField("frames").apply { isAccessible = true }
            var loaded = false
            for (attempt in 0 until 80) {
                instrumentation.runOnMainSync {
                    val values = frames.get(behavior) as List<*>
                    loaded = values.size == 7 && values.all { it != null }
                }
                if (loaded) break
                Thread.sleep(50)
            }
            assertTrue("Bloop frames must load before the review", loaded)

            instrumentation.runOnMainSync {
                assertEquals(PetArtworkScale.forPet(PetType.BLOOP), bridge.spriteScale, 0f)
                val sheet = Bitmap.createBitmap(1800, 420, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sheet)
                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 15f
                }
                val metadata = StringBuilder("sample,phase,frame,x,y,scaleX,scaleY,alpha\n")
                try {
                    canvas.drawColor(Color.rgb(250, 247, 239))
                    fun sample(index: Int, phase: String): Unit {
                        val left = index * 300f
                        behavior.onDraw(canvas, left + 150f, 250f)
                        canvas.drawLine(left + 15f, 250f, left + 285f, 250f, label)
                        canvas.drawText("$phase / frame ${bridge.currentFrame}", left + 8f, 305f, label)
                        metadata.append(
                            "$index,$phase,${bridge.currentFrame},${bridge.windowX},${bridge.windowY}," +
                                "${bridge.animScaleX},${bridge.animScaleY},${bridge.animAlpha}\n"
                        )
                    }

                    behavior.updateIdle(0f)
                    assertEquals("Neutral Bloop must keep the configured body scale", 1f, abs(bridge.animScaleX), 0f)
                    sample(0, "float")

                    behavior.onInteract()
                    behavior.updateInteracting(0f)
                    val startX = bridge.windowX
                    val startY = bridge.windowY
                    sample(1, "escape-0s")
                    behavior.updateInteracting(.1f)
                    val afterFirstEscapeX = bridge.windowX
                    val afterFirstEscapeY = bridge.windowY
                    assertTrue(
                        "Escape must advance the window",
                        afterFirstEscapeX != startX || afterFirstEscapeY != startY,
                    )
                    sample(2, "escape-0.1s")
                    behavior.updateInteracting(.1f)
                    assertTrue(
                        "Escape must keep advancing the window",
                        bridge.windowX != afterFirstEscapeX || bridge.windowY != afterFirstEscapeY,
                    )
                    sample(3, "escape-0.2s")

                    behavior.updateDrag(1f / 60f)
                    assertEquals("Drag must not inject width scale", 1f, abs(bridge.animScaleX), 0f)
                    assertEquals("Drag must not inject height scale", 1f, bridge.animScaleY, 0f)
                    sample(4, "drag")

                    behavior.reset()
                    behavior.onScheduledRestRequested(true)
                    behavior.updateIdle(0f)
                    assertEquals("Rest must keep neutral width scale", 1f, abs(bridge.animScaleX), 0f)
                    assertTrue("Ghost breathing stays within its soft-body envelope", abs(bridge.animScaleY - 1f) <= .021f)
                    sample(5, "rest")

                    val directory = instrumentation.targetContext.cacheDir
                    File(directory, "bloop-desktop-review.png").outputStream().use {
                        assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    File(directory, "bloop-desktop-review.csv").writeText(metadata.toString())
                } finally {
                    sheet.recycle()
                }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }
}
