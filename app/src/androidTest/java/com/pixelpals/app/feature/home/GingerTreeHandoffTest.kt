package com.pixelpals.app.feature.home

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GingerTreeHandoffTest {
    @Test fun treeVisitReturnsToMotionWithTheSameFacingAndOrigin() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = CompanionPreferences(context)
        val previousReducedMotion: Boolean = preferences.reducedMotion
        lateinit var scene: HomeSceneView
        try {
            preferences.reducedMotion = false
            instrumentation.runOnMainSync {
                scene = HomeSceneView(context).apply { reviewSeed = 7 }
                runBlocking { scene.loadPet(PetType.GINGER) }
                scene.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
                scene.layout(0, 0, 1000, 760)
                val motion = privateField(scene, "motion") as CompanionMotion
                assertEquals(500f, motion.x, .01f)
                assertEquals(650f, motion.y, .01f)
                assertFalse(motion.isFacingLeft)
                scene.exploreTree()
            }
            val visitField = HomeSceneView::class.java.getDeclaredField("treeVisit").apply { isAccessible = true }
            var lastFacingLeft = false
            var sawVisit = false
            instrumentation.runOnMainSync {
                repeat(4000) {
                    val visit = visitField.get(scene) as GingerTreeVisit?
                    if (visit != null) { sawVisit = true; lastFacingLeft = visit.facingLeft }
                    scene.advanceScene(16L)
                    if (sawVisit && visitField.get(scene) == null) return@runOnMainSync
                }
            }
            assertTrue("Ginger tree visit never started", sawVisit)
            assertNull("Ginger tree visit did not finish", visitField.get(scene))
            val motion = privateField(scene, "motion") as CompanionMotion
            assertEquals("Tree return lost facing", lastFacingLeft, motion.isFacingLeft)
            assertEquals("Tree return changed origin X", 500f, motion.x, .01f)
            assertEquals("Tree return changed origin Y", 650f, motion.y, .01f)
        } finally {
            preferences.reducedMotion = previousReducedMotion
        }
    }

    private fun privateField(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

}
