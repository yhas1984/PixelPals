package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.home.DecorationCatalog
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The tray icon must stay pixel-identical to the scene prop at the same size. */
@RunWith(AndroidJUnit4::class)
class CareToolDrawableTest {
    private fun renderDrawable(action: CareSceneAction, pet: PetType = PetType.CORGI,
        bed: String? = null, toy: String? = null): IntArray {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        CareToolDrawable(action, 96, pet, bed, toy).draw(Canvas(bitmap))
        return IntArray(96 * 96).also { bitmap.getPixels(it, 0, 96, 0, 0, 96, 96); bitmap.recycle() }
    }

    private fun renderScene(action: CareSceneAction, pet: PetType = PetType.CORGI,
        bed: String? = null, toy: String? = null): IntArray {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val painter = CarePropPainter().apply { bedDecorationId = bed; toyDecorationId = toy }
        val scale = if (toy != null) .58f else if (bed != null) .80f else .86f
        val offset = if (toy != null) .08f else if (bed != null) .23f else 0f
        painter.draw(Canvas(bitmap), action, 48f, 48f + 96f * offset, 96f * scale, pet = pet)
        return IntArray(96 * 96).also { bitmap.getPixels(it, 0, 96, 0, 0, 96, 96); bitmap.recycle() }
    }

    @Test fun selectedBedAndToyMatchTheSceneAndDifferFromDefaults() {
        for ((action, id) in listOf(CareSceneAction.REST to "moss_bed", CareSceneAction.PLAY to "yarn")) {
            assertTrue("Catalog contains $id", DecorationCatalog.find(id) != null)
            val selected = if (action == CareSceneAction.REST) renderDrawable(action, bed = id)
            else renderDrawable(action, toy = id)
            val expected = if (action == CareSceneAction.REST) renderScene(action, bed = id)
            else renderScene(action, toy = id)
            val default = renderDrawable(action)
            assertArrayEquals("$action $id uses scene geometry", expected, selected)
            assertTrue("$action $id differs from its default", selected.indices.count { selected[it] != default[it] } > 20)
        }
    }

    @Test fun speciesFoodAndActionArtworkRemainDistinct() {
        exportTools()
        val telaFood = renderDrawable(CareSceneAction.FEED, PetType.TELA)
        val corgiFood = renderDrawable(CareSceneAction.FEED, PetType.CORGI)
        val yukiClean = renderDrawable(CareSceneAction.CLEAN, PetType.YUKI)
        val yukiPlay = renderDrawable(CareSceneAction.PLAY, PetType.YUKI)
        assertTrue("Tela food differs from Corgi food", telaFood.indices.count { telaFood[it] != corgiFood[it] } > 20)
        assertTrue("Yuki clean differs from play", yukiClean.indices.count { yukiClean[it] != yukiPlay[it] } > 20)
    }
    @Test fun iconsStayInsideTheirBoundsIncludingThePinwheel() {
        val bitmap = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888)
        val overflows = mutableListOf<String>()
        val pixels = IntArray(288 * 288)
        try {
            for (pet in PetType.entries) for (action in CareSceneAction.entries) {
                bitmap.eraseColor(Color.TRANSPARENT)
                val canvas = Canvas(bitmap)
                canvas.translate(96f, 96f)
                CareToolDrawable(action, 96, pet).draw(canvas)
                bitmap.getPixels(pixels, 0, 288, 0, 0, 288, 288)
                val outside = pixels.indices.firstOrNull {
                    (it % 288 !in 96..191 || it / 288 !in 96..191) && Color.alpha(pixels[it]) >= 8
                }
                if (outside != null) overflows += "$pet $action at ${outside % 288},${outside / 288}"
            }
            assertTrue("Icons overflow: $overflows", overflows.isEmpty())
        } finally { bitmap.recycle() }
    }

    private fun exportTools() {
        val sheet = Bitmap.createBitmap(720, PetType.entries.size * 112, Bitmap.Config.ARGB_8888)
        sheet.eraseColor(Color.rgb(250, 247, 239))
        val canvas = Canvas(sheet)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY; textSize = 12f
        }
        for ((row, pet) in PetType.entries.withIndex()) {
            canvas.drawText(pet.name, 4f, row * 112f + 16f, paint)
            for ((column, action) in CareSceneAction.entries.withIndex()) {
                canvas.save()
                canvas.translate(140f + column * 96f, row * 112f + 16f)
                CareToolDrawable(action, 80, pet).draw(canvas)
                canvas.drawText(action.name, 0f, 94f, paint)
                canvas.restore()
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.filesDir, "care-tool-review.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        sheet.recycle()
    }

}
