package com.pixelpals.app.feature.home

import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixelpals.app.MainActivity
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.database.CompanionExpeditionEntity
import com.pixelpals.app.navigation.PixelPalsDestination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpeditionReadyUiTest {
    @Test fun completionReplacesTravelCopyWithoutGrantingReward(): Unit = runBlocking {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dao = AppServices.companions(context).dao
        val previous = dao.getExpedition()
        val preferences = CompanionPreferences(context)
        val introduction: Boolean = preferences.hasSeenIntroduction
        preferences.hasSeenIntroduction = true
        val destination = ExpeditionDestination.MEADOW
        val now: Long = System.currentTimeMillis()
        val trip = CompanionExpeditionEntity(requestId = "ready-ui-review", petId = "corgi", destination = destination.id,
            startedAt = now, lastWallTime = now, lastUptime = SystemClock.elapsedRealtime(),
            bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0))
        dao.saveExpedition(trip)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.navigate(PixelPalsDestination.ADVENTURES) }
                val device = UiDevice.getInstance(instrumentation)
                val petName = context.getString(PetType.CORGI.displayNameResId)
                val destinationName = context.getString(destination.title)
                val travelling = context.getString(R.string.adventure_travel, petName, destinationName)
                assertTrue(device.wait(Until.hasObject(By.text(travelling)), 10_000))
                assertFalse(device.hasObject(By.text(context.getString(R.string.adventure_return))))
                dao.saveExpedition(trip.copy(elapsedMs = destination.durationMs))
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.adventure_ready, petName, destinationName))), 5_000))
                assertTrue(device.hasObject(By.text(context.getString(R.string.adventure_ready_hint))))
                assertTrue(device.hasObject(By.text(context.getString(R.string.adventure_return))))
                assertFalse(device.hasObject(By.text(travelling)))
                assertFalse(device.hasObject(By.text(context.getString(R.string.adventure_remaining, 0))))
                assertEquals("Displaying readiness must not claim the trip", trip.requestId, dao.getExpedition()?.requestId)
            }
        } finally {
            dao.clearExpedition(trip.requestId)
            if (previous != null) dao.saveExpedition(previous)
            preferences.hasSeenIntroduction = introduction
        }
    }
}
