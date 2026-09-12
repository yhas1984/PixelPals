package com.pixelpals.app.feature.home

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.JellyBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class JellyScheduledRestRenderingTest {
    @Test fun scheduledSleepPreservesTheActualDesktopBodyOnEntryAndWake(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val art = HomeLocomotion.load(context, PetType.JELLY)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduled = ScheduledPetSleep(context, PetType.JELLY, scope)
        val preferences = CompanionPreferences(context)
        val previousReduced = preferences.reducedMotion
        val tint = PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN)
        val tile = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val metrics = JSONArray()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: JellyBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(context, PetType.JELLY, 160, PetArtworkScale.forPet(PetType.JELLY))
            bridge.animColorFilter = tint
            behavior = JellyBehavior(bridge, SeededPetRandom(71))
        }
        try {
            var reference: Bounds? = null
            for (attempt in 0 until 100) {
                instrumentation.runOnMainSync {
                    tile.eraseColor(0)
                    behavior.updateIdle(0f)
                    behavior.onDraw(Canvas(tile), 160f, 160f)
                }
                reference = magentaBounds(tile)
                if (reference != null) break
                delay(25)
            }
            val idle = requireNotNull(reference) { "Jelly desktop artwork did not load" }
            var baseline = 0f
            instrumentation.runOnMainSync { baseline = requireNotNull(behavior.careBaselineOffsetY) }
            setPrivate(scheduled, "art", art)
            setPrivate(scheduled, "bedLoaded", true)
            val motion = ScheduledPetSleep::class.java.getDeclaredField("motion").apply { isAccessible = true }.get(scheduled) as CompanionMotion
            val activity = CompanionMotion::class.java.getDeclaredField("activity").apply { isAccessible = true }
            val elapsed = CompanionMotion::class.java.getDeclaredField("elapsed").apply { isAccessible = true }
            for (reduced in listOf(false, true)) {
                preferences.reducedMotion = reduced
                for (state in listOf(CompanionActivity.REST, CompanionActivity.WAKE)) {
                    val directory = File(context.cacheDir, "jelly-rest/scheduled-${if (reduced) "reduced" else "normal"}-${state.name.lowercase()}").apply { mkdirs() }
                    for (index in 0..36) {
                        val seconds = index * 1.2f / 36f
                        activity.set(motion, state)
                        elapsed.setFloat(motion, seconds)
                        tile.eraseColor(0)
                        scheduled.draw(Canvas(tile), 160f, baseline, false, tint)
                        val body = requireNotNull(magentaBounds(tile))
                        assertTrue("$state $seconds ground", abs(body.bottom - idle.bottom) <= 2)
                        assertTrue("$state $seconds area ${body.count} / ${idle.count}", abs(body.count - idle.count) <= idle.count * .05f)
                        assertTrue("$state $seconds clipped", body.left > 0 && body.top > 0 && body.right < 320 && body.bottom < 320)
                        if ((state == CompanionActivity.REST && !reduced && index == 0) ||
                            (state == CompanionActivity.WAKE && (reduced || index == 36))) {
                            assertTrue("awake width", abs(body.width - idle.width) <= idle.width * .05f)
                            assertTrue("awake height", abs(body.height - idle.height) <= idle.height * .05f)
                        }
                        if (state == CompanionActivity.REST && (reduced || index == 36)) {
                            assertTrue("sleep widens the gel", body.width > idle.width)
                            assertTrue("sleep lowers the gel", body.height < idle.height)
                        }
                        metrics.put(JSONObject().put("state", state.name).put("reduced", reduced)
                            .put("seconds", seconds.toDouble()).put("area", body.count).put("width", body.width)
                            .put("height", body.height).put("bottom", body.bottom))
                        tile.eraseColor(0)
                        scheduled.draw(Canvas(tile), 160f, baseline, false, null)
                        File(directory, "%04d.png".format(index)).outputStream().use { tile.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
            File(context.cacheDir, "jelly-rest/scheduled-metrics.json").writeText(JSONObject()
                .put("referenceArea", idle.count).put("referenceWidth", idle.width).put("referenceHeight", idle.height)
                .put("referenceBottom", idle.bottom).put("samples", metrics).toString(2))
        } finally {
            preferences.reducedMotion = previousReduced
            scope.cancel()
            instrumentation.runOnMainSync { behavior.destroy() }
            tile.recycle()
        }
    }

    private fun setPrivate(instance: Any, name: String, value: Any?): Unit = ScheduledPetSleep::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(instance, value)

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int, val count: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    private fun magentaBounds(bitmap: Bitmap): Bounds? {
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1; var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.forEachIndexed { index, color ->
            if (Color.alpha(color) >= 32 && Color.red(color) > 200 && Color.green(color) < 20 && Color.blue(color) > 200) {
                val x = index % bitmap.width; val y = index / bitmap.width
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y); count++
            }
        }
        return if (count == 0) null else Bounds(left, top, right, bottom, count)
    }
}
