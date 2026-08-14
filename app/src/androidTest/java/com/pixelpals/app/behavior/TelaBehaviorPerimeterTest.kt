package com.pixelpals.app.feature.overlay.behavior

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.DefaultPetRandom
import com.pixelpals.app.core.motion.PetRandom
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TelaBehavior es un animal de PERÍMETRO: cuando está cerca de un borde debe
 * recorrerlo en sentido horario (techo → derecha → suelo → izquierda), sin
 * quedarse colgada al azar. Estas pruebas verifican la decisión de ruta para
 * las cuatro esquinas, los bordes y el centro de la pantalla.
 *
 * Constantes del TestPetBridge: screen 1080x2400, sprite 80, topInset 100,
 * bottomInset 200 → minY=160, maxY=2060, maxX=1000, ceilingY=170, floorY=2040.
 */
@RunWith(AndroidJUnit4::class)
class TelaBehaviorPerimeterTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var bridge: TestPetBridge
    private lateinit var behavior: TelaBehavior

    @Before
    fun setUp() {
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.TELA)
            behavior = TelaBehavior(bridge, DefaultPetRandom(Random(7)))
        }
    }

    private fun placeAndDecide(x: Int, y: Int) {
        instrumentation.runOnMainSync {
            bridge.getWindowParams().apply {
                this.x = x
                this.y = y
            }
            behavior.reset()
        }
    }

    private fun field(name: String): Any? {
        val f = TelaBehavior::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.get(behavior)
    }

    private fun modeName(): String = (field("mode") as Enum<*>).name
    private fun toX(): Float = field("toX") as Float
    private fun toY(): Float = field("toY") as Float
    private fun facingRight(): Boolean = field("facingRight") as Boolean

    private fun field(target: TelaBehavior, name: String): Any? {
        val f = TelaBehavior::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target)
    }

    private fun modeName(target: TelaBehavior): String = (field(target, "mode") as Enum<*>).name

    private class FixedPetRandom(private val value: Float) : PetRandom {
        override fun nextFloat(): Float = value
        override fun nextInt(from: Int, until: Int): Int = from
    }

    @Test
    fun topLeftCornerWalksTheCeilingToTheRight() {
        placeAndDecide(x = 5, y = 165)
        assertEquals("CEILING", modeName())
        assertEquals(980f, toX())
        assertEquals(170f, toY())
        assertTrue("hacia la derecha", facingRight())
    }

    @Test
    fun topRightCornerClimbsDownTheRightWall() {
        placeAndDecide(x = 995, y = 165)
        assertEquals("CLIMB", modeName())
        assertEquals(1000f, toX())
        assertEquals(2040f, toY())
        assertTrue("pared derecha", facingRight())
    }

    @Test
    fun bottomRightCornerWalksTheFloorToTheLeft() {
        placeAndDecide(x = 995, y = 2055)
        assertEquals("WALK", modeName())
        assertEquals(20f, toX())
        assertEquals(2040f, toY())
        assertFalse("hacia la izquierda", facingRight())
    }

    @Test
    fun bottomLeftCornerClimbsUpTheLeftWall() {
        placeAndDecide(x = 5, y = 2055)
        assertEquals("CLIMB", modeName())
        assertEquals(0f, toX())
        assertEquals(170f, toY())
        assertFalse("pared izquierda", facingRight())
    }

    @Test
    fun topEdgeKeepsTraversingRight() {
        placeAndDecide(x = 500, y = 165)
        assertEquals("CEILING", modeName())
        assertEquals(980f, toX())
        assertEquals(170f, toY())
    }

    @Test
    fun rightEdgeClimbsDown() {
        placeAndDecide(x = 995, y = 1000)
        assertEquals("CLIMB", modeName())
        assertEquals(1000f, toX())
        assertEquals(2040f, toY())
    }

    @Test
    fun bottomEdgeWalksLeft() {
        placeAndDecide(x = 500, y = 2055)
        assertEquals("WALK", modeName())
        assertEquals(20f, toX())
        assertEquals(2040f, toY())
    }

    @Test
    fun leftEdgeClimbsUp() {
        placeAndDecide(x = 5, y = 1000)
        assertEquals("CLIMB", modeName())
        assertEquals(0f, toX())
        assertEquals(170f, toY())
    }

    @Test
    fun centerKeepsTheHangingPersonalityWithoutCrossingTheScreen() {
        placeAndDecide(x = 500, y = 1200)
        // En el centro la X nunca cambia: solo cuelga o sube/baja en vertical.
        assertEquals(500f, toX())
        val y = toY()
        assertTrue(
            "movimiento vertical local (y=$y)",
            y == 1200f || y == 170f || y == 2040f
        )
    }

    @Test
    fun topEdgeCanStartAnUprightWebDescent() {
        val webBridge = TestPetBridge(instrumentation.targetContext, PetType.TELA)
        val webBehavior = TelaBehavior(webBridge, FixedPetRandom(0.1f))
        instrumentation.runOnMainSync {
            webBridge.getWindowParams().apply {
                x = 500
                y = 165
            }
            webBehavior.reset()
        }

        assertEquals("WEB_DESCEND", modeName(webBehavior))
        assertEquals(500f, field(webBehavior, "toX"))
        val targetY = field(webBehavior, "toY") as Float
        assertTrue("desciende a media pantalla", targetY in 1_000f..1_600f)
        assertEquals("la seda nace del borde superior", 0f, field(webBehavior, "webAnchorY") as Float)
    }

    @Test
    fun cornerCanLeaveTemporaryDecorativeWeb() {
        val cornerBridge = TestPetBridge(instrumentation.targetContext, PetType.TELA)
        val cornerBehavior = TelaBehavior(cornerBridge, FixedPetRandom(0.3f))
        instrumentation.runOnMainSync {
            cornerBridge.getWindowParams().apply {
                x = 5
                y = 165
            }
            cornerBehavior.reset()
        }

        assertEquals(TelaWebCorner.TOP_LEFT, cornerBridge.lastTelaCornerWebState?.corner)
        assertTrue((cornerBridge.lastTelaCornerWebState?.radius ?: 0f) > 0f)
    }
}
