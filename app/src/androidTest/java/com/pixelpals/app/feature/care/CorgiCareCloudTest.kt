package com.pixelpals.app.feature.care

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.getAvailableDesktopCareActions
import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetStatusSnapshot
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Off-screen cloud checks; no overlay permission or pet progress changes. */
@RunWith(AndroidJUnit4::class)
class CorgiCareCloudTest {
    private val snapshot: PetStatusSnapshot = PetStatusSnapshot("corgi", 90, 50, 50, 50, 30,
        PetMood.HAPPY, 1, 10, CareAction.FEED, 1)
    @Test fun sixAnimatedIllustrationsHaveNoVisibleNamesButRemainAccessible(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (language: String in listOf("es", "en")) {
                val base: Context = instrumentation.targetContext
                val configuration: Configuration = Configuration(base.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                }
                val context: Context = base.createConfigurationContext(configuration)
                val selected: MutableList<CareSceneAction> = mutableListOf()
                val cloud: CorgiCareCloudView = CorgiCareCloudView(context, PetType.CORGI,
                    getAvailableDesktopCareActions(snapshot.copy(condition = PetCondition.SICK), Long.MAX_VALUE), selected::add, {})
                val density: Float = context.resources.displayMetrics.density
                val width: Int = (152 * density).toInt()
                val height: Int = (136 * density).toInt()
                cloud.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                cloud.layout(0, 0, width, height)
                cloud.pointTo(width / 2f, fromTop = false)
                assertEquals(6, cloud.childCount)
                val descriptions: List<String> = if (language == "es") listOf("Alimentar", "Jugar", "Acariciar", "Asear", "Descansar", "Dar medicina")
                    else listOf("Feed", "Play", "Pet", "Clean", "Rest", "Medicine")
                repeat(6) { index ->
                    val control: View = cloud.getChildAt(index)
                    assertFalse(control is TextView)
                    assertEquals(descriptions[index], control.contentDescription.toString())
                    assertEquals((48 * density).toInt(), control.width)
                    assertEquals((48 * density).toInt(), control.height)
                    assertEquals(((4 + index % 3 * 48) * density).toInt(), control.left)
                    assertEquals(((10 + index / 3 * 48) * density).toInt(), control.top)
                    assertTrue(control.performClick())
                }
                assertEquals(CareSceneAction.entries, selected)
                val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                cloud.draw(Canvas(bitmap))
                assertEquals(0, Color.alpha(bitmap.getPixel(0, height - 1)))
                assertEquals(255, Color.alpha(bitmap.getPixel(width / 2, height / 2)))
                val directory: File = requireNotNull(context.getExternalFilesDir("care-review"))
                File(directory, "corgi-care-cloud-$language.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                cloud.pointTo(width / 2f, fromTop = true)
                cloud.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                cloud.layout(0, 0, width, height)
                repeat(6) { index ->
                    val control: View = cloud.getChildAt(index)
                    assertEquals(((30 + index / 3 * 48) * density).toInt(), control.top)
                    assertTrue(control.bottom <= height)
                    assertTrue(control.right <= width)
                }
            }
        }
    }

    @Test fun medicineIsAbsentWhenUnneededAndRowsReflowWhenItsAvailabilityChanges(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context: Context = instrumentation.targetContext
            val selected: MutableList<CareSceneAction> = mutableListOf()
            val cloud: CorgiCareCloudView = CorgiCareCloudView(context, PetType.CORGI,
                getAvailableDesktopCareActions(snapshot, 100L), selected::add, {})
            val density: Float = context.resources.displayMetrics.density
            val width: Int = (152 * density).toInt()
            val height: Int = (136 * density).toInt()
            val medicineLabel: String = context.getString(com.pixelpals.app.R.string.action_medicine)
            val statuses: List<PetStatusSnapshot?> = listOf(null, snapshot,
                snapshot.copy(condition = PetCondition.AT_RISK),
                snapshot.copy(condition = PetCondition.HIBERNATING),
                snapshot.copy(condition = PetCondition.SICK, medicineAvailableAt = 101L),
                snapshot.copy(condition = PetCondition.SICK, medicineAvailableAt = 100L),
                snapshot.copy(condition = PetCondition.RECOVERING, medicineAvailableAt = 0L), snapshot)
            for (status: PetStatusSnapshot? in statuses) {
                val actions: List<CareSceneAction> = getAvailableDesktopCareActions(status, 100L)
                cloud.updateActions(actions)
                cloud.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                cloud.layout(0, 0, width, height)
                assertEquals(actions.size, cloud.childCount)
                val descriptions: List<String> = (0 until cloud.childCount).map { cloud.getChildAt(it).contentDescription.toString() }
                assertEquals(CareSceneAction.MEDICINE in actions, medicineLabel in descriptions)
                if (actions.size == 5) {
                    assertEquals((28 * density).toInt(), cloud.getChildAt(3).left)
                    assertEquals((76 * density).toInt(), cloud.getChildAt(4).left)
                }
                selected.clear()
                repeat(cloud.childCount) { cloud.getChildAt(it).performClick() }
                assertEquals(actions, selected)
            }
            val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            cloud.pointTo(width / 2f, fromTop = false)
            cloud.draw(Canvas(bitmap))
            val directory: File = requireNotNull(context.getExternalFilesDir("care-review"))
            File(directory, "corgi-care-cloud-healthy.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test fun everyPetCloudUsesItsSpeciesProps(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context: Context = instrumentation.targetContext
            val density: Float = context.resources.displayMetrics.density
            val width: Int = (152 * density).toInt()
            val height: Int = (136 * density).toInt()
            val actions: List<CareSceneAction> = getAvailableDesktopCareActions(snapshot, 100L)
            val bitmaps: List<Bitmap> = PetType.entries.map { pet: PetType ->
                val cloud: CorgiCareCloudView = CorgiCareCloudView(context, pet, actions, {}, {})
                cloud.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                cloud.layout(0, 0, width, height)
                assertEquals("$pet actions", actions.size, cloud.childCount)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap: Bitmap ->
                    cloud.draw(Canvas(bitmap))
                    val pixels: IntArray = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    assertTrue("$pet cloud visible", pixels.count { Color.alpha(it) > 128 } > 2_000)
                }
            }
            val corgiIndex: Int = PetType.entries.indexOf(PetType.CORGI)
            val corgiPixels: IntArray = IntArray(width * height)
            bitmaps[corgiIndex].getPixels(corgiPixels, 0, width, 0, 0, width, height)
            for (index: Int in bitmaps.indices.filter { it != corgiIndex }) {
                val speciesPixels: IntArray = IntArray(width * height)
                bitmaps[index].getPixels(speciesPixels, 0, width, 0, 0, width, height)
                assertTrue(PetType.entries[index].name, corgiPixels.indices.count { corgiPixels[it] != speciesPixels[it] } > 500)
            }
            val sheet: Bitmap = Bitmap.createBitmap(width * 5, height * 3, Bitmap.Config.ARGB_8888)
            val sheetCanvas: Canvas = Canvas(sheet)
            bitmaps.forEachIndexed { index: Int, bitmap: Bitmap ->
                sheetCanvas.drawBitmap(bitmap, (index % 5 * width).toFloat(), (index / 5 * height).toFloat(), null)
            }
            val directory: File = requireNotNull(context.getExternalFilesDir("care-review"))
            File(directory, "desktop-care-cloud-all-pets.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            sheet.recycle()
            bitmaps.forEach(Bitmap::recycle)
        }
    }
}
