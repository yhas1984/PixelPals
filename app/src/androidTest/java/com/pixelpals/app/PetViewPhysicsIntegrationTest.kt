package com.pixelpals.app

import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.WindowManager
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifica la integración de la física compartida en PetView: tras un fling no
 * manejado por el behavior (o un soltado), la mascota entra en FALLING, cae con
 * su perfil por especie y vuelve a IDLE asentada en el suelo.
 */
@RunWith(AndroidJUnit4::class)
class PetViewPhysicsIntegrationTest {

    @org.junit.Before fun requireDisposableEmulator() {
        org.junit.Assume.assumeTrue("PetView construction starts repository status refreshes",
            android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("Emulator") || android.os.Build.HARDWARE == "ranchu")
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val step = 1f / 60f

    private fun newPetView(type: PetType): PetView {
        val view = PetView(context, screenWidth = 1_080, screenHeight = 2_400, petSpriteSize = 80, petType = type)
        view.layoutParams = WindowManager.LayoutParams(
            160,
            160,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            x = -40
            y = -40
        }
        view.windowX = 500
        view.windowY = 500
        return view
    }

    private fun launchPhysics(view: PetView, vx: Float, vy: Float) {
        val method = PetView::class.java.getDeclaredMethod("launchPhysics", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(view, vx, vy)
    }

    private fun advanceFalling(view: PetView, seconds: Float) {
        val method = PetView::class.java.getDeclaredMethod("updatePhysicsFalling", Float::class.javaPrimitiveType)
        method.isAccessible = true
        val steps = (seconds * 60f).toInt()
        for (i in 0 until steps) {
            if (view.state != PetState.FALLING) break
            method.invoke(view, step)
        }
    }

    @Test
    fun corgiTreasureCelebrationKeepsBodySizeInBothDirections() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view: PetView = newPetView(PetType.CORGI)
            val timer: java.lang.reflect.Field = PetView::class.java.getDeclaredField("treasureReactionTimer").apply { isAccessible = true }
            val update: java.lang.reflect.Method = PetView::class.java.getDeclaredMethod("update", Float::class.javaPrimitiveType).apply { isAccessible = true }
            val behavior: com.pixelpals.app.feature.overlay.behavior.CorgiBehavior = PetView::class.java.getDeclaredMethod("getBehavior")
                .apply { isAccessible = true }.invoke(view) as com.pixelpals.app.feature.overlay.behavior.CorgiBehavior
            for (direction: Float in listOf(-1f, 1f)) {
                behavior.resumeAfterCare(direction < 0f, false)
                view.state = PetState.DRAGGING
                view.animScaleX = direction
                timer.setFloat(view, .95f)
                repeat(65) {
                    update.invoke(view, step)
                    assertEquals("Celebration must not widen Corgi", direction, view.renderScaleX, 0f)
                    assertEquals("Celebration must not flatten Corgi", 1f, view.renderScaleY, 0f)
                }
            }
        }
    }

