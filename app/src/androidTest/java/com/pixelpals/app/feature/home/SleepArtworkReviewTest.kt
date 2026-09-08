package com.pixelpals.app.feature.home

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CareBed
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.PetCareProfile
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.care.CarePropPainter
import com.pixelpals.app.feature.care.PetDreamPainter
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Review artifact, not a substitute for human visual acceptance. */
@RunWith(AndroidJUnit4::class)
class SleepArtworkReviewTest {
    @Test fun exportIdleAndSleepAtIdenticalDesktopScale(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val label: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 16f }
        val prop: CarePropPainter = CarePropPainter()
        val dream: PetDreamPainter = PetDreamPainter()
        for ((group, pets) in PetType.entries.chunked(5).withIndex()) {
            val sheet: Bitmap = Bitmap.createBitmap(640, pets.size * 280, Bitmap.Config.ARGB_8888)
            try {
                sheet.eraseColor(Color.rgb(250, 247, 239))
                val canvas: Canvas = Canvas(sheet)
                for ((row, pet) in pets.withIndex()) {
                    val art: HomeLocomotion = HomeLocomotion.load(context, pet)
                    for (sleep: Boolean in listOf(false, true)) {
                        val offset: Float = if (sleep) 320f else 0f
                        canvas.save()
                        canvas.translate(offset, row * 280f)
                        canvas.clipRect(0f, 0f, 320f, 280f)
                        canvas.drawText("${pet.name} / ${if (sleep) "REST" else "IDLE"}", 8f, 22f, label)
                        val motion: CompanionMotion = CompanionMotion()
                        if (sleep) repeat(300) { motion.advanceScheduledRest(true, 1f / 60f) }
                        val ground: Float = 240f
                        if (sleep && PetCareProfile.forPet(pet).bed != CareBed.WING_WRAP) {
                            prop.draw(canvas, CareSceneAction.REST, 160f, ground, 180f * 1.08f, pet = pet)
                        }
                        art.draw(canvas, paint, RectF(70f, ground - 180f, 250f, ground), motion, false)
                        if (sleep) dream.drawDesktop(canvas, 160f, ground, 180f, 5f, false)
                        canvas.restore()
                    }
                }
                File(context.cacheDir, "sleep-review-$group.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { sheet.recycle() }
        }
    }
}
