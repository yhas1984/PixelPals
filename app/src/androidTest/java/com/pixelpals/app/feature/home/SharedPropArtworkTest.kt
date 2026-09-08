package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.care.CarePropPainter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPropArtworkTest {
    @Test fun fetchToysReuseHomeGeometry() {
        for (id in listOf("yarn", "star_toy")) {
            val home = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            val desktop = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                HomeScenePainter().drawObject(Canvas(home), requireNotNull(DecorationCatalog.find(id)), RectF(0f, 0f, 400f, 400f))
                CarePropPainter().apply { toyDecorationId = id }.draw(Canvas(desktop), CareSceneAction.PLAY, 200f, 240f, 268f)
                var different = 0
                for (y in 0 until 400) for (x in 0 until 400) if (home.getPixel(x, y) != desktop.getPixel(x, y)) different++
                assertTrue("$id differs in $different pixels", different < 400)
            } finally { home.recycle(); desktop.recycle() }
        }
    }

    @Test fun selectedBedVariantsReuseHomeGeometry() {
        for (id in listOf("moss_bed", "cloud_bed", "moon_bed")) {
            val home = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            val desktop = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                HomeScenePainter().drawObject(Canvas(home), requireNotNull(DecorationCatalog.find(id)), RectF(0f, 0f, 400f, 400f))
                CarePropPainter().apply { bedDecorationId = id }.draw(Canvas(desktop), CareSceneAction.REST, 200f, 308f, 376f)
                var different = 0
                for (y in 0 until 400) for (x in 0 until 400) {
                    if (home.getPixel(x, y) != desktop.getPixel(x, y)) different++
                }
                assertTrue("$id differs in $different pixels", different < 400)
            } finally {
                home.recycle(); desktop.recycle()
            }
        }
    }

    @Test fun everySpeciesStarterUsesTheDesktopDrawing() {
        for (pet in PetType.entries) for (id in listOf("ball", "linen_bed")) {
            val home = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            val reference = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                HomeScenePainter().drawObject(Canvas(home), requireNotNull(DecorationCatalog.find(id)), RectF(0f, 0f, 400f, 400f), pet = pet)
                val canvas = Canvas(reference)
                canvas.scale(4f, 4f)
                CarePropPainter().draw(canvas,
                    if (id == "ball") CareSceneAction.PLAY else CareSceneAction.REST,
                    50f, if (id == "ball") 60f else 77f, if (id == "ball") 67f else 94f, pet = pet)
                var compared = 0
                for (y in 0 until 400) for (x in 0 until 400) {
                    val color = reference.getPixel(x, y)
                    if (color ushr 24 == 255) {
                        assertEquals("$pet $id at $x,$y", color, home.getPixel(x, y))
                        compared++
                    }
                }
                assertTrue("No visible object for $pet $id", compared > 100)
            } finally {
                home.recycle(); reference.recycle()
            }
        }
    }
}
