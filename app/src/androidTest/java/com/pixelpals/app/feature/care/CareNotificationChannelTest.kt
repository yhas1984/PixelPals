package com.pixelpals.app.feature.care

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.pixelpals.app.core.care.CareReminderDecision
import com.pixelpals.app.core.care.CareReminderType
import com.pixelpals.app.notifications.PetCareNotificationManager
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareNotificationChannelTest {
    @Test fun disabledCareChannelBlocksReminderAndSurvivesRecreation() {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        PetCareNotificationManager.createChannel(context)
        assumeTrue("Global notifications must be enabled", PetCareNotificationManager.canNotify(context))
        val initialEnabled = PetCareNotificationManager.isCareChannelEnabled(context)
        val device = UiDevice.getInstance(instrumentation)
        val settingsIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, PetCareNotificationManager.CHANNEL_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(settingsIntent)
        try {
            val channelSwitch = requireNotNull(findSwitch(device))
            assertEquals("Settings did not reflect channel state", initialEnabled, channelSwitch.isChecked)
            if (initialEnabled) {
                channelSwitch.click()
                assertTrue("Channel did not become disabled", waitUntil {
                    !PetCareNotificationManager.isCareChannelEnabled(context)
                })
            }
            assertTrue(PetCareNotificationManager.canNotify(context))
            assertFalse(PetCareNotificationManager.canSendCareReminder(context))
            val decision = CareReminderDecision(CareReminderType.SATIETY, CareAction.FEED, false)
            assertFalse(PetCareNotificationManager.show(context, PetType.CORGI, decision))
            PetCareNotificationManager.createChannel(context)
            assertFalse("Recreating a channel must not restore user choice", PetCareNotificationManager.isCareChannelEnabled(context))
        } finally {
            try {
                if (initialEnabled && !PetCareNotificationManager.isCareChannelEnabled(context)) {
                    requireNotNull(findSwitch(device)).click()
                    assertTrue("Original channel state was not restored", waitUntil {
                        PetCareNotificationManager.isCareChannelEnabled(context)
                    })
                }
            } finally { device.pressBack() }
        }
    }

    private fun findSwitch(device: UiDevice): UiObject2? {
        for (selector in listOf(By.res("com.android.settings:id/switch_widget"), By.res("android:id/switch_widget"))) {
            device.wait(Until.findObject(selector), 4_000L)?.let { return it }
        }
        return null
    }

    private fun waitUntil(predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(100L)
        }
        return predicate()
    }
}
