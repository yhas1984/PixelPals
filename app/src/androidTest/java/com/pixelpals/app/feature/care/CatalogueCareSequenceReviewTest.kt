package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneController
import com.pixelpals.app.core.care.scene.CareSceneMode
import com.pixelpals.app.core.care.scene.CarePoint
import com.pixelpals.app.core.care.scene.CorgiFetchMotion
import com.pixelpals.app.core.care.scene.CorgiFetchPlan
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.CorgiBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real care renderer catalogue review; this test never mutates app state. */
@RunWith(AndroidJUnit4::class)
class CatalogueCareSequenceReviewTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val actions = CareSceneAction.entries.toList()
    private val checkpoints = listOf(0, 20, 40, 60, 80, 100)

    @Test fun renderCatalogueContactsAndGeometryForEverySpeciesAndAction(): Unit = kotlinx.coroutines.runBlocking {
        renderCatalogue(exportSequences = InstrumentationRegistry.getArguments().getString("reviewFrames") == "true")
    }

    private suspend fun renderCatalogue(exportSequences: Boolean) {
        val root = File(context.cacheDir, "catalogue-final-review").apply { mkdirs() }
        val csv = File(root, "geometry.csv")
        csv.writeText("pet,action,reduced,progress,requestedClipFrame,sourceGroundX,sourceGroundY,bodyLeft,bodyTop,bodyRight,bodyBottom,contact\n")
        val tile = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val body = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val bodyFilter = PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN)
        val speciesRenderer = SpeciesCareRenderer()
        val corgiRenderer = CorgiDesktopCareRenderer()
        lateinit var corgiBridge: TestPetBridge
        lateinit var corgiNative: CorgiBehavior
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            corgiBridge = TestPetBridge(context, PetType.CORGI, 160,
                com.pixelpals.app.core.motion.PetArtworkScale.forPet(PetType.CORGI))
            corgiNative = CorgiBehavior(corgiBridge, SeededPetRandom(17))
        }
        val loadingField = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        repeat(100) { if (loadingField.getBoolean(corgiNative)) kotlinx.coroutines.delay(20L) }
        assertTrue("Native Corgi must load before reviewing fetch", !loadingField.getBoolean(corgiNative))
        corgiBridge.currentFrame = 0
        val corgiCareBaselineOffsetY: Float = corgiNative.careBaselineOffsetY ?: 160f * .46f
        val label = android.graphics.Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 14f }
        try {
            val requested = InstrumentationRegistry.getArguments().getString("reviewPets")?.split(',')
            for (pet in PetType.entries.filter { requested == null || it.name.lowercase() in requested }) {
                val pack = CarePoseLoader.load(context.assets, pet)
                try {
                    val contactSheets = listOf(false, true).associateWith {
                        Bitmap.createBitmap(320 * 6, 320 * 6, Bitmap.Config.ARGB_8888).also { sheet ->
                            sheet.eraseColor(Color.rgb(242, 237, 247))
                        }
                    }
                    try {
                        for (reduced in listOf(false, true)) for ((actionIndex, action) in actions.withIndex()) {
                            for ((stepIndex, percent) in checkpoints.withIndex()) {
                                val fetchPlan = fetchPlan(pet, action, reduced)
                                val timing = fetchPlan?.timing ?: pack.spec.timings.getValue(action)
                                val duration = timing.durationMs
                                val elapsed = duration * percent / 100L
                                val scene = CareSceneController(action, CareSceneMode.AUTOMATIC, timing)
                                if (elapsed > 0L) scene.advance(elapsed)
                                render(pet, pack, scene, reduced, bodyFilter, body, speciesRenderer, corgiRenderer, fetchPlan, corgiNative, corgiBridge, corgiCareBaselineOffsetY)
                                val bounds = bodyBounds(body)
                                assertTrue("$pet $action reduced=$reduced $percent body visible", bounds != null)
                                val b = requireNotNull(bounds)
                                assertTrue("$pet $action reduced=$reduced $percent left clipping", b.left > 0)
                                assertTrue("$pet $action reduced=$reduced $percent right clipping", b.right < 320)
                                assertTrue("$pet $action reduced=$reduced $percent top clipping", b.top > 0)
                                assertTrue("$pet $action reduced=$reduced $percent bottom clipping", b.bottom < 320)
                                assertTrue("$pet $action reduced=$reduced $percent camera", b.centerX() in 40..280)
                                val requestedClipFrame = pack.spec.getFrame(action, elapsed)
                                val sourceGround = pack.spec.anchors[requestedClipFrame].ground
                                csv.appendText("${pet.name.lowercase()},${action.name},$reduced,$percent,$requestedClipFrame,${sourceGround.x},${sourceGround.y},${b.left},${b.top},${b.right},${b.bottom},${scene.hasContact}\n")
                                render(pet, pack, scene, reduced, null, tile, speciesRenderer, corgiRenderer, fetchPlan, corgiNative, corgiBridge, corgiCareBaselineOffsetY)
                                val sheet = requireNotNull(contactSheets[reduced])
                                val x = stepIndex * 320f; val y = actionIndex * 320f
                                Canvas(sheet).apply {
                                    drawBitmap(tile, x, y, null)
                                    drawText("${action.name} ${percent}% ${if (reduced) "REDUCED" else "NORMAL"}", x + 8f, y + 18f, label)
                                }
                            }
                        }
                        File(root, pet.name.lowercase()).mkdirs()
                        for (reduced in listOf(false, true)) {
                            val suffix = if (reduced) "reduced" else "normal"
                            File(root, pet.name.lowercase() + "/contacts-$suffix.png").outputStream().use {
                                requireNotNull(contactSheets[reduced]).compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                        }
                        if (exportSequences) exportSequences(pet, pack, root, speciesRenderer, corgiRenderer, corgiNative, corgiBridge, corgiCareBaselineOffsetY)
                    } finally { contactSheets.values.forEach(Bitmap::recycle) }
                } finally { pack.bitmap.recycle() }
            }
        } finally { corgiNative.destroy(); tile.recycle(); body.recycle() }
    }

    private fun exportSequences(pet: PetType, pack: CarePosePack, root: File,
                                speciesRenderer: SpeciesCareRenderer, corgiRenderer: CorgiDesktopCareRenderer,
                                corgiNative: CorgiBehavior, corgiBridge: TestPetBridge,
                                corgiCareBaselineOffsetY: Float) {
        val tile = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        try {
            for (reduced in listOf(false, true)) for (action in actions) {
                val fetchPlan = fetchPlan(pet, action, reduced)
                val timing = fetchPlan?.timing ?: pack.spec.timings.getValue(action)
                val duration = timing.durationMs
                val chunkColumns = 8
                var sheet: Bitmap? = null
                var chunk = 0
                var column = 0
                var elapsed = 0L
                fun flush() {
                    val output = sheet ?: return
                    val suffix = if (reduced) "reduced" else "normal"
                    File(root, "${pet.name.lowercase()}/${action.name.lowercase()}-$suffix-${chunk.toString().padStart(3, '0')}.png").outputStream().use {
                        output.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    output.recycle(); sheet = null; chunk++; column = 0
                }
                while (elapsed <= duration) {
                    if (sheet == null) sheet = Bitmap.createBitmap(320 * chunkColumns, 320, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.rgb(242, 237, 247))
                    }
                    val output = requireNotNull(sheet)
                        val scene = CareSceneController(action, CareSceneMode.AUTOMATIC, timing)
                        if (elapsed > 0L) scene.advance(elapsed)
                        render(pet, pack, scene, reduced, null, tile, speciesRenderer, corgiRenderer, fetchPlan, corgiNative, corgiBridge, corgiCareBaselineOffsetY)
                        Canvas(output).drawBitmap(tile, column * 320f, 0f, null)
                        elapsed += 100L; column++
                    if (column == chunkColumns) flush()
                }
                flush()
            }
        } finally { tile.recycle() }
    }

    private fun fetchPlan(pet: PetType, action: CareSceneAction, reduced: Boolean): CorgiFetchPlan? =
        if (pet == PetType.CORGI && action == CareSceneAction.PLAY) {
            CorgiFetchMotion.createPlan(CarePoint(400f, 200f), PetBounds(350, 550, 100, 200), 160, false, reduced)
        } else null

    private fun render(pet: PetType, pack: CarePosePack, scene: CareSceneController, reduced: Boolean,
                       filter: PorterDuffColorFilter?, target: Bitmap,
                       speciesRenderer: SpeciesCareRenderer, corgiRenderer: CorgiDesktopCareRenderer,
                       fetchPlan: CorgiFetchPlan? = null, corgiNative: CorgiBehavior? = null,
                       corgiBridge: TestPetBridge? = null,
                       corgiCareBaselineOffsetY: Float = 160f * .46f) {
        target.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(target)
        if (pet == PetType.CORGI) {
            if (fetchPlan != null && reduced && scene.animationMs == 0L && corgiBridge != null) {
                // Reduced fetch starts directly in the care pose; match PetView's
                // frame-0 entry instead of inheriting the preceding action's frame.
                corgiBridge.currentFrame = 0
            }
            val fetchPose = fetchPlan?.let { plan -> CorgiFetchMotion.getPose(plan, scene.animationMs) }
            val fetchFrame = fetchPose?.let { pose ->
                CorgiFetchFrame.fromPose(requireNotNull(fetchPlan), pose, pack.spec.anchors[pose.careFrame])
            }
            if (fetchPose?.regularFrame != null && corgiNative != null && corgiBridge != null) {
                val plan = requireNotNull(fetchPlan)
                corgiBridge.currentFrame = fetchPose.regularFrame
                corgiBridge.animScaleX = if (plan.direction < 0f) -1f else 1f
                corgiBridge.animColorFilter = filter
                corgiNative.onDraw(canvas, 160f, 160f)
                corgiBridge.animColorFilter = null
            } else corgiRenderer.draw(canvas, pack, 160, scene.animationMs,
                false, reduced, scene.action, fetchFrame = fetchPose?.careFrame ?: 2,
                baselineOffsetY = corgiCareBaselineOffsetY,
                colorFilter = filter)
            if (fetchFrame != null) {
                val alphaLayer = canvas.saveLayerAlpha(
                    0f, 0f, target.width.toFloat(), target.height.toFloat(),
                    (fetchFrame.alpha * 255f).toInt().coerceIn(0, 255),
                )
                canvas.save()
                canvas.rotate(fetchFrame.rotation, fetchFrame.ball.x - fetchFrame.pet.x + 80f,
                    fetchFrame.ball.y - fetchFrame.pet.y + 80f)
                CarePropPainter().draw(canvas, CareSceneAction.PLAY,
                    fetchFrame.ball.x - fetchFrame.pet.x + 80f,
                    fetchFrame.ball.y - fetchFrame.pet.y + 80f,
                    160f * .30f * .86f)
                canvas.restore()
                canvas.restoreToCount(alphaLayer)
            }
        } else speciesRenderer.draw(canvas, pack, scene, reduced, false, desktopSize = 160, colorFilter = filter)
    }

    private fun bodyBounds(bitmap: Bitmap): Rect? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1
        pixels.forEachIndexed { index, color ->
            if (Color.alpha(color) > 0 && Color.red(color) > 180 && Color.blue(color) > 180 && Color.green(color) < 120) {
                val x = index % bitmap.width; val y = index / bitmap.width
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        return if (right >= left && bottom >= top) Rect(left, top, right, bottom) else null
    }
}
