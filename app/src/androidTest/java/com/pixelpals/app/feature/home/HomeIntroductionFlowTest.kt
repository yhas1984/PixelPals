package com.pixelpals.app.feature.home

import android.os.Build
import android.os.SystemClock
import android.widget.TextView
import android.widget.EditText
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
import com.pixelpals.app.core.domain.PetType
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
        CompanionPreferences(context).finishFirstHome()
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
            assertEquals("corgi", CompanionPreferences(context).firstHomePet)
            assertNotNull(UiDevice.getInstance(instrumentation).findObject(By.pkg(context.packageName).clazz("android.widget.EditText")))
            UiDevice.getInstance(instrumentation).pressBack()
        }
    }

    @Test fun cancellingNewUserIntroductionKeepsGuideWithoutReopeningNaming(): Unit {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val initial = awaitDialog(scenario)
            scenario.onActivity { initial.cancel() }
            instrumentation.waitForIdleSync()
            assertTrue(CompanionPreferences(context).hasSeenIntroduction)
            assertEquals("corgi", CompanionPreferences(context).firstHomePet)
            assertNull(UiDevice.getInstance(instrumentation).findObject(By.pkg(context.packageName).clazz("android.widget.EditText")))
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertNull(LivingHomeFragment::class.java.getDeclaredField("introductionDialog").apply { isAccessible = true }
                    .get(activity.supportFragmentManager.findFragmentByTag(PixelPalsDestination.HOME.fragmentTag)))
                val action = activity.findViewById<android.widget.Button>(R.id.firstHomeAction)
                assertEquals(context.getString(R.string.home_name), action.text)
                assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.firstHomeGuide).visibility)
            }
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
            assertNull(CompanionPreferences(context).firstHomePet)
            assertNull(UiDevice.getInstance(instrumentation).findObject(By.pkg(context.packageName).clazz("android.widget.EditText")))
        }
    }

    @Test fun firstHomeGuideResumesTheRightPetAndCanBeSkipped(): Unit {
        val preferences = CompanionPreferences(context)
        preferences.beginFirstHome("corgi")
        instrumentation.runOnMainSync {
            var names: Int = 0
            var cares: Int = 0
            var desktop: Int = 0
            fun createGuide(): FirstHomeGuideView = FirstHomeGuideView(context, { names++ }, { cares++ }, { desktop++ })
            val blank = CompanionHomeEntity("corgi")
            val named = blank.copy(nickname = "Copito", adoptedAt = 1L)
            val guide: FirstHomeGuideView = createGuide()
            guide.render(blank, false)
            guide.findViewById<android.widget.Button>(R.id.firstHomeAction).performClick()
            assertEquals(1, names)
            guide.render(named, false)
            guide.findViewById<android.widget.Button>(R.id.firstHomeAction).performClick()
            assertEquals(1, cares)
            assertFalse("Opening care does not complete the guide", preferences.hasCompletedFirstHomeCare)
            preferences.completeFirstHomeCare("ginger")
            assertFalse(preferences.hasCompletedFirstHomeCare)
            preferences.completeFirstHomeCare("corgi")
            val restored: FirstHomeGuideView = createGuide()
            restored.render(named, false)
            assertTrue(CompanionPreferences(context).hasCompletedFirstHomeCare)
            restored.findViewById<android.widget.Button>(R.id.firstHomeAction).performClick()
            assertEquals(1, desktop)
            assertEquals("Declining permission must leave the invitation available", "corgi", preferences.firstHomePet)
            restored.render(named, true)
            assertEquals(android.view.View.GONE, restored.visibility)
            restored.render(CompanionHomeEntity("ginger"), false)
            assertEquals(android.view.View.GONE, restored.visibility)
            restored.render(named, false)
            restored.findViewById<android.widget.Button>(R.id.firstHomeSkip).performClick()
            assertNull(preferences.firstHomePet)
            assertEquals(android.view.View.GONE, restored.visibility)
        }
    }

    @Test fun firstAffectionAdvancesOnlyAfterCompletionAndSurvivesRecreation(): Unit = runBlocking {
        val preferences = CompanionPreferences(context)
        preferences.hasSeenIntroduction = true
        preferences.beginFirstHome("corgi")
        AppServices.companions(context).dao.saveHome(CompanionHomeEntity("corgi", nickname = "Copito", adoptedAt = 1L))
        val coordinator = AppServices.careScenes(context)
        fun awaitCondition(predicate: () -> Boolean) {
            val deadline: Long = SystemClock.elapsedRealtime() + 10_000L
            while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertTrue("First-home condition was not reached", predicate())
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun clickAffection() {
                awaitCondition {
                    var ready: Boolean = false
                    scenario.onActivity { activity ->
                        val button = activity.findViewById<android.widget.Button>(R.id.firstHomeAction)
                        ready = button?.text == context.getString(R.string.home_guide_pet)
                    }
                    ready
                }
                scenario.onActivity { it.findViewById<android.widget.Button>(R.id.firstHomeAction).performClick() }
            }
            clickAffection()
            awaitCondition { coordinator.session.value?.request?.action == com.pixelpals.app.core.care.scene.CareSceneAction.PET }
            scenario.recreate()
            assertFalse("An interrupted caricia must not advance the guide", preferences.hasCompletedFirstHomeCare)
            awaitCondition { coordinator.session.value == null }
            clickAffection()
            awaitCondition { preferences.hasCompletedFirstHomeCare }
            scenario.recreate()
            awaitCondition {
                var ready: Boolean = false
                scenario.onActivity { activity ->
                    ready = activity.findViewById<android.widget.Button>(R.id.firstHomeAction)?.text == context.getString(R.string.home_desktop)
                }
                ready
            }
            assertEquals("corgi", preferences.firstHomePet)
        }
    }

    @Test fun namingKeepsDraftAndPetAcrossRecreationAndValidatesBeforeSaving(): Unit = runBlocking {
        CompanionPreferences(context).hasSeenIntroduction = true
        val dao = AppServices.companions(context).dao
        dao.saveHome(CompanionHomeEntity("ginger", nickname = "Ginger original"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val home = activity.supportFragmentManager.findFragmentByTag(PixelPalsDestination.HOME.fragmentTag) as LivingHomeFragment
                PetNameDialogFragment.create(PetType.CORGI, "").showNow(home.childFragmentManager, PetNameDialogFragment.TAG)
                val nameDialog = (home.childFragmentManager.findFragmentByTag(PetNameDialogFragment.TAG) as PetNameDialogFragment).requireDialog() as AlertDialog
                val input = requireNotNull(nameDialog.findViewById<EditText>(R.id.petNameDraft))
                input.setText("   ")
                nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertTrue(nameDialog.isShowing)
                assertNotNull(input.error)
                input.setText("  Copito  ")
                input.setSelection(4)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val home = activity.supportFragmentManager.findFragmentByTag(PixelPalsDestination.HOME.fragmentTag) as LivingHomeFragment
                val nameDialog = (home.childFragmentManager.findFragmentByTag(PetNameDialogFragment.TAG) as PetNameDialogFragment).requireDialog() as AlertDialog
                val input = requireNotNull(nameDialog.findViewById<EditText>(R.id.petNameDraft))
                assertEquals("  Copito  ", input.text.toString())
                assertEquals(4, input.selectionStart)
                com.pixelpals.app.data.prefs.SelectedPetStore(context).save(PetType.GINGER)
                nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertFalse(input.isEnabled)
            }
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            while (dao.getHome("corgi")?.nickname != "Copito" && SystemClock.elapsedRealtime() < deadline) kotlinx.coroutines.delay(50)
            assertEquals("Copito", dao.getHome("corgi")?.nickname)
            assertEquals("Ginger original", dao.getHome("ginger")?.nickname)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val home = activity.supportFragmentManager.findFragmentByTag(PixelPalsDestination.HOME.fragmentTag) as LivingHomeFragment
                assertNull(home.childFragmentManager.findFragmentByTag(PetNameDialogFragment.TAG))
            }
        }
    }
}
