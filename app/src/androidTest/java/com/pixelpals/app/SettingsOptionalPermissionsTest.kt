package com.pixelpals.app

import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Permission-state rendering, not an Android 13 system permission dialog test. */
@RunWith(AndroidJUnit4::class)
class SettingsOptionalPermissionsTest {
    @Test fun onlyOverlayGatesLaunchAndOptionalPermissionsAreLabelled(): Unit {
        ActivityScenario.launch(CompanionSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                val fragment: SettingsFragment = activity.supportFragmentManager
                    .findFragmentById(R.id.companionSettings) as SettingsFragment
                val render = SettingsFragment::class.java.getDeclaredMethod("renderPermissionUi",
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                for (overlay: Boolean in listOf(false, true)) {
                    for (notifications: Boolean in listOf(false, true)) {
                        for (usage: Boolean in listOf(false, true)) {
                            render.invoke(fragment, overlay, notifications, usage)
                            val view = fragment.requireView()
                            assertEquals(overlay, view.findViewById<Button>(R.id.btnLaunch).isEnabled)
                            assertEquals(activity.getString(if (notifications) R.string.permission_granted else R.string.permission_optional),
                                view.findViewById<TextView>(R.id.statusNotification).text.toString())
                            assertEquals(activity.getString(if (usage) R.string.permission_granted else R.string.permission_optional),
                                view.findViewById<TextView>(R.id.statusUsage).text.toString())
                            val expected: Int = when {
                                !overlay -> R.string.launch_disabled_reason_overlay
                                !notifications -> R.string.launch_ready_without_notifications
                                else -> R.string.launch_ready
                            }
                            assertEquals(activity.getString(expected), view.findViewById<TextView>(R.id.txtLaunchReason).text.toString())
                        }
                    }
                }
            }
        }
    }
}
