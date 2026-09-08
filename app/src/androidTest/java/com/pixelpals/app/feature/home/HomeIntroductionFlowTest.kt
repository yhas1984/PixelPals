package com.pixelpals.app.feature.home

import android.os.Build
import android.os.SystemClock
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.pixelpals.app.MainActivity
import com.pixelpals.app.R
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.database.CompanionHomeEntity
import com.pixelpals.app.navigation.PixelPalsDestination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Changes selection/onboarding fixtures: disposable emulator only. */
@RunWith(AndroidJUnit4::class)
class HomeIntroductionFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @Before fun prepare(): Unit = runBlocking {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        context.getSharedPreferences("pixelpals_selection", 0).edit().clear().commit()
        CompanionPreferences(context).hasSeenIntroduction = false
        AppServices.companions(context).dao.saveHome(CompanionHomeEntity("corgi"))
    }

    private fun awaitDialog(scenario: ActivityScenario<MainActivity>): AlertDialog {
        var dialog: AlertDialog? = null
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (dialog == null && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(PixelPalsDestination.HOME.fragmentTag)
                if (fragment is LivingHomeFragment) dialog = LivingHomeFragment::class.java.getDeclaredField("introductionDialog")
                    .apply { isAccessible = true }.get(fragment) as? AlertDialog
            }
            if (dialog == null) SystemClock.sleep(50)
        }
        return requireNotNull(dialog)
    }

    @Test fun newUserIntroductionSurvivesRecreationUntilAcknowledged() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val initial = awaitDialog(scenario)
            scenario.onActivity {
                assertEquals(context.getString(R.string.home_introduction), initial.findViewById<TextView>(android.R.id.message)?.text)
                assertFalse(CompanionPreferences(context).hasSeenIntroduction)
            }
            scenario.recreate()
            val restored = awaitDialog(scenario)
            scenario.onActivity {
                assertTrue(restored.isShowing)
                assertFalse(CompanionPreferences(context).hasSeenIntroduction)
                restored.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            assertTrue(CompanionPreferences(context).hasSeenIntroduction)
            assertNotNull(UiDevice.getInstance(instrumentation).findObject(By.pkg(context.packageName).clazz("android.widget.EditText")))
            UiDevice.getInstance(instrumentation).pressBack()
        }
    }

    @Test fun legacySelectionWithoutTimestampGetsWelcomeWithoutRenaming() {
        context.getSharedPreferences("pixelpals_selection", 0).edit().putString("selected_pet", "CORGI").putBoolean("pet_enabled", false).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dialog = awaitDialog(scenario)
            scenario.onActivity {
                assertEquals(context.getString(R.string.home_welcome), dialog.findViewById<TextView>(android.R.id.message)?.text)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            assertTrue(CompanionPreferences(context).hasSeenIntroduction)
            assertNull(UiDevice.getInstance(instrumentation).findObject(By.pkg(context.packageName).clazz("android.widget.EditText")))
        }
    }
}
