package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class GingerTreeRenderingTest {
    @Test fun renderExistingPosesOnTheTreeRoute() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val art = HomeLocomotion.load(context, PetType.GINGER)
        val visit = GingerTreeVisit(500f, 650f)
        val motion = CompanionMotion()
        val sheet = Bitmap.createBitmap(1200, 1336, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(250, 247, 239))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; color = Color.rgb(51, 60, 53) }
        val seen = mutableSetOf<GingerTreeVisit.Phase>()
        var index = 0
        repeat(3000) {
            visit.advance(1f / 60f)
            if (visit.elapsed > .15f && seen.add(visit.phase) && index < 12) {
                canvas.save()
                canvas.translate(index % 3 * 400f, index / 3 * 334f)
                canvas.save(); canvas.clipRect(0f, 0f, 400f, 304f); canvas.scale(.4f, .4f)
                HomeScenePainter().drawBackground(canvas, HomeEnvironment.GARDEN, 14, PetType.GINGER)
                val target = RectF(visit.x - 145f, visit.y - 290f, visit.x + 145f, visit.y)
                canvas.save()
                if (visit.facingLeft) canvas.scale(-1f, 1f, visit.x, visit.y)
                art.draw(canvas, paint, target, motion, false, visit.clip, visit.clipSeconds)
                canvas.restore(); canvas.restore()
                canvas.drawText(visit.phase.name, 12f, 326f, paint)
                canvas.restore()
                index++
            }
        }
        assertEquals(GingerTreeVisit.Phase.DONE, visit.phase)
        File(context.getExternalFilesDir(null), "ginger-tree.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sheet.recycle()
    }
}
