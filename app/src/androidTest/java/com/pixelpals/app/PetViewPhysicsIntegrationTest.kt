package com.pixelpals.app

import android.graphics.PixelFormat
import android.view.WindowManager
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
    fun groundPetThrownUpFallsAndSettlesOnTheFloor() {
        val view = newPetView(PetType.TARO)
        launchPhysics(view, 200f, -400f)
        assertEquals(PetState.FALLING, view.state)

        advanceFalling(view, 10f)

        assertEquals(PetState.IDLE, view.state)
        assertEquals(view.bounds.floor, view.windowY)
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
}
