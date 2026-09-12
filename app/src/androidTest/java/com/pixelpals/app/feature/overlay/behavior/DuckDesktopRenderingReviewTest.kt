package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exports Patito's real desktop sequence for visual review. */
@RunWith(AndroidJUnit4::class)
class DuckDesktopRenderingReviewTest {
    @Test
    fun exportsWaddleFlightLandingDragAndReleaseAtTenSamplesPerSecond(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: DuckBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(
                instrumentation.targetContext,
                PetType.PATITO,
                180,
                PetArtworkScale.forPet(PetType.PATITO),
            )
            bridge.getWindowParams().y = bridge.groundY
            bridge.windowY = bridge.groundY
            behavior = DuckBehavior(bridge, deterministicRandom())
        }

        try {
            val frames = BaseBehavior::class.java.getDeclaredField("frames").apply { isAccessible = true }
            val spriteSheet = BaseBehavior::class.java.getDeclaredField("spriteSheetBitmap").apply { isAccessible = true }
            val frameRects = BaseBehavior::class.java.getDeclaredField("spriteFrameRects").apply { isAccessible = true }
            var loaded = false
            for (attempt in 0 until 80) {
                @Suppress("UNCHECKED_CAST")
                val values = frames.get(behavior) as List<*>
                val legacyLoaded = values.size == 10 && values.all { it != null }
                val atlasLoaded = spriteSheet.get(behavior) != null &&
                    (frameRects.get(behavior) as List<*>).size >= 16
                loaded = legacyLoaded || atlasLoaded
                if (loaded) break
                Thread.sleep(50)
            }
            assertTrue("Patito assets must load before the review", loaded)

            instrumentation.runOnMainSync {
                assertEquals(PetArtworkScale.forPet(PetType.PATITO), bridge.spriteScale, 0f)
                behavior.updateIdle(0f)
                val referenceGroundOffset = requireNotNull(behavior.careBaselineOffsetY)
                val directory = File(instrumentation.targetContext.cacheDir, "patito-desktop-review").apply { mkdirs() }
                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 14f
                }
                val metadata = StringBuilder(
                    "sample,timeSeconds,phase,mode,frame,x,y,scaleX,scaleY,rotation,windowFloorOnCanvas\n"
                )
                val observed = linkedSetOf<String>()
                var sample = 0
                var interactionStarted = false
                var dragCaptured = false
                var landed = false
                var flightMoved = false
                var previousFlightX = bridge.windowX
                var previousFlightY = bridge.windowY

                fun capture(phase: String, timeSeconds: Float): Unit {
                    val mode = modeName(behavior)
                    observed += mode
                    val bitmap = Bitmap.createBitmap(260, 300, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.rgb(250, 247, 239))
                        val centerY = 120f
                        // The line follows the window's logical Y so the floor contact
                        // can be inspected while the sprite is airborne.
                        val floorOnCanvas = centerY + referenceGroundOffset + (bridge.groundY - bridge.windowY)
                        canvas.drawLine(12f, floorOnCanvas, 248f, floorOnCanvas, label)
                        for (mark in -1..12) {
                            val x = mark * 24f - bridge.windowX % 24
                            canvas.drawLine(x, floorOnCanvas, x, floorOnCanvas + 5f, label)
                        }
                        behavior.onDraw(canvas, 130f, centerY)
                        canvas.drawText(
                            "$phase / $mode / frame ${bridge.currentFrame}",
                            8f,
                            278f,
                            label,
                        )
                        File(directory, "%04d.png".format(sample)).outputStream().use {
                            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                        metadata.append(
                            "$sample,$timeSeconds,$phase,$mode,${bridge.currentFrame}," +
                                "${bridge.windowX},${bridge.windowY},${bridge.animScaleX}," +
                                "${bridge.animScaleY},${bridge.animRotation},$floorOnCanvas\n"
                        )
                        sample++
                    } finally {
                        bitmap.recycle()
                    }
                }

                // Eight seconds at 60 Hz, with captures every six simulation steps (10 Hz).
                repeat(8 * 60) { tick ->
                    if (!interactionStarted && tick == 30) {
                        behavior.onInteract()
                        interactionStarted = true
                    }

                    if (interactionStarted && !dragCaptured && landed) {
                        behavior.updateDrag(1f / 60f)
                        capture("drag", tick / 60f)
                        dragCaptured = true
                        behavior.reset() // Grounded release after the landing, not an aerial drop.
                    } else if (bridge.state == com.pixelpals.app.core.domain.PetState.INTERACTING) {
                        behavior.updateInteracting(1f / 60f)
                    } else {
                        behavior.updateIdle(1f / 60f)
                    }

                    val mode = modeName(behavior)
                    if (mode == "TAKEOFF" || mode == "FLUTTER" || mode == "LANDING") {
                        if (bridge.windowX != previousFlightX || bridge.windowY != previousFlightY) flightMoved = true
                        previousFlightX = bridge.windowX
                        previousFlightY = bridge.windowY
                    }
                    if (mode == "WADDLE" && observed.contains("QUACK")) landed = true

                    if (tick % 6 == 0) {
                        capture(if (dragCaptured) "release" else mode.lowercase(), tick / 60f)
                    }
                }

                assertTrue("Patito must reach flight", observed.any { it == "TAKEOFF" || it == "FLUTTER" })
                assertTrue("Patito flight must move the window", flightMoved)
                assertTrue("Patito must reach landing recovery", observed.contains("LAND_END"))
                assertTrue("Patito must return to waddle", observed.contains("WADDLE"))
                assertTrue("Patito drag sample must be exported", dragCaptured)
                assertTrue("Patito must include quack pause", observed.contains("QUACK"))

                // Hold the duck in the air, then let its controller own the descent.
                behavior.reset()
                bridge.getWindowParams()!!.x = bridge.screenWidth * 3 / 4
                bridge.updateWindowLayout(bridge.getWindowParams()!!)
                bridge.getWindowParams()!!.y = bridge.groundY - bridge.petSpriteSize * 2
                bridge.updateWindowLayout(bridge.getWindowParams()!!)
                behavior.onInteract()
                repeat(8) { behavior.updateInteracting(1f / 60f) }
                behavior.updateDrag(1f / 60f)
                capture("drag-air", 8f)
                behavior.onRelease(0f, 0f)
                var descentMoved = false
                var previousDescentY = bridge.windowY
                for (tick in 0 until 8 * 60) {
                    behavior.updateInteracting(1f / 60f)
                    if (bridge.windowY != previousDescentY) descentMoved = true
                    previousDescentY = bridge.windowY
                    if (tick % 2 == 0) capture("release-descent", 8f + tick / 60f)
                    if ((modeName(behavior) == "WADDLE" || modeName(behavior) == "QUACK") &&
                        observed.contains("QUACK")) break
                }
                assertTrue("Aerial release must move toward the floor", descentMoved)
                File(directory, "sequence.csv").writeText(metadata.toString())
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private fun modeName(behavior: DuckBehavior): String =
        behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }.get(behavior).toString()

    private fun deterministicRandom(): PetRandom = object : PetRandom {
        override fun nextFloat(): Float = .5f
        override fun nextInt(from: Int, until: Int): Int = from
    }
}
