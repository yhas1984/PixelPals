package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.TaroRuntimeBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders the actual home and desktop loaders; never selects or rewards a stored pet. */
@RunWith(AndroidJUnit4::class)
class TaroArtworkContinuityTest {
    @Test fun homeUsesDesktopAtlasAndKeepsSleepToWakePose(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actor: HomeLocomotion = HomeLocomotion.load(context, PetType.TARO)
        val sprites: Any = requireNotNull(HomeLocomotion::class.java.getDeclaredField("sprites")
            .apply { isAccessible = true }.get(actor))
        val frames: List<*> = sprites.javaClass.getDeclaredField("frames")
            .apply { isAccessible = true }.get(sprites) as List<*>
        val first: Any = requireNotNull(frames.first())
        val loaded: Bitmap = first.javaClass.getDeclaredField("bitmap")
            .apply { isAccessible = true }.get(first) as Bitmap
        val expected: Bitmap = context.assets.open("pets/taro/taro_motion_v2.png").use {
            requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }))
        }
        val sleeping: Bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val waking: Bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val sheet: Bitmap = Bitmap.createBitmap(1440, 1000, Bitmap.Config.ARGB_8888)
        try {
            assertTrue("Home must use the same Taro atlas as desktop, not a debug-only replacement", expected.sameAs(loaded))
            val target: RectF = RectF(20f, 20f, 220f, 220f)
            val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val motion: CompanionMotion = CompanionMotion()
            actor.draw(Canvas(sleeping), paint, target, motion, false, "sleep", 10f)
            actor.draw(Canvas(waking), paint, target, motion, false, "wake", 0f)
            assertTrue("Waking must begin at the settled sleeping pose", sleeping.sameAs(waking))
            val canvas: Canvas = Canvas(sheet)
            canvas.drawColor(Color.rgb(250, 247, 239))
            val label: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 15f }
            val sequences: List<Pair<String, List<Float>>> = listOf(
                "walk" to listOf(0f, .42f, .84f, 1.26f, 1.68f, 2.1f),
                "turn" to listOf(0f, .3f, .6f, .9f, 1.2f, 1.3f),
                "sleep" to listOf(0f, 1.6f, 3.2f, 4.8f, 6.4f, 8f),
                "wake" to listOf(0f, 1.6f, 3.2f, 4.8f, 6.4f, 8f),
            )
            sequences.forEachIndexed { row, (clip, times) ->
                times.forEachIndexed { column, seconds ->
                    canvas.save()
                    canvas.translate(column * 240f, row * 250f)
                    actor.draw(canvas, paint, target, motion, false, clip, seconds)
                    canvas.drawText("$clip / $seconds s", 8f, 244f, label)
                    canvas.restore()
                }
            }
            File(context.cacheDir, "taro-home-clips.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            expected.recycle(); sleeping.recycle(); waking.recycle(); sheet.recycle()
        }
    }

    @Test fun desktopSleepAndWakeStayGroundedAndCanExportContinuousFrames(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val export: Boolean = InstrumentationRegistry.getArguments().getString("exportTaroMotion") == "true"
        val directory: File = File(instrumentation.targetContext.cacheDir, "taro-desktop-wake").apply { if (export) mkdirs() }
        lateinit var bridge: TestPetBridge
        lateinit var behavior: TaroRuntimeBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.TARO, 200, PetArtworkScale.forPet(PetType.TARO))
            behavior = TaroRuntimeBehavior(bridge, object : PetRandom {
                private var first: Boolean = true
                override fun nextFloat(): Float = if (first) { first = false; .99f } else 0f
                override fun nextInt(from: Int, until: Int): Int = from
            })
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            for (attempt: Int in 0 until 60) {
                instrumentation.waitForIdleSync()
                if (!loading.getBoolean(behavior)) break
                Thread.sleep(50)
            }
            assertFalse("Taro artwork did not load", loading.getBoolean(behavior))
            instrumentation.runOnMainSync {
                val bitmap: Bitmap = Bitmap.createBitmap(360, 320, Bitmap.Config.ARGB_8888)
                val canvas: Canvas = Canvas(bitmap)
                val label: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 17f }
                val frames: MutableSet<Int> = mutableSetOf()
                var slept: Boolean = false
                var woke: Boolean = false
                var restingX: Int? = null
                val metadata: StringBuilder = StringBuilder("sample,seconds,frame,sleeping,x,y\n")
                try {
                    repeat(156) { sample ->
                        repeat(2) { behavior.updateIdle(1f / 60f) }
                        frames.add(bridge.currentFrame)
                        if (behavior.isSleeping) { slept = true; restingX = bridge.windowX }
                        if (slept && bridge.currentFrame in 20..23) {
                            woke = true
                            assertEquals("Taro must emerge in place", restingX, bridge.windowX)
                            assertEquals("A shell must not stretch while waking", 1f, bridge.animScaleY, 0f)
                        }
                        if (export) {
                            canvas.drawColor(Color.rgb(250, 247, 239))
                            canvas.drawLine(20f, 240f, 340f, 240f, label)
                            behavior.onDraw(canvas, 180f, 240f)
                            canvas.drawText("Taro / frame ${bridge.currentFrame}", 12f, 286f, label)
                            canvas.drawText(if (behavior.isSleeping) "sleep" else if (bridge.currentFrame in 20..23) "wake" else "idle", 12f, 308f, label)
                            File(directory, "%04d.png".format(sample)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            metadata.append("$sample,${(sample + 1) / 30f},${bridge.currentFrame},${behavior.isSleeping},${bridge.windowX},${bridge.windowY}\n")
                        }
                    }
                    assertTrue("Autonomous sleep must be reached", slept)
                    assertTrue("Desktop must visibly emerge from its shell before standing", woke)
                    assertTrue("Every recovery pose must play", frames.containsAll(listOf(20, 21, 22, 23)))
                    if (export) File(directory, "sequence.csv").writeText(metadata.toString())
                } finally { bitmap.recycle() }
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
}
