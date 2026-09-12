package com.pixelpals.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.core.motion.TelaArtworkScale
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.TelaBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exports the reviewed Tela motion frames with their shared camera calibration. */
@RunWith(AndroidJUnit4::class)
class TelaCameraArtworkReviewTest {
    @Test
    fun exportCalibratedTelaMotionFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bridge = TestPetBridge(context, PetType.TELA, 320, PetArtworkScale.forPet(PetType.TELA))
        val behavior = TelaBehavior(bridge, SeededPetRandom(7))
        val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        try {
            val deadline = SystemClock.elapsedRealtime() + 10_000L
            while (loading.getBoolean(behavior) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50L)
            assertTrue("Tela atlas did not load", !loading.getBoolean(behavior))
            val frames = listOf(0) + (4..27).toList()
            val tileSize = 240
            val sheet = Bitmap.createBitmap(tileSize * 3, tileSize * ((frames.size + 2) / 3), Bitmap.Config.ARGB_8888)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 16f }
            val baseline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffd26b6b.toInt(); strokeWidth = 2f }
            instrumentation.runOnMainSync {
                sheet.eraseColor(0xfffaf7ef.toInt())
                val canvas = Canvas(sheet)
                frames.forEachIndexed { slot, frame ->
                    bridge.currentFrame = frame
                    val x = (slot % 3) * tileSize
                    val y = (slot / 3) * tileSize
                    canvas.save()
                    canvas.translate(x.toFloat(), y.toFloat())
                    val centerY = tileSize * .82f
                    behavior.onDraw(canvas, tileSize / 2f, centerY)
                    val groundY = centerY + requireNotNull(behavior.careBaselineOffsetY)
                    canvas.drawLine(0f, groundY, tileSize.toFloat(), groundY, baseline)
                    canvas.drawText("frame $frame  x${TelaArtworkScale.frame(frame)}", 8f, 20f, label)
                    canvas.restore()
                }
            }
            File(context.cacheDir, "tela-camera-calibrated.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            assertEquals(.60f, PetArtworkScale.speciesSize(PetType.TELA), .001f)
            assertEquals(.880f * .60f, PetArtworkScale.forPet(PetType.TELA), .001f)
            assertTrue("Tela camera review must include calibrated frames", TelaArtworkScale.frame(20) != 1f)
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }
}
