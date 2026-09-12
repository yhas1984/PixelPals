package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class YukiHomeHeatRenderingTest {
    @Test fun hotHomeStopsWalkingAndRecoversWhenBatteryCools(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences: CompanionPreferences = CompanionPreferences(context)
        val previous: Boolean = preferences.reducedMotion
        val sheet: Bitmap = Bitmap.createBitmap(1500, 800, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(sheet)
        try {
            preferences.reducedMotion = false
            instrumentation.runOnMainSync {
                var temperature: Float? = 42f
                val scene: HomeSceneView = HomeSceneView(context).apply {
                    reviewSeed = 7
                    temperatureReader = { temperature }
                }
                runBlocking { scene.loadPet(PetType.YUKI) }
                scene.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
                scene.layout(0, 0, 1000, 760)
                val motion: CompanionMotion = HomeSceneView::class.java.getDeclaredField("motion")
                    .apply { isAccessible = true }.get(scene) as CompanionMotion
                val heat: YukiHomeHeat = HomeSceneView::class.java.getDeclaredField("yukiHeat")
                    .apply { isAccessible = true }.get(scene) as YukiHomeHeat
                val x: Float = motion.x
                val y: Float = motion.y
                repeat(6) { frame ->
                    if (frame == 3) { temperature = 37f; scene.pause(); scene.resume() }
                    repeat(20) { scene.advanceScene(16) }
                    canvas.save()
                    canvas.translate(frame % 3 * 500f, frame / 3 * 400f)
                    canvas.scale(.5f, .5f)
                    scene.draw(canvas)
                    canvas.drawText(if (frame < 3) "42 C" else "37 C", 20f, 755f, Paint().apply { textSize = 28f })
                    canvas.restore()
                    assertEquals(x, motion.x, .001f)
                    assertEquals(y, motion.y, .001f)
                    if (frame == 2) assertTrue(heat.active)
                }
                repeat(10) { scene.advanceScene(16) }
                assertFalse(heat.active)
            }
            File(context.filesDir, "yuki-home-heat.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { preferences.reducedMotion = previous; sheet.recycle() }
    }
}
