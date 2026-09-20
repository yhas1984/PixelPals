package com.pixelpals.app.feature.home

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixelpals.app.MainActivity
import com.pixelpals.app.R
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.database.CompanionExpeditionEntity
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.RootNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises persisted app state and a real cancel button on a disposable emulator only. */
@RunWith(AndroidJUnit4::class)
class UnknownExpeditionUiTest {
    @Test fun unrecognisedTripShowsAnExplanationAndACancelButton(): Unit = runBlocking {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dao = AppServices.companions(context).dao
        val previous = dao.getExpedition()
        val preferences = CompanionPreferences(context)
        val introduction: Boolean = preferences.hasSeenIntroduction
        preferences.hasSeenIntroduction = true
        val request: String = "unknown-expedition-ui"
        dao.saveExpedition(CompanionExpeditionEntity(requestId = request, petId = "corgi", destination = "unknown_destination",
            startedAt = System.currentTimeMillis(), lastWallTime = System.currentTimeMillis(), lastUptime = 0L, bootCount = 0))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { (it as RootNavigator).navigate(PixelPalsDestination.ADVENTURES) }
                val device: UiDevice = UiDevice.getInstance(instrumentation)
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.adventure_unknown))), 10_000L))
                val cancel = device.wait(Until.findObject(By.text(context.getString(R.string.adventure_cancel))), 5_000L)
                assertNotNull("An unknown journey must still offer cancellation", cancel)
                cancel.click()
                repeat(100) { if (dao.getExpedition() != null) delay(50) }
                assertNull("The real cancel action must release the travel state", dao.getExpedition())
                assertTrue(device.wait(Until.gone(By.text(context.getString(R.string.adventure_unknown))), 5_000L))
            }
        } finally {
            dao.clearExpedition(request)
            if (previous != null) dao.saveExpedition(previous)
            preferences.hasSeenIntroduction = introduction
        }
    }
}
