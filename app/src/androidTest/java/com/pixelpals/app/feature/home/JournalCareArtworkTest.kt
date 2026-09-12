package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class JournalCareArtworkTest {
    @Test fun careMemoriesUseSpeciesFoodAndActionTools(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            fun render(pet: PetType, action: String): Bitmap {
                val view = MemoryIllustration(context, "care", action, pet)
                view.layout(0, 0, 400, 150)
                return Bitmap.createBitmap(400, 150, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val tela = render(PetType.TELA, "FEED")
            val corgi = render(PetType.CORGI, "FEED")
            val moki = render(PetType.MOKI, "FEED")
            val play = render(PetType.TELA, "PLAY")
            assertFalse("Tela must not have a dog's meal in the diary", tela.sameAs(corgi))
            assertTrue("The shared fly food must stay consistent", tela.sameAs(moki))
            assertFalse("Different care actions need their own illustrations", tela.sameAs(play))
            val sheet = Bitmap.createBitmap(800, 360, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.WHITE)
            val label = Paint().apply { color = Color.BLACK; textSize = 16f }
            listOf(tela, corgi, moki, play).forEachIndexed { index, bitmap ->
                val x: Float = index % 2 * 400f
                val y: Float = index / 2 * 180f
                canvas.drawBitmap(bitmap, x, y, null)
                canvas.drawText(listOf("Tela / feed", "Corgi / feed", "Moki / feed", "Tela / play")[index], x + 10, y + 171, label)
            }
            File(context.cacheDir, "journal-care-artwork.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            listOf(tela, corgi, moki, play, sheet).forEach(Bitmap::recycle)
        }
    }
}
