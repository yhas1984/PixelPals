package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.motion.PetArtworkScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Exercises actual window travel, including the 120 Hz case that used to stall. */
class NubeMichiTravelTest {
    @Test fun freeDriftCoversTheSameDistanceAt30Through120HzInBothDirections() {
        for (direction in listOf(-1f, 1f)) {
            for (hz in listOf(30, 60, 120)) withCloud { cloud, bridge ->
                mode(cloud, "PUFF_FLOAT")
                number(cloud, "driftSpeedX", direction * 32.24f)
                repeat(2 * hz) { cloud.updateIdle(1f / hz) }
                assertEquals("Two seconds of drift at $hz Hz", 300f + direction * 64.48f, bridge.windowX.toFloat(), 1f)
                assertTrue("Breathing must preserve direction at $hz Hz", bridge.animScaleX * direction > 0f)
            }
        }
    }

    @Test fun featherAndReturnTravelAreIndependentOfRefreshRate() {
        for (phase in listOf("FEATHER_FALL", "CLOUD_RETURN")) {
            val ends = mutableListOf<Pair<Int, Int>>()
            for (hz in listOf(30, 60, 120)) withCloud { cloud, bridge ->
                cloud.onInteract()
                mode(cloud, phase)
                number(cloud, "returnTargetY", 440f)
                repeat(hz) { cloud.updateInteracting(1f / hz) }
                ends += bridge.windowX to bridge.windowY
                if (phase == "FEATHER_FALL") {
                    assertTrue("Feather must descend gradually", bridge.windowY in 655..683)
                } else {
                    assertEquals("Cloud rises at 130 px/s", 470f, bridge.windowY.toFloat(), 1f)
                }
            }
            assertTrue("$phase horizontal distance must not depend on Hz: $ends", ends.maxOf { it.first } - ends.minOf { it.first } <= 1)
            assertTrue("$phase vertical distance must not depend on Hz: $ends", ends.maxOf { it.second } - ends.minOf { it.second } <= 1)
        }
    }

    @Test fun pausedInteractionDoesNotMoveEvenWithFractionalTravelPending() = withCloud { cloud, bridge ->
        for (phase in listOf("FEATHER_FALL", "CLOUD_RETURN")) {
            cloud.onInteract()
            mode(cloud, phase)
            number(cloud, "returnTargetY", 440f)
            cloud.updateInteracting(1f / 120f)
            val position = bridge.windowX to bridge.windowY
            repeat(30) { cloud.updateInteracting(0f) }
            assertEquals("No elapsed time means no travel in $phase", position, bridge.windowX to bridge.windowY)
        }
    }

    @Test fun windCanOccurDuringTheNormalSleepCycle() = withCloud { cloud, bridge ->
        repeat(300) { cloud.updateIdle(1f / 60f) }
        assertTrue("A gust chosen at waking must move the cloud", bridge.windowX > 300)
        assertTrue("Use the existing wind poses", bridge.currentFrame in listOf(4, 6, 7))
    }

    @Test fun exportsLoadedDesktopPosesAtTheirActualScale() = withCloud { cloud, bridge ->
        val sheet = Bitmap.createBitmap(1440, 280, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.rgb(247, 244, 234))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 16f }
            val metadata = StringBuilder("phase,frame,x,y,scaleX,scaleY\n")
            listOf("SLEEP_FLOAT", "WAKE_UP", "WIND_GUST", "PUFF_FLOAT", "FEATHER_FALL", "CLOUD_RETURN").forEachIndexed { index, phase ->
                mode(cloud, phase)
                number(cloud, "stateTimer", 0f)
                number(cloud, "gustTimer", 2f)
                number(cloud, "driftSpeedX", -32.24f)
                number(cloud, "returnTargetY", 440f)
                if (index < 4) cloud.updateIdle(.1f) else cloud.updateInteracting(.1f)
                cloud.onDraw(canvas, index * 240f + 120f, 125f)
                canvas.drawText(phase, index * 240f + 10f, 225f, paint)
                metadata.append("$phase,${bridge.currentFrame},${bridge.windowX},${bridge.windowY},${bridge.animScaleX},${bridge.animScaleY}\n")
            }
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            File(directory, "nube-desktop-review.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "nube-desktop-review.csv").writeText(metadata.toString())
        } finally { sheet.recycle() }
    }

    private fun withCloud(block: (NubeMichiBehavior, TestPetBridge) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var cloud: NubeMichiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.NUBE_MICHI, 180, PetArtworkScale.forPet(PetType.NUBE_MICHI))
            cloud = NubeMichiBehavior(bridge, object : PetRandom {
                override fun nextFloat(): Float = .1f
                override fun nextInt(from: Int, until: Int): Int = from
            })
        }
        try {
            val frames = BaseBehavior::class.java.getDeclaredField("frames").apply { isAccessible = true }
            var loaded = false
            for (attempt in 0 until 80) {
                instrumentation.runOnMainSync {
                    val values = frames.get(cloud) as List<*>
                    loaded = values.size == 11 && values.none { it == null }
                }
                if (loaded) break
                Thread.sleep(50)
            }
            assertTrue("All cloud poses must load before testing movement", loaded)
            instrumentation.runOnMainSync { block(cloud, bridge) }
        } finally { instrumentation.runOnMainSync { cloud.destroy() } }
    }

    private fun mode(cloud: NubeMichiBehavior, value: String) {
        val field = cloud.javaClass.getDeclaredField("mode").apply { isAccessible = true }
        field.set(cloud, field.type.enumConstants!!.first { it.toString() == value })
    }

    private fun number(cloud: NubeMichiBehavior, name: String, value: Float) {
        cloud.javaClass.getDeclaredField(name).apply { isAccessible = true }.setFloat(cloud, value)
    }
}
