package com.pixelpals.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.Cosmetic
import com.pixelpals.app.data.catalog.CosmeticEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetCosmeticSpeciesScaleTest {
    @Test
    fun auraAndFloatCosmeticsFollowTelaSpeciesScale() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val corgi = PetView(context, 1080, 2400, 100, PetType.CORGI).apply { layout(0, 0, 400, 400) }
            val tela = PetView(context, 1080, 2400, 100, PetType.TELA).apply { layout(0, 0, 400, 400) }
            val equipped = PetView::class.java.getDeclaredField("equippedCosmetic").apply { isAccessible = true }
            val draw = PetView::class.java.getDeclaredMethod("drawCosmetic", Canvas::class.java).apply { isAccessible = true }
            val paint = PetView::class.java.getDeclaredField("cosmeticPaint").apply { isAccessible = true }
            val effects = listOf<CosmeticEffect>(
                CosmeticEffect.AuraEffect("★", count = 1, radiusRatio = 1f, speed = 1f, sizeRatio = 1f),
                CosmeticEffect.FloatEffect("★", xRatio = 0f, yRatio = 0f, bobAmplitude = 0f, bobSpeed = 1f, sizeRatio = 1f),
            )
            try {
                effects.forEach { effect ->
                    equipped.set(corgi, Cosmetic("fixture", "fixture", "fixture", "", effect))
                    equipped.set(tela, Cosmetic("fixture", "fixture", "fixture", "", effect))
                    val corgiBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                    val telaBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                    try {
                        draw.invoke(corgi, Canvas(corgiBitmap))
                        val corgiTextSize = (paint.get(corgi) as Paint).textSize
                        draw.invoke(tela, Canvas(telaBitmap))
                        val telaTextSize = (paint.get(tela) as Paint).textSize
                        assertEquals("Tela cosmetic size must follow body scale", corgiTextSize * .60f, telaTextSize, .01f)
                        val corgiBounds = opaqueBounds(corgiBitmap)
                        val telaBounds = opaqueBounds(telaBitmap)
                        assertEquals("Corgi cosmetic must render", true, corgiBounds != null)
                        assertEquals("Tela cosmetic must render", true, telaBounds != null)
                        if (effect is CosmeticEffect.AuraEffect) {
                            val corgiRadius = corgiBounds!!.centerX() - 200f
                            val telaRadius = telaBounds!!.centerX() - 200f
                            assertEquals("Tela aura orbit must follow body scale", corgiRadius * .60f, telaRadius, 3f)
                        }
                    } finally {
                        corgiBitmap.recycle()
                        telaBitmap.recycle()
                    }
                }
            } finally {
                (PetView::class.java.getDeclaredField("uiScope").apply { isAccessible = true }.get(corgi) as CoroutineScope).cancel()
                (PetView::class.java.getDeclaredField("uiScope").apply { isAccessible = true }.get(tela) as CoroutineScope).cancel()
            }
        }
    }

    private fun opaqueBounds(bitmap: Bitmap): Bounds? {
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) ushr 24 > 10) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        return if (right < left) null else Bounds(left, top, right, bottom)
    }

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun centerX(): Float = (left + right) / 2f
    }
}
