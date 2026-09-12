package com.pixelpals.app.feature.home

import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.MainActivity
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.TreasureItem
import com.pixelpals.app.navigation.PixelPalsDestination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.hamcrest.Matchers.allOf

/** Fixture mutation is restricted to the disposable emulator. */
class HomeTreasureSelectionTest {
    @Test fun selectedKeepsakeSurvivesRecreationAndCanBeHidden() = runBlocking {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        db.clearAllTables()
        SelectedPetStore(context).save(PetType.CORGI)
        CompanionPreferences(context).apply { hasSeenIntroduction = true; finishFirstHome() }
        AppServices.companions(context).adopt(PetType.CORGI, "Corgi")
        db.treasureDao().insertTreasure(TreasureItem("🦴", 0, 100L, 100L, 1))
        db.treasureDao().insertTreasure(TreasureItem("🍀", 1, 100L, 100L, 1))
        ActivityScenario.launch<MainActivity>(MainActivity.createIntent(context, PixelPalsDestination.HOME)).use { scenario ->
            awaitTreasure(scenario, "🦴")
            showPicker(scenario)
            onView(withText("🍀 " + context.getString(R.string.treasure_name_companion_clover)))
                .inRoot(allOf(isDialog(), withDecorView(isDisplayed()))).perform(click())
            awaitTreasure(scenario, "🍀")
            scenario.recreate()
            awaitTreasure(scenario, "🍀")
            showPicker(scenario)
            onView(withText(R.string.home_exhibit_none))
                .inRoot(allOf(isDialog(), withDecorView(isDisplayed()))).perform(click())
            awaitTreasure(scenario, null)
            assertEquals("", db.companionDao().getHome("corgi")?.selectedTreasureId)
            assertEquals(1, db.treasureDao().getTreasure("🍀")?.count)
        }
    }

    private fun showPicker(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag(PixelPalsDestination.HOME.fragmentTag)
            LivingHomeFragment::class.java.getDeclaredMethod("showTreasures").apply { isAccessible = true }.invoke(fragment)
        }
    }

    private fun awaitTreasure(scenario: ActivityScenario<MainActivity>, expected: String?) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matched = false
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(PixelPalsDestination.HOME.fragmentTag)
                if (fragment is LivingHomeFragment) {
                    val scene = LivingHomeFragment::class.java.getDeclaredField("scene").apply { isAccessible = true }.get(fragment) as? HomeSceneView
                    matched = scene != null && scene.treasure == expected
                }
            }
            if (matched) return
            SystemClock.sleep(50)
        }
        error("Home did not display $expected")
    }
}
