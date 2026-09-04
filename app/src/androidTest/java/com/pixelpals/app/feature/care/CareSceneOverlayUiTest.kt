package com.pixelpals.app.feature.care

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixelpals.app.R
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Bounded overlay UI smoke; emulator-only because it applies a real care action. */
@RunWith(AndroidJUnit4::class)
class CareSceneOverlayUiTest {
    @Test fun boundedTrayAcceptsCareThenClosesOnOutsideTouch(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context: Context = instrumentation.targetContext
        assumeTrue("Grant overlay permission on disposable emulator before this test", Settings.canDrawOverlays(context))
        val device: UiDevice = UiDevice.getInstance(instrumentation)
        var overlay: CareOverlayController? = null
        var result: CareSceneResult? = null
        instrumentation.runOnMainSync {
            overlay = CareOverlayController(context, context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
                onVisibilityChanged = {}, onResult = { result = it })
            overlay!!.showHint(PetType.CORGI, 150, 300)
        }
        try {
            val care = device.wait(Until.findObject(By.text(context.getString(R.string.care_scene_open))), 5_000L)
            assertNotNull(care)
            care.click()
            val feed = device.wait(Until.findObject(By.text(context.getString(R.string.action_feed))), 5_000L)
            assertNotNull(feed)
            val deadline: Long = SystemClock.elapsedRealtime() + 8_000L
            while (!feed.isEnabled && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50L)
            assertTrue(feed.isEnabled)
            feed.click()
            Thread.sleep(900L)
            val directory: File = requireNotNull(context.getExternalFilesDir("care-ui"))
            device.takeScreenshot(File(directory, "overlay-feed.png"))
            val completionDeadline: Long = SystemClock.elapsedRealtime() + 8_000L
            var complete: Boolean = false
            while (!complete && SystemClock.elapsedRealtime() < completionDeadline) {
                instrumentation.runOnMainSync { complete = result is CareSceneResult.Completed }
                Thread.sleep(50L)
            }
            assertTrue(complete)
            device.click(device.displayWidth - 8, device.displayHeight / 2)
            Thread.sleep(150L)
            instrumentation.runOnMainSync { assertFalse(overlay!!.isShowing) }
        } finally {
            instrumentation.runOnMainSync { overlay?.close() }
        }
    }
}
