package com.pixelpals.app.feature.overlay.behavior

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.core.thermal.YukiThermalMemory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YukiDesktopHeatHandoffTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var thermalPreferences: android.content.SharedPreferences
    private var hadMeltedKey: Boolean = false
    private var previousMelted: Boolean? = null

    @Before fun prepare(): Unit {
        thermalPreferences = context.getSharedPreferences("yuki_thermal", Context.MODE_PRIVATE)
        hadMeltedKey = thermalPreferences.contains("melted")
        previousMelted = thermalPreferences.getBoolean("melted", false)
        thermalPreferences.edit().putBoolean("melted", false).commit()
    }

    @After fun cleanup(): Unit {
        thermalPreferences.edit().apply {
            if (hadMeltedKey) putBoolean("melted", previousMelted == true) else remove("melted")
        }.commit()
    }

    @Test fun coolingMemoryIsObservedByExistingDesktopBehavior(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: YukiRuntimeBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(context, PetType.YUKI, 160)
            behavior = YukiRuntimeBehavior(bridge, SeededPetRandom(7))
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                behavior.onBatteryTemperatureChanged(42f)
                repeat(80) { behavior.updateIdle(.016f) }
                assertEquals(12, bridge.currentFrame)

                val review = Bitmap.createBitmap(600, 240, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(review)
                    canvas.drawColor(Color.rgb(248, 246, 238))
                    behavior.onDraw(canvas, 150f, 120f)

                    val otherInstance = YukiThermalMemory(context)
                    assertFalse("Cooling did not clear shared memory", otherInstance.update(38f))
                    behavior.onKeyboardVisibilityChanged(false, 0)
                    repeat(80) { behavior.updateIdle(.016f) }
                    assertFalse("Stale hot sample re-latched Yuki", otherInstance.update(null))
                    assertNotEquals("Yuki remained on the first melt frame", 12, bridge.currentFrame)
                    behavior.onDraw(canvas, 450f, 120f)
                    val file = java.io.File(instrumentation.targetContext.cacheDir, "yuki-desktop-heat.png")
                    file.outputStream().use { review.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    review.recycle()
                }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test fun affectionUsesSnowmanPosesAndNeverMeltPool(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: YukiRuntimeBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(context, PetType.YUKI, 160)
            behavior = YukiRuntimeBehavior(bridge, SeededPetRandom(7))
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                behavior.onBatteryTemperatureChanged(25f)
                behavior.updateIdle(0f)
                val review = Bitmap.createBitmap(600, 240, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(review)
                    canvas.drawColor(Color.rgb(248, 246, 238))
                    behavior.onHold()
                    assertEquals(10, bridge.currentFrame)
                    behavior.onDraw(canvas, 150f, 120f)
                    repeat(22) { behavior.updateIdle(.016f) }
                    assertEquals(3, bridge.currentFrame)
                    behavior.onDraw(canvas, 300f, 120f)
                    behavior.onHoldReleased()
                    assertTrue(bridge.currentFrame !in 12..14)
                    behavior.onDraw(canvas, 450f, 120f)
                    val file = java.io.File(instrumentation.targetContext.cacheDir, "yuki-desktop-affection.png")
                    file.outputStream().use { review.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    review.recycle()
                }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test fun hotYukiDoesNotFlashOutOfMeltDuringInteractions(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: YukiRuntimeBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(context, PetType.YUKI, 160)
            behavior = YukiRuntimeBehavior(bridge, SeededPetRandom(7))
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                behavior.onBatteryTemperatureChanged(42f)
                repeat(80) { behavior.updateIdle(.016f) }
                assertEquals(12, bridge.currentFrame)
                val frame = bridge.currentFrame
                val scaleX = bridge.animScaleX
                val scaleY = bridge.animScaleY
                behavior.onInteract()
                assertEquals(frame, bridge.currentFrame)
                assertEquals(scaleX, bridge.animScaleX, .001f)
                assertEquals(scaleY, bridge.animScaleY, .001f)
                behavior.onHold()
                assertEquals(frame, bridge.currentFrame)
                assertEquals(scaleX, bridge.animScaleX, .001f)
                assertEquals(scaleY, bridge.animScaleY, .001f)
                behavior.onHoldReleased()
                assertEquals(frame, bridge.currentFrame)
                assertEquals(scaleX, bridge.animScaleX, .001f)
                assertEquals(scaleY, bridge.animScaleY, .001f)
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private fun waitForAssets(
        behavior: YukiRuntimeBehavior,
        instrumentation: android.app.Instrumentation,
    ): Unit {
        val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        repeat(40) {
            instrumentation.waitForIdleSync()
            if (!loading.getBoolean(behavior)) return
            Thread.sleep(50)
        }
        assertFalse("Yuki artwork did not load", loading.getBoolean(behavior))
    }
}
