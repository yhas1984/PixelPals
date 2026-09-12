package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.rest.PetRestSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class CorgiSleepContinuityTest {
    @Test fun restAndWakeUseTheSamePlantedPosturesBeforeLocomotion(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val art = HomeLocomotion.load(context, PetType.CORGI)
        val sprites = HomeLocomotion::class.java.getDeclaredField("sprites").apply { isAccessible = true }.get(art) as HomeSpriteFrames
        val rest = requireNotNull(HomeRestArtwork.load(context, PetType.CORGI))
        val motion = CompanionMotion()
        val target = RectF(40f, 40f, 280f, 280f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val actual = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val expected = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val sheet = Bitmap.createBitmap(320 * 6, 320 * 2, Bitmap.Config.ARGB_8888)
        sheet.eraseColor(0xfff5ebd9.toInt())
        try {
            fun clock(activity: CompanionActivity, seconds: Float) {
                CompanionMotion::class.java.getDeclaredField("activity").apply { isAccessible = true }.set(motion, activity)
                CompanionMotion::class.java.getDeclaredField("elapsed").apply { isAccessible = true }.setFloat(motion, seconds)
            }
            fun compare(activity: CompanionActivity, seconds: Float, frame: Int, column: Int, row: Int) {
                clock(activity, seconds)
                actual.eraseColor(0); expected.eraseColor(0)
                art.draw(Canvas(actual), paint, target, motion, false)
                sprites.draw(Canvas(expected), paint, target, frame)
                assertTrue("$activity at $seconds uses posture $frame", actual.sameAs(expected))
                assertTrue((0 until 320).any { actual.getPixel(it, 220) ushr 24 > 0 })
                Canvas(sheet).drawBitmap(actual, column * 320f, row * 320f, null)
            }
            listOf(0f to 0, .18f to 15, .30f to 16, .46f to 17).forEachIndexed { column, (seconds, frame) ->
                compare(CompanionActivity.REST, seconds, frame, column, 0)
            }
            // No standing prefix when the desktop actor is already seated.
            clock(CompanionActivity.REST, .02f)
            actual.eraseColor(0); expected.eraseColor(0)
            art.draw(Canvas(actual), paint, target, motion, false, restStartsSeated = true)
            rest.draw(Canvas(expected), paint, target, .02f, false, false)
            assertTrue(actual.sameAs(expected))
            Canvas(sheet).drawBitmap(actual, 4 * 320f, 0f, null)
            // All four care poses must be drawn while unfolding, including care16.
            for (seconds in listOf(0f, .2f, .4f, .6f)) {
                clock(CompanionActivity.WAKE, seconds)
                actual.eraseColor(0); expected.eraseColor(0)
                art.draw(Canvas(actual), paint, target, motion, false)
                rest.draw(Canvas(expected), paint, target, seconds / .68f * 1.6f, true, false)
                assertTrue("Unfold at $seconds", actual.sameAs(expected))
            }
            listOf(.70f to 17, .84f to 16, 1.02f to 15, 1.15f to 0).forEachIndexed { column, (seconds, frame) ->
                compare(CompanionActivity.WAKE, seconds, frame, column, 1)
            }
            clock(CompanionActivity.WAKE, .1f)
            actual.eraseColor(0); expected.eraseColor(0)
            art.draw(Canvas(actual), paint, target, motion, true)
            sprites.draw(Canvas(expected), paint, target, 0)
            assertTrue("Reduced wake uses the upright endpoint", actual.sameAs(expected))
            File(context.cacheDir, "corgi-home-rest-transition.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { actual.recycle(); expected.recycle(); sheet.recycle() }
    }

    @Test fun scheduledSleepLatchesTheEntryPostureAndDiscardsInterruptedClocks(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = CompanionPreferences(context)
        val previous = preferences.restSchedule
        val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduled = ScheduledPetSleep(context, PetType.CORGI, scope)
        fun field(name: String) = ScheduledPetSleep::class.java.getDeclaredField(name).apply { isAccessible = true }
        try {
            preferences.restSchedule = PetRestSchedule(true, false, (now + 1430) % 1440, (now + 10) % 1440)
            field("art").set(scheduled, HomeLocomotion.load(context, PetType.CORGI))
            field("bedLoaded").setBoolean(scheduled, true)
            assertTrue(scheduled.update(.02f, true, true))
            assertTrue(field("restStartsSeated").getBoolean(scheduled))
            assertTrue(scheduled.update(.02f, true, false))
            assertTrue("An ongoing rest keeps its original entry posture", field("restStartsSeated").getBoolean(scheduled))
            val enabled = preferences.restSchedule
            preferences.restSchedule = PetRestSchedule(false, false)
            assertTrue(scheduled.update(.02f, true))
            preferences.restSchedule = enabled
            repeat(13) { scheduled.update(.1f, true, true) }
            assertFalse("After finishing wake, re-enabled rest starts upright", field("restStartsSeated").getBoolean(scheduled))
            scheduled.wakeForInteraction()
            assertFalse(scheduled.active)
            val motion = field("motion").get(scheduled) as CompanionMotion
            assertEquals(CompanionActivity.OBSERVE, motion.activity)
            assertEquals(0f, motion.elapsed, 0f)
            assertFalse(scheduled.update(.02f, true, false))
            field("awakeUntil").setLong(scheduled, 0L)
            assertTrue(scheduled.update(.02f, true, false))
            assertFalse("A new standing entry must sit again", field("restStartsSeated").getBoolean(scheduled))
            preferences.restSchedule = PetRestSchedule(false, false)
            assertTrue(scheduled.update(.02f, true))
            assertEquals(CompanionActivity.WAKE, motion.activity)
            repeat(13) { scheduled.update(.1f, true) }
            assertFalse(scheduled.active)
            assertEquals(CompanionActivity.OBSERVE, motion.activity)
        } finally { preferences.restSchedule = previous; scope.cancel() }
    }
}
