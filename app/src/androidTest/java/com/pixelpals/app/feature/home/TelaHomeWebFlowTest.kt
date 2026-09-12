package com.pixelpals.app.feature.home

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.MainActivity
import com.pixelpals.app.R
import com.pixelpals.app.core.care.scene.CareScenePhase
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.navigation.PixelPalsDestination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Selection fixtures are restricted to the disposable emulator. */
class TelaHomeWebFlowTest {
    @Test fun accessibleButtonStartsHuntAndPausingCancelsWithoutFood() {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        context.getSharedPreferences("pixelpals_selection",0).edit().putString("selected_pet","TELA").putBoolean("pet_enabled",false).commit()
        CompanionPreferences(context).hasSeenIntroduction=true
        CompanionPreferences(context).finishFirstHome()
        val coordinator=AppServices.careScenes(context)
        val preferences=CompanionPreferences(context)
        val originalReducedMotion=preferences.reducedMotion
        // ActivityScenario requires an idle queue. Motion is covered separately by the render test.
        preferences.reducedMotion=true
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var scene: HomeSceneView?=null
            val end=SystemClock.elapsedRealtime()+5000
            while(scene?.pet != PetType.TELA && SystemClock.elapsedRealtime()<end) {
                scenario.onActivity { scene=it.findViewById(R.id.companionScene) }
                SystemClock.sleep(40)
            }
            scenario.onActivity { activity ->
                val fragment=activity.supportFragmentManager.findFragmentByTag(PixelPalsDestination.HOME.fragmentTag) as LivingHomeFragment
                val button=LivingHomeFragment::class.java.getDeclaredField("webButton").apply { isAccessible=true }.get(fragment) as Button
                assertEquals(View.VISIBLE,button.visibility)
                assertEquals(context.getString(R.string.home_tela_feed),button.text)
                assertTrue(button.performClick())
                assertFalse(button.isEnabled)
            }
            val readyUntil=SystemClock.elapsedRealtime()+5000
            while(coordinator.session.value?.phase != CareScenePhase.READY && SystemClock.elapsedRealtime()<readyUntil) SystemClock.sleep(40)
            assertEquals(PetType.TELA,coordinator.session.value?.request?.pet)
            // ActivityScenario may wait for idle before changing state. Freeze presentation time
            // first so this tests cancellation before the meal marker, not a completed meal.
            instrumentation.runOnMainSync { scene?.pause() }
            assertEquals(CareScenePhase.READY, coordinator.session.value?.phase)
            val before=runBlocking { AppServices.repository(context).getStatusSnapshot(PetType.TELA) }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            val cancelledUntil=SystemClock.elapsedRealtime()+5000
            while(coordinator.session.value != null && SystemClock.elapsedRealtime()<cancelledUntil) SystemClock.sleep(40)
            assertNull(coordinator.session.value)
            val after=runBlocking { AppServices.repository(context).getStatusSnapshot(PetType.TELA) }
            assertEquals(before.bond,after.bond)
            assertEquals(before.softCurrency,after.softCurrency)
            assertEquals(before.hunger,after.hunger)
            assertEquals(before.lastInteractionAt,after.lastInteractionAt)
        } } finally { preferences.reducedMotion=originalReducedMotion }
    }
}
