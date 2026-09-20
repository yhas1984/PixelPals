package com.pixelpals.app.status

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.BuildConfig
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.database.CompanionHomeEntity
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.feature.care.CarePoseLoader
import com.pixelpals.app.feature.care.CareStageView
import com.pixelpals.app.feature.home.HomeEnvironment
import com.pixelpals.app.core.services.AppServices
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetDashboardEnvironmentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun requireCareSceneEmulator(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        assumeTrue(BuildConfig.CARE_SCENES_ENABLED)
        assumeTrue(CarePoseLoader.isAvailable(context.assets, PetType.DIABLILLO))
    }

    @Test
    fun dashboardAppliesDiablilloHomeEnvironmentAcrossRecreation(): Unit {
        val selection = SelectedPetStore(context)
        val companions = AppServices.companions(context)
        val previousSelection: PetType = selection.load()
        val previousHome: CompanionHomeEntity? = runBlocking { companions.dao.getHome("diablillo") }
        selection.save(PetType.DIABLILLO)
        try {
            runBlocking { companions.dao.saveHome(CompanionHomeEntity("diablillo", environment = HomeEnvironment.GARDEN.name)) }
            ActivityScenario.launch(PetDashboardActivity::class.java).use { scenario ->
                awaitEnvironment(scenario, HomeEnvironment.GARDEN)
                runBlocking { companions.dao.saveHome(CompanionHomeEntity("diablillo", environment = HomeEnvironment.NIGHT.name)) }
                scenario.recreate()
                awaitEnvironment(scenario, HomeEnvironment.NIGHT)
            }
        } finally {
            selection.save(previousSelection)
            runBlocking { if (previousHome != null) companions.dao.saveHome(previousHome) }
        }
    }

    private fun awaitEnvironment(scenario: ActivityScenario<PetDashboardActivity>, expected: HomeEnvironment): Unit {
        val deadline: Long = SystemClock.elapsedRealtime() + 10_000L
        var actual: HomeEnvironment? = null
        while (SystemClock.elapsedRealtime() < deadline && actual != expected) {
            scenario.onActivity { activity -> actual = findStage(activity.window.decorView)?.environment }
            if (actual != expected) Thread.sleep(50L)
        }
        assertEquals(expected, actual)
    }

    private fun findStage(view: View): CareStageView? {
        if (view is CareStageView) return view
        if (view is ViewGroup) for (index: Int in 0 until view.childCount) findStage(view.getChildAt(index))?.let { return it }
        return null
    }
}