    @Test
    fun reducedMotionClearsTreasureMovementAndLetsTheReactionExpire() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val preferences = com.pixelpals.app.feature.home.CompanionPreferences(context)
            val oldReduced: Boolean = preferences.reducedMotion
            val timer = PetView::class.java.getDeclaredField("treasureReactionTimer").apply { isAccessible = true }
            val pendingTouch = PetView::class.java.getDeclaredField("isTouchPending").apply { isAccessible = true }
            val update = PetView::class.java.getDeclaredMethod("update", Float::class.javaPrimitiveType).apply { isAccessible = true }
            try {
                for (pet: PetType in PetType.entries) {
                    val view: PetView = newPetView(pet)
                    // Hold the underlying pose so this checks the actual composite treasure transform.
                    pendingTouch.setBoolean(view, true)
                    preferences.reducedMotion = false
                    timer.setFloat(view, .95f)
                    update.invoke(view, .1f)
                    assertTrue("$pet animated celebration should be visible", view.renderOffsetY < 0f)
                    preferences.reducedMotion = true
                    repeat(65) {
                        update.invoke(view, step)
                        assertEquals("$pet stationary X", 0f, view.renderOffsetX, 0f)
                        assertEquals("$pet stationary Y", 0f, view.renderOffsetY, 0f)
                        assertEquals("$pet no rotation", 0f, view.renderRotation, 0f)
                        assertEquals("$pet unchanged width", 1f, view.renderScaleX, 0f)
                        assertEquals("$pet unchanged height", 1f, view.renderScaleY, 0f)
                    }
                    assertEquals("$pet reaction must expire", 0f, timer.getFloat(view), 0f)
                    preferences.reducedMotion = false
                    update.invoke(view, step)
                    assertEquals("$pet expired celebration must not restart", 0f, view.renderOffsetY, 0f)
                }
            } finally { preferences.reducedMotion = oldReduced }
        }
    }

    @Test
    fun groundPetThrownUpFallsAndSettlesOnTheFloor() {
        val view = newPetView(PetType.TARO)
        launchPhysics(view, 200f, -400f)
        assertEquals(PetState.FALLING, view.state)

        advanceFalling(view, 10f)

        assertEquals(PetState.IDLE, view.state)
        assertEquals(view.bounds.floor, view.windowY)
    }

    @Test
    fun fallingPetsKeepTheirFacingAndRigidSpeciesKeepTheirShape() {
        val soft: Set<PetType> = setOf(PetType.JELLY, PetType.BLOOP, PetType.NUBE_MICHI)
        for (pet: PetType in PetType.entries) for (direction: Float in listOf(-1f, 1f)) {
            val view: PetView = newPetView(pet)
            view.animScaleX = direction
            launchPhysics(view, direction * 200f, 400f)
            repeat(20) {
                advanceFalling(view, step)
                assertEquals(PetState.FALLING, view.state)
                assertEquals("$pet must keep its facing", direction, kotlin.math.sign(view.animScaleX), 0f)
                assertEquals("$pet must not gain area", 1f, kotlin.math.abs(view.animScaleX) * view.animScaleY, .00001f)
                if (pet !in soft) assertEquals("$pet has a rigid body", 1f, view.animScaleY, 0f)
            }
        }
    }

    @Test
    fun groundPetDroppedWithoutFlingFallsAndSettles() {
        val view = newPetView(PetType.CORGI)
        view.windowY = 800
        launchPhysics(view, 0f, 0f)
        assertEquals(PetState.FALLING, view.state)

        advanceFalling(view, 10f)

        assertEquals(PetState.IDLE, view.state)
        assertEquals(view.bounds.floor, view.windowY)
    }

    @Test fun duckDragReleaseDispatchesToItsControlledLandingWithoutTeleporting() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = newPetView(PetType.PATITO)
            val body = PetView::class.java.getDeclaredField("physicsBody").apply { isAccessible = true }
            val now = SystemClock.uptimeMillis()
            try {
                fun touch(action: Int, at: Long, x: Float) {
                    val event = MotionEvent.obtain(now, at, action, x, 0f, 0)
                    try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
                }
                touch(MotionEvent.ACTION_DOWN, now, 0f)
                touch(MotionEvent.ACTION_MOVE, now + 40, 100f)
                touch(MotionEvent.ACTION_MOVE, now + 120, 130f)
                assertEquals(PetState.DRAGGING, view.state)
                val heldX = view.windowX; val heldY = view.windowY
                // Stationary before release: this is a drop, not a fling gesture.
                touch(MotionEvent.ACTION_MOVE, now + 700, 130f)
                touch(MotionEvent.ACTION_UP, now + 900, 130f)
                assertEquals(PetState.INTERACTING, view.state)
                assertTrue("Duck should own its landing", body.get(view) == null)
                assertEquals("Release moved X before the next animation step", heldX, view.windowX)
                assertEquals("Release teleported to the floor", heldY, view.windowY)
            } finally { detachForTest(view) }
        }
    }

    @Test
    fun flyingPetSettlesWithoutReachingTheFloor() {
        val view = newPetView(PetType.ANGEL)
        view.windowY = 1_000
        launchPhysics(view, 100f, 50f)
        assertEquals(PetState.FALLING, view.state)

        advanceFalling(view, 10f)

        assertEquals(PetState.IDLE, view.state)
        assertTrue(view.windowY < view.bounds.floor)
    }

    @Test
    fun aquaticPetFallsSlowlyThenResumesItsPoseOnTheFloor() {
        val view = newPetView(PetType.PIRU)
        view.windowY = 1_000
        launchPhysics(view, 100f, 50f)
        assertEquals(PetState.FALLING, view.state)

        // Tras el settle, PiruBehavior.reset retoma su pose (se posa en el suelo).
        advanceFalling(view, 10f)

        assertEquals(PetState.IDLE, view.state)
        assertEquals(view.bounds.floor, view.windowY)
    }
    @Test fun tappingDuringSharedFlightKeepsMomentumAndFacingUntilSettlement(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val body = PetView::class.java.getDeclaredField("physicsBody").apply { isAccessible = true }
        val getBehavior = PetView::class.java.getDeclaredMethod("getBehavior").apply { isAccessible = true }
        val detach = PetView::class.java.getDeclaredMethod("onDetachedFromWindow").apply { isAccessible = true }
        var checked: Int = 0
        for (pet: PetType in PetType.entries) {
            lateinit var view: PetView
            lateinit var behavior: com.pixelpals.app.feature.overlay.behavior.PetBehavior
            instrumentation.runOnMainSync {
                view = newPetView(pet)
                behavior = getBehavior.invoke(view) as com.pixelpals.app.feature.overlay.behavior.PetBehavior
            }
            try {
                var usesRuntimeInput: Boolean = false
                instrumentation.runOnMainSync { usesRuntimeInput = behavior.usesRuntimeInput }
                if (!usesRuntimeInput) waitForRealBehaviorAssets(behavior, instrumentation)
                instrumentation.runOnMainSync {
                    if (!behavior.usesRuntimeInput) {
                        checked++
                        view.animScaleX = -1f
                        launchPhysics(view, -200f, 150f)
                        advanceFalling(view, .1f)
                        val before = body.get(view)
                        val x: Int = view.windowX
                        val y: Int = view.windowY
                        val scaleX: Float = view.animScaleX
                        val scaleY: Float = view.animScaleY
                        var affordanceShown: Boolean = false
                        view.onCareAffordance = { affordanceShown = true }
                        view.performClick()
                        assertEquals("$pet tap suspended shared flight", PetState.FALLING, view.state)
                        assertEquals("$pet tap replaced momentum", before, body.get(view))
                        assertEquals(x, view.windowX)
                        assertEquals(y, view.windowY)
                        assertEquals(scaleX, view.animScaleX, .001f)
                        assertEquals(scaleY, view.animScaleY, .001f)
                        assertTrue("$pet lost its care affordance", affordanceShown)
                        if (pet == PetType.PATITO) {
                            var previousY: Int = view.windowY
                            var sawControlledLanding = false
                            for (tick in 0 until 60 * 30) {
                                if (view.state == PetState.FALLING) advanceFalling(view, step)
                                else if (view.state == PetState.INTERACTING) {
                                    behavior.updateInteracting(step)
                                    sawControlledLanding = true
                                    assertTrue("$pet jumped during controlled landing", view.windowY >= previousY)
                                    assertTrue("$pet exceeded landing step", view.windowY - previousY <= view.petSpriteSize * 2.5f * step + 1f)
                                }
                                previousY = view.windowY
                                if (view.state == PetState.IDLE) break
                            }
                            assertTrue("$pet never entered controlled landing", sawControlledLanding)
                            assertEquals("$pet must finish on the floor", view.bounds.floor, view.windowY)
                        } else {
                            advanceFalling(view, 15f)
                        }
                        assertEquals("$pet never settled after affection", PetState.IDLE, view.state)
                    }
                }
            } finally { instrumentation.runOnMainSync { detach.invoke(view) } }
        }
        assertTrue("Expected shared physics across the legacy species", checked >= 10)
    }

    private fun waitForRealBehaviorAssets(
        behavior: com.pixelpals.app.feature.overlay.behavior.PetBehavior,
        instrumentation: android.app.Instrumentation,
    ): Unit {
        val loading = com.pixelpals.app.feature.overlay.behavior.BaseBehavior::class.java
            .getDeclaredField("isLoading").apply { isAccessible = true }
        repeat(100) {
            var isLoading: Boolean = true
            instrumentation.runOnMainSync { isLoading = loading.getBoolean(behavior) }
            if (!isLoading) return
            Thread.sleep(50)
        }
        var isLoading: Boolean = true
        instrumentation.runOnMainSync { isLoading = loading.getBoolean(behavior) }
        assertTrue("Behavior artwork did not load", !isLoading)
    }

    @Test
    fun piruTapDuringFallingRetainsBodyAndProgresses(): Unit {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view: PetView = newPetView(PetType.PIRU)
            val body: java.lang.reflect.Field = PetView::class.java.getDeclaredField("physicsBody").apply { isAccessible = true }
            try {
                launchPhysics(view, 0f, 160f)
                val downTime: Long = SystemClock.uptimeMillis()
                val down: MotionEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                val up: MotionEvent = MotionEvent.obtain(downTime, downTime + 20L, MotionEvent.ACTION_UP, 0f, 0f, 0)
                try {
                    assertTrue(view.onTouchEvent(down))
                    assertTrue("Piru tap cleared its flight body on DOWN", body.get(view) != null)
                    assertTrue(view.onTouchEvent(up))
                    assertEquals(PetState.FALLING, view.state)
                    assertTrue("Piru tap cleared its flight body", body.get(view) != null)
                    val beforeY: Int = view.windowY
                    advanceFalling(view, 1f / 60f)
                    assertTrue("Piru flight stopped after tap", view.windowY != beforeY)
                } finally {
                    down.recycle()
                    up.recycle()
                }
            } finally {
                detachForTest(view)
            }
        }
    }

    @Test
    fun piruHoldDuringFallingRetainsBodyAndProgresses(): Unit {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view: PetView = newPetView(PetType.PIRU)
            val body: java.lang.reflect.Field = PetView::class.java.getDeclaredField("physicsBody").apply { isAccessible = true }
            val holdRunnable: java.lang.reflect.Field = PetView::class.java.getDeclaredField("holdRunnable").apply { isAccessible = true }
            try {
                launchPhysics(view, 0f, 160f)
                val downTime: Long = SystemClock.uptimeMillis() - ViewConfiguration.getLongPressTimeout().toLong() - 10L
                val down: MotionEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                val up: MotionEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 0f, 0f, 0)
                try {
                    assertTrue(view.onTouchEvent(down))
                    assertTrue("Piru hold cleared its flight body on DOWN", body.get(view) != null)
                    (holdRunnable.get(view) as Runnable).run()
                    assertEquals(PetState.FALLING, view.state)
                    assertTrue("Piru hold cleared its flight body", body.get(view) != null)
                    assertTrue(view.onTouchEvent(up))
                    assertEquals(PetState.FALLING, view.state)
                    val beforeY: Int = view.windowY
                    advanceFalling(view, 1f / 60f)
                    assertTrue("Piru flight stopped after hold", view.windowY != beforeY)
                } finally {
                    down.recycle()
                    up.recycle()
                }
            } finally {
                detachForTest(view)
            }
        }
    }

    @Test
    fun piruDragMoveClearsBodyAfterCrossingSlop(): Unit {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view: PetView = newPetView(PetType.PIRU)
            val body: java.lang.reflect.Field = PetView::class.java.getDeclaredField("physicsBody").apply { isAccessible = true }
            try {
                launchPhysics(view, 0f, 160f)
                val downTime: Long = SystemClock.uptimeMillis()
                val down: MotionEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                val move: MotionEvent = MotionEvent.obtain(downTime, downTime + 40L, MotionEvent.ACTION_MOVE, 100f, 0f, 0)
                try {
                    assertTrue(view.onTouchEvent(down))
                    advanceFalling(view, 1f / 60f)
                    val beforeX: Int = view.windowX
                    val beforeY: Int = view.windowY
                    assertTrue(view.onTouchEvent(move))
                    assertEquals(PetState.DRAGGING, view.state)
                    assertTrue("Piru drag did not clear shared flight body", body.get(view) == null)
                    assertEquals("Piru drag jumped in X when it crossed slop", beforeX, view.windowX)
                    assertEquals("Piru drag jumped in Y when it crossed slop", beforeY, view.windowY)
                } finally {
                    down.recycle()
                    move.recycle()
                }
            } finally {
                detachForTest(view)
            }
        }
    }

    @Test fun cancelledTouchPreservesTheExistingFall(): Unit {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view: PetView = newPetView(PetType.PIRU)
            val body = PetView::class.java.getDeclaredField("physicsBody").apply { isAccessible = true }
            try {
                launchPhysics(view, -200f, 160f)
                val before = body.get(view)
                val now: Long = SystemClock.uptimeMillis()
                for (action: Int in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL)) {
                    val event: MotionEvent = MotionEvent.obtain(now, now, action, 0f, 0f, 0)
                    try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
                }
                assertEquals(PetState.FALLING, view.state)
                assertEquals("Cancellation replaced momentum", before, body.get(view))
                val beforeY: Int = view.windowY
                advanceFalling(view, 1f / 60f)
                assertTrue(view.windowY != beforeY)
            } finally { detachForTest(view) }
        }
    }

    private fun detachForTest(view: PetView): Unit {
        PetView::class.java.getDeclaredMethod("onDetachedFromWindow").apply { isAccessible = true }.invoke(view)
    }

}
