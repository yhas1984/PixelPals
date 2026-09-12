package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.PetRandom
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders Ginger's original atlas frames and records their actual alpha bounds. */
@RunWith(AndroidJUnit4::class)
class GingerGroundRenderingTest {
    @Test
    fun exportDecodedGroundedFramesForBothFacingDirections(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var bridge: TestPetBridge
        lateinit var behavior: GingerBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(context, PetType.GINGER, SPRITE_SIZE, PetArtworkScale.forPet(PetType.GINGER))
            behavior = GingerBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior)
            val frames: List<Int> = (0..11).toList() + (13..15).toList() +
                if (hasOptionalPostureArtwork(behavior)) (16..18).toList() else emptyList()
            val directions: List<Pair<String, Float>> = listOf("right" to -1f, "left" to 1f)
            val sheet: Bitmap = Bitmap.createBitmap(CELL_SIZE * DIRECTIONS_PER_ROW, CELL_SIZE * ((frames.size * directions.size + DIRECTIONS_PER_ROW - 1) / DIRECTIONS_PER_ROW), Bitmap.Config.ARGB_8888)
            val records: JSONArray = JSONArray()
            val mismatches: MutableList<String> = mutableListOf()
            val label: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 18f }
            val baselinePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffd26b6b.toInt(); strokeWidth = 2f }
            instrumentation.runOnMainSync {
                sheet.eraseColor(Color.TRANSPARENT)
                frames.flatMap { frame -> directions.map { frame to it } }.forEachIndexed { slot, sample ->
                    val (frame, direction) = sample
                    val tile: Bitmap = Bitmap.createBitmap(CELL_SIZE, CELL_SIZE, Bitmap.Config.ARGB_8888)
                    tile.eraseColor(Color.TRANSPARENT)
                    bridge.currentFrame = frame
                    bridge.animScaleX = direction.second
                    bridge.animScaleY = 1f
                    bridge.animOffsetX = 0f
                    bridge.animOffsetY = 0f
                    bridge.animRotation = 0f
                    behavior.onDraw(Canvas(tile), CENTER_X, CENTER_Y)
                    val bounds: AlphaBounds = findAlphaBounds(tile)
                    val expectedBaseline: Float = CENTER_Y + requireNotNull(behavior.careBaselineOffsetY)
                    val sourceGroundBaseline: Float = CENTER_Y + (SOURCE_GROUND_PX - SOURCE_PIVOT_PX) *
                        SPRITE_SIZE * PetArtworkScale.forPet(PetType.GINGER) / SOURCE_FRAME_SIZE
                    if (!bounds.hasPixels) mismatches += "frame=$frame facing=${direction.first} has no decoded pixels"
                    if (bounds.hasPixels && kotlin.math.abs(bounds.bottom - expectedBaseline) > BASELINE_TOLERANCE) {
                        mismatches += "frame=$frame facing=${direction.first} bottom=${bounds.bottom} expected=$expectedBaseline"
                    }
                    val x: Float = (slot % DIRECTIONS_PER_ROW) * CELL_SIZE.toFloat()
                    val y: Float = (slot / DIRECTIONS_PER_ROW) * CELL_SIZE.toFloat()
                    sheetCanvas(sheet).drawBitmap(tile, x, y, null)
                    sheetCanvas(sheet).drawLine(x, expectedBaseline + y, x + CELL_SIZE, expectedBaseline + y, baselinePaint)
                    sheetCanvas(sheet).drawText("$frame ${direction.first[0]} b${bounds.bottom}", x + 8f, y + 22f, label)
                    records.put(JSONObject().apply {
                        put("frame", frame)
                        put("facing", direction.first)
                        put("alphaThreshold", ALPHA_THRESHOLD)
                        put("left", bounds.left)
                        put("top", bounds.top)
                        put("right", bounds.right)
                        put("bottom", bounds.bottom)
                        put("expectedBaseline", expectedBaseline)
                        put("sourceGroundBaseline", sourceGroundBaseline)
                    })
                    tile.recycle()
                }
            }
            val outputDirectory: File = File(context.filesDir, OUTPUT_DIRECTORY).apply { mkdirs() }
            File(outputDirectory, "ginger-ground-contact-sheet.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(outputDirectory, "ginger-ground-measurements.json").writeText(JSONObject().apply {
                put("pet", "ginger")
                put("spriteSize", SPRITE_SIZE)
                put("spriteScale", PetArtworkScale.forPet(PetType.GINGER))
                put("sourceFrameSize", SOURCE_FRAME_SIZE)
                put("sourcePivotPx", SOURCE_PIVOT_PX)
                put("sourceGroundPx", SOURCE_GROUND_PX)
                put("samples", records)
            }.toString(2))
            sheet.recycle()
            assertTrue("Ginger grounding mismatches (artifacts preserved): $mismatches", mismatches.isEmpty())
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private fun waitForAssets(behavior: GingerBehavior): Unit {
        val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        val deadline: Long = SystemClock.elapsedRealtime() + ASSET_TIMEOUT_MS
        while (loading.getBoolean(behavior) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(ASSET_POLL_MS)
        assertTrue("Ginger atlas did not load", !loading.getBoolean(behavior))
    }

    private fun findAlphaBounds(bitmap: Bitmap): AlphaBounds {
        var left: Int = bitmap.width
        var top: Int = bitmap.height
        var right: Int = -1
        var bottom: Int = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.alpha(bitmap.getPixel(x, y)) < ALPHA_THRESHOLD) continue
            left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
        }
        return AlphaBounds(right >= 0, left, top, right, bottom)
    }

    private fun hasOptionalPostureArtwork(behavior: GingerBehavior): Boolean {
        val rects = BaseBehavior::class.java.getDeclaredField("spriteFrameRects")
            .apply { isAccessible = true }
            .get(behavior) as? List<*>
        return rects?.size == 19 || rects?.size == 22
    }

    private fun sheetCanvas(sheet: Bitmap): Canvas = Canvas(sheet)

    private fun deterministicRandom(): PetRandom = object : PetRandom {
        override fun nextFloat(): Float = .5f
        override fun nextInt(from: Int, until: Int): Int = from
    }

    private data class AlphaBounds(val hasPixels: Boolean, val left: Int, val top: Int, val right: Int, val bottom: Int)

    private companion object {
        const val SPRITE_SIZE: Int = 160
        const val CELL_SIZE: Int = 320
        const val DIRECTIONS_PER_ROW: Int = 4
        const val CENTER_X: Float = 160f
        const val CENTER_Y: Float = 200f
        const val ALPHA_THRESHOLD: Int = 32
        const val BASELINE_TOLERANCE: Float = 3f
        const val SOURCE_FRAME_SIZE: Int = 384
        const val SOURCE_PIVOT_PX: Int = 368
        const val SOURCE_GROUND_PX: Int = 367
        const val ASSET_TIMEOUT_MS: Long = 10_000L
        const val ASSET_POLL_MS: Long = 50L
        const val OUTPUT_DIRECTORY: String = "ginger-ground-review"
    }
}
