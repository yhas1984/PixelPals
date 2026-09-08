package com.pixelpals.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.Cosmetic
import com.pixelpals.app.data.catalog.CosmeticEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopCosmeticScaleTest {
    @Test fun turningDoesNotShrinkOrMovePurchasedEffects(): Unit {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val view = PetView(context, 1080, 2400, 100, PetType.CORGI)
            view.layout(0, 0, 400, 400)
            val equipped = PetView::class.java.getDeclaredField("equippedCosmetic").apply { isAccessible = true }
            val draw = PetView::class.java.getDeclaredMethod("drawCosmetic", Canvas::class.java).apply { isAccessible = true }
            try {
                for (effect: CosmeticEffect in listOf(CosmeticEffect.AuraEffect("★"), CosmeticEffect.FloatEffect("★"))) {
                    equipped.set(view, Cosmetic("fixture", "fixture", "fixture", "", effect))
                    val right = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                    val left = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                    try {
                        view.animScaleX = 1f
                        draw.invoke(view, Canvas(right))
                        view.animScaleX = -1f
                        draw.invoke(view, Canvas(left))
                        val pixels = IntArray(400 * 400)
                        right.getPixels(pixels, 0, 400, 0, 0, 400, 400)
                        assertTrue("Effect must be visible", pixels.any { it ushr 24 > 0 })
                        assertTrue("Facing changed accessory size or its orbit", right.sameAs(left))
                    } finally { right.recycle(); left.recycle() }
                }
            } finally {
                (PetView::class.java.getDeclaredField("uiScope").apply { isAccessible = true }.get(view) as CoroutineScope).cancel()
            }
        }
    }
}
