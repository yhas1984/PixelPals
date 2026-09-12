package com.pixelpals.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.data.prefs.SelectedPetStore
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that revoking overlay permission stops the running pet service. */
@RunWith(AndroidJUnit4::class)
class PetOverlayPermissionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun revokingOverlayPermissionStopsServiceAndDisablesPet() {
        assumeTrue("Requires an emulator", isDisposableEmulator())
        assumeTrue("Requires foreground-service support", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        val store = SelectedPetStore(context)
        val previousEnabled = store.isPetEnabled()
        val previousOverlayPermission = Settings.canDrawOverlays(context)

        try {
            setOverlayPermission(context, "allow")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.startForegroundService(
                        Intent(activity, PetService::class.java).setAction(PetService.ACTION_SHOW)
                    )
                }

                assertTrue("service did not become running", waitUntil(8_000L) {
                    PetService.isRunning && store.isPetEnabled()
                })
                SystemClock.sleep(500L)
                assertTrue("service stopped before revocation", PetService.isRunning)
                assertTrue("overlay permission was not granted", Settings.canDrawOverlays(context))
                assertTrue("pet preference was not enabled", store.isPetEnabled())

                setOverlayPermission(context, "ignore")
                assertFalse("overlay permission was not revoked", Settings.canDrawOverlays(context))
                assertTrue("service remained active after permission revocation", waitUntil(8_000L) {
                    !PetService.isRunning && !store.isPetEnabled()
                })
            }
        } finally {
            context.stopService(Intent(context, PetService::class.java))
            setOverlayPermission(context, if (previousOverlayPermission) "allow" else "ignore")
            store.setPetEnabled(previousEnabled)
        }
    }

    private fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(100L)
        }
        return predicate()
    }

    private fun setOverlayPermission(context: Context, mode: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode"
        )
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun isDisposableEmulator(): Boolean =
        Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu"
}
