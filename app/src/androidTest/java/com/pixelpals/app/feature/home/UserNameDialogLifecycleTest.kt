package com.pixelpals.app.feature.home

import android.os.Build
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.CompanionSettingsActivity
import com.pixelpals.app.R
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.database.CompanionHomeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Changes local profile and companion fixtures: disposable emulator only. */
@RunWith(AndroidJUnit4::class)
class UserNameDialogLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun prepare(): Unit = runBlocking {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        CompanionPreferences(context).userName = ""
        val dao = AppServices.companions(context).dao
        dao.saveHome(CompanionHomeEntity("corgi", nickname = "Corgi original"))
        dao.saveHome(CompanionHomeEntity("ginger", nickname = "Ginger original"))
    }

    private fun showDialog(scenario: ActivityScenario<CompanionSettingsActivity>): AlertDialog {
        scenario.onActivity { activity ->
            UserNameDialogFragment.create().showNow(activity.supportFragmentManager, UserNameDialogFragment.TAG)
        }
        var dialog: AlertDialog? = null
        scenario.onActivity { activity ->
            dialog = (activity.supportFragmentManager.findFragmentByTag(UserNameDialogFragment.TAG)
                as UserNameDialogFragment).requireDialog() as AlertDialog
        }
        return requireNotNull(dialog)
    }

    @Test
    fun savingNormalizedProfileDoesNotRenameAnyPet(): Unit = runBlocking {
        val dao = AppServices.companions(context).dao
        ActivityScenario.launch(CompanionSettingsActivity::class.java).use { scenario ->
            val dialog = showDialog(scenario)
            scenario.onActivity {
                requireNotNull(dialog.findViewById<EditText>(R.id.userNameDraft)).setText("  Ada  ")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            assertEquals("Ada", CompanionPreferences(context).userName)
            assertEquals("Corgi original", dao.getHome("corgi")?.nickname)
            assertEquals("Ginger original", dao.getHome("ginger")?.nickname)
        }
    }

    @Test
    fun blankProfileIsRejectedAndDraftCursorSurviveRecreation(): Unit {
        ActivityScenario.launch(CompanionSettingsActivity::class.java).use { scenario ->
            val dialog = showDialog(scenario)
            scenario.onActivity {
                val input = requireNotNull(dialog.findViewById<EditText>(R.id.userNameDraft))
                input.setText("   ")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertTrue(dialog.isShowing)
                assertNotNull(input.error)
                input.setText("  Ada  ")
                input.setSelection(3)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = (activity.supportFragmentManager.findFragmentByTag(UserNameDialogFragment.TAG)
                    as UserNameDialogFragment).requireDialog() as AlertDialog
                val input = requireNotNull(restored.findViewById<EditText>(R.id.userNameDraft))
                assertEquals("  Ada  ", input.text.toString())
                assertEquals(3, input.selectionStart)
                restored.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            assertEquals("", CompanionPreferences(context).userName)
        }
    }

    @Test
    fun optionalSkipLeavesBlankProfileAndRemoveClearsExistingProfile(): Unit {
        ActivityScenario.launch(CompanionSettingsActivity::class.java).use { scenario ->
            val skipped = showDialog(scenario)
            scenario.onActivity { skipped.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
            assertEquals("", CompanionPreferences(context).userName)

            CompanionPreferences(context).userName = "Lola"
            val existing = showDialog(scenario)
            scenario.onActivity {
                assertNotNull(existing.getButton(AlertDialog.BUTTON_NEUTRAL))
                existing.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            }
            assertEquals("", CompanionPreferences(context).userName)
        }
    }
}
