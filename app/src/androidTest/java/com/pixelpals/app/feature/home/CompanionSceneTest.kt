package com.pixelpals.app.feature.home

import com.pixelpals.app.feature.care.PetDreamPainter

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.database.HomeDecorationEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CompanionSceneTest {
    private var originalReducedMotion: Boolean = false
    @org.junit.Before fun enableMotionForAnimationChecks(): Unit {
        val preferences = CompanionPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        originalReducedMotion = preferences.reducedMotion
        preferences.reducedMotion = false
    }
    @org.junit.After fun restoreMotionPreference(): Unit {
        CompanionPreferences(InstrumentationRegistry.getInstrumentation().targetContext).reducedMotion = originalReducedMotion
    }
    @Test fun changingDepthAndBondDoesNotResizeActor(): Unit {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context: Context = ApplicationProvider.getApplicationContext()
            val scene: HomeSceneView = HomeSceneView(context).apply { reviewSeed = 42 }
            scene.placements = listOf(HomeDecorationEntity("corgi", "linen_bed", 0, 0))
            runBlocking { scene.loadPet(PetType.CORGI) }
            scene.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(304, View.MeasureSpec.EXACTLY))
            scene.layout(0, 0, 400, 304)
            val bitmap: Bitmap = Bitmap.createBitmap(400, 304, Bitmap.Config.ARGB_8888)
            scene.draw(Canvas(bitmap))
            val standingSize: Float = scene.renderedActorSize
            scene.bond = 100
            scene.requestMotionReview(CompanionIntent.REST)
            var attempts: Int = 0
            while (scene.activity != CompanionActivity.REST && attempts++ < 2400) scene.advanceScene(50)
            assertTrue(scene.activity == CompanionActivity.REST)
            scene.draw(Canvas(bitmap))
            org.junit.Assert.assertEquals("Depth or bond resized the same pet", standingSize, scene.renderedActorSize, .001f)
            bitmap.recycle()
        }
    }

    @Test fun lyingDownReducesHeightWithoutInflatingTheBody(): Unit {
        val source: Bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val paint: Paint = Paint().apply { color = Color.RED }
        Canvas(source).drawRect(20f, 20f, 80f, 80f, paint)
        Canvas(source).drawRect(120f, 50f, 180f, 80f, paint)
        val frames: HomeSpriteFrames = HomeSpriteFrames(listOf(source to Rect(0, 0, 100, 100), source to Rect(100, 0, 200, 100)))
        val result: Bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        frames.draw(Canvas(result), paint, RectF(0f, 0f, 100f, 100f), 0)
        frames.draw(Canvas(result), paint, RectF(100f, 0f, 200f, 100f), 1)
        fun rowWidth(offset: Int, row: Int): Int = (0 until 100).count { Color.alpha(result.getPixel(offset + it, row)) > 128 }
        org.junit.Assert.assertEquals(rowWidth(0, 80), rowWidth(100, 80))
        assertTrue(rowWidth(0, 30) > 0 && rowWidth(100, 30) == 0)
        source.recycle(); result.recycle()
    }

    @Test fun dreamCloudStaysStillWithReducedMotion(): Unit {
        val painter: PetDreamPainter = PetDreamPainter()
        val first: Bitmap = Bitmap.createBitmap(1000, 760, Bitmap.Config.ARGB_8888)
        val second: Bitmap = Bitmap.createBitmap(1000, 760, Bitmap.Config.ARGB_8888)
        painter.draw(Canvas(first), 500f, 650f, 300f, 2f, true)
        painter.draw(Canvas(second), 500f, 650f, 300f, 20f, true)
        assertTrue("Reduced motion must freeze both cloud and dream motif", first.sameAs(second))
        assertTrue("The dream cloud must be visible", first.getPixel(578, 382) ushr 24 > 0)
        first.recycle(); second.recycle()
    }

    @Test fun transparentDecorationDoesNotChangeNextBackgroundOpacity(): Unit {
        val painter: HomeScenePainter = HomeScenePainter()
        val first: Bitmap = Bitmap.createBitmap(1000, 760, Bitmap.Config.ARGB_8888)
        val second: Bitmap = Bitmap.createBitmap(1000, 760, Bitmap.Config.ARGB_8888)
        painter.drawBackground(Canvas(first), HomeEnvironment.NIGHT, 22)
        painter.drawObject(Canvas(Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)), requireNotNull(DecorationCatalog.find("ball")), RectF(0f, 0f, 150f, 150f))
        painter.drawBackground(Canvas(second), HomeEnvironment.NIGHT, 22)
        assertTrue("A translucent object changed the following sky", first.sameAs(second))
        first.recycle(); second.recycle()
    }

    @Test fun atlasPaddingAndResolutionDoNotChangeVisibleSizeOrFloorContact(): Unit {
        val paint = Paint()
        val padded = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val larger = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        paint.color = Color.RED
        Canvas(padded).drawRect(10f, 10f, 50f, 60f, paint)
        Canvas(larger).drawRect(80f, 90f, 160f, 190f, paint)
        val first = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
        val target = RectF(20f, 20f, 120f, 120f)
        HomeSpriteFrames(listOf(padded to Rect(0, 0, 100, 100))).draw(Canvas(first), paint, target, 0)
        HomeSpriteFrames(listOf(larger to Rect(0, 0, 200, 200))).draw(Canvas(second), paint, target, 0)
        fun visibleBounds(bitmap: Bitmap): Rect {
            val bounds = Rect(bitmap.width, bitmap.height, 0, 0)
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) >= 128) {
                    bounds.left = minOf(bounds.left, x); bounds.top = minOf(bounds.top, y)
                    bounds.right = maxOf(bounds.right, x + 1); bounds.bottom = maxOf(bounds.bottom, y + 1)
                }
            }
            return bounds
        }
        // API versions filter edge pixels differently; the contract is geometry, not byte identity.
        org.junit.Assert.assertEquals(Rect(38, 36, 102, 116), visibleBounds(first))
        org.junit.Assert.assertEquals("Transparent padding moved or resized the pet", visibleBounds(first), visibleBounds(second))
        listOf(padded, larger, first, second).forEach { it.recycle() }
    }

    @Test fun reviewDecoratedHomeAcrossMovementAndRest(): Unit {
        val context: Context = ApplicationProvider.getApplicationContext()
        val directory = File(context.getExternalFilesDir(null), "home-transitions").apply { mkdirs() }
        // Tela's web replaces the floor furniture route; its hunt/rest path has a dedicated render test.
        for (pet in PetType.entries.filter { it != PetType.TELA }) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val scene = HomeSceneView(context).apply { reviewSeed = 42 }
                scene.placements = listOf(
                    HomeDecorationEntity(pet.name.lowercase(), DecorationCatalog.starters.first { it.kind == DecorationKind.BED }.id, 0, 0),
                    HomeDecorationEntity(pet.name.lowercase(), DecorationCatalog.starters.first { it.kind == DecorationKind.TOY }.id, 4, 2),
                )
                runBlocking { scene.loadPet(pet) }
                scene.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(304, View.MeasureSpec.EXACTLY))
                scene.layout(0, 0, 400, 304)
                val sheet = Bitmap.createBitmap(1200, 330, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sheet)
                canvas.drawColor(HomeUi.cream)
                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HomeUi.ink; textSize = 16f }
                listOf(CompanionActivity.GREET, CompanionActivity.APPROACH_TOY, CompanionActivity.REST).forEachIndexed { index, activity ->
                    when (activity) {
                        CompanionActivity.APPROACH_TOY -> scene.requestMotionReview(CompanionIntent.PLAY)
                        CompanionActivity.REST -> scene.requestMotionReview(CompanionIntent.REST)
                        else -> Unit
                    }
                    var attempts = 0
                    while (scene.activity != activity && attempts++ < 2400) scene.advanceScene(50)
                    assertTrue("$pet did not reach $activity", scene.activity == activity)
                    if (activity == CompanionActivity.APPROACH_TOY) repeat(30) { scene.advanceScene(50) }
                    if (activity == CompanionActivity.REST) repeat(40) { scene.advanceScene(50) }
                    canvas.save(); canvas.translate(index * 400f, 0f); scene.draw(canvas); canvas.restore()
                    canvas.drawText("${pet.name} / ${listOf("greeting", "approach", "rear bed")[index]}", index * 400f + 10, 322f, label)
                }
                File(directory, "${pet.name.lowercase()}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
                sheet.recycle()
            }
        }
    }

    @Test fun allFifteenCompanionsHaveVisibleHomeArtwork(): Unit {
        val context: Context = ApplicationProvider.getApplicationContext()
        val sheet: Bitmap = Bitmap.createBitmap(1500, 2100, Bitmap.Config.ARGB_8888)
        val sheetCanvas: Canvas = Canvas(sheet)
        sheetCanvas.drawColor(HomeUi.cream)
        val label: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HomeUi.ink; textSize = 22f }
        for ((index, pet) in PetType.entries.withIndex()) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val scene: HomeSceneView = HomeSceneView(context)
                scene.environment = HomeEnvironment.entries[index % 3]
                runBlocking { scene.loadPet(pet) }
                scene.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(380, View.MeasureSpec.EXACTLY))
                scene.layout(0, 0, 500, 380)
                val withPet: Bitmap = Bitmap.createBitmap(500, 380, Bitmap.Config.ARGB_8888)
                scene.draw(Canvas(withPet))
                scene.showPet = false
                val withoutPet: Bitmap = Bitmap.createBitmap(500, 380, Bitmap.Config.ARGB_8888)
                scene.draw(Canvas(withoutPet))
                var different: Int = 0
                for (y: Int in 140 until 330) for (x: Int in 120 until 380) if (withPet.getPixel(x, y) != withoutPet.getPixel(x, y)) different++
                assertTrue("Missing actor for $pet", different > 500)
                val left: Float = (index % 3 * 500).toFloat()
                val top: Float = (index / 3 * 420).toFloat()
                sheetCanvas.drawBitmap(withPet, left, top, null)
                sheetCanvas.drawText(pet.name, left + 16, top + 408, label)
                withPet.recycle(); withoutPet.recycle()
            }
        }
        File(context.getExternalFilesDir(null), "companion-catalog.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        sheet.recycle()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val memories = Bitmap.createBitmap(1200, 304, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(memories)
            ExpeditionDestination.entries.forEachIndexed { index, destination ->
                val illustration = MemoryIllustration(context, "expedition", destination.id)
                illustration.layout(0, 0, 400, 304)
                canvas.save(); canvas.translate(index * 400f, 0f); illustration.draw(canvas); canvas.restore()
            }
            File(context.getExternalFilesDir(null), "companion-encounters.png").outputStream().use {
                memories.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            memories.recycle()
        }
    }
}
