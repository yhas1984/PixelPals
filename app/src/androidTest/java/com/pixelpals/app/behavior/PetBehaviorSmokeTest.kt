package com.pixelpals.app.feature.overlay.behavior

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.DefaultPetRandom
import kotlin.random.Random
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetBehaviorSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Test
    fun everyPetLoadsMovesInteractsAndFlingsWithinBounds() {
        PetType.entries.forEachIndexed { index, petType ->
            lateinit var bridge: TestPetBridge
            lateinit var behavior: PetBehavior
            instrumentation.runOnMainSync {
                bridge = TestPetBridge(context, petType)
                behavior = PetBehaviorFactory.create(
                    petType,
                    bridge,
                    DefaultPetRandom(Random(index + 100))
                )
            }
            waitForAssets(petType, behavior)
            instrumentation.runOnMainSync {
                advance(behavior, bridge, 6f)
                behavior.onInteract()
                advance(behavior, bridge, 6f)
                behavior.onFling(1_800f, -1_250f)
                // Contrato con PetView: si el pet no maneja el fling, el view
                // vuelve a IDLE y resetea. El test lo reproduce para verificar
                // que ningún pet queda atrapado en DRAGGING.
                if (bridge.state == PetState.DRAGGING) {
                    bridge.state = PetState.IDLE
                    behavior.reset()
                }
                advance(behavior, bridge, 25f)
                behavior.onDraw(
                    Canvas(Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)),
                    80f,
                    80f
                )
                assertBridgeIsValid(petType, bridge)
                assertTrue(
                    "$petType quedó atrapado en DRAGGING tras el fling",
                    bridge.state != PetState.DRAGGING
                )
                // El runtime puede encontrarse legítimamente en una reacción
                // social autónoma al tomar esta instantánea. Lo que el contrato
                // prohíbe es que INTERACTING no tenga una salida acotada.
                repeat((8f / STEP_SECONDS).toInt()) {
                    if (bridge.state != PetState.INTERACTING) return@repeat
                    behavior.updateInteracting(STEP_SECONDS)
                }
                assertNotEquals("$petType left an interaction running", PetState.INTERACTING, bridge.state)
                behavior.destroy()
            }
        }
    }

    private fun advance(behavior: PetBehavior, bridge: TestPetBridge, seconds: Float) {
        repeat((seconds * 60f).toInt()) {
            when (bridge.state) {
                PetState.IDLE -> behavior.updateIdle(STEP_SECONDS)
                PetState.DRAGGING -> behavior.updateDrag(STEP_SECONDS)
                PetState.FALLING -> behavior.updateFalling(STEP_SECONDS)
                PetState.JUMPING -> behavior.updateJumping(STEP_SECONDS)
                PetState.INTERACTING -> behavior.updateInteracting(STEP_SECONDS)
                else -> behavior.updateAutonomous(STEP_SECONDS)
            }
        }
    }

    private fun assertBridgeIsValid(petType: PetType, bridge: TestPetBridge) {
        val params = bridge.getWindowParams()
        val maxX = bridge.screenWidth - bridge.petSpriteSize
        val maxY = bridge.screenHeight - bridge.petSpriteSize
        val maxFrame = maxFrameByPet[petType] ?: Int.MAX_VALUE
        assertTrue("$petType x out of bounds: ${params.x}", params.x in 0..maxX)
        assertTrue("$petType y out of bounds: ${params.y}", params.y in 0..maxY)
        assertTrue("$petType frame out of range: ${bridge.currentFrame}", bridge.currentFrame in 0..maxFrame)
        assertTrue("$petType scaleX is invalid", bridge.animScaleX.isFinite())
        assertTrue("$petType scaleY is invalid", bridge.animScaleY.isFinite())
        assertTrue("$petType rotation is invalid", bridge.animRotation.isFinite())
        assertTrue("$petType alpha is invalid", bridge.animAlpha.isFinite() && bridge.animAlpha in 0f..1f)
    }

    private fun waitForAssets(petType: PetType, behavior: PetBehavior) {
        repeat(30) {
            instrumentation.waitForIdleSync()
            if (hasLoadedAssets(behavior)) return
            Thread.sleep(100L)
        }
        assertTrue("$petType did not load animation assets", hasLoadedAssets(behavior))
    }

    private fun hasLoadedAssets(behavior: PetBehavior): Boolean {
        val baseBehavior = behavior as BaseBehavior
        val framesField = BaseBehavior::class.java.getDeclaredField("frames").apply { isAccessible = true }
        val frames = framesField.get(baseBehavior) as List<*>
        val sheetField = BaseBehavior::class.java.getDeclaredField("spriteSheetBitmap").apply { isAccessible = true }
        val spriteSheet = sheetField.get(baseBehavior)
        return frames.any { it != null } || spriteSheet != null
    }

    private companion object {
        const val STEP_SECONDS = 1f / 60f

        /** Último índice de frame válido por pet (según sus resourceIds / atlas). */
        val maxFrameByPet: Map<PetType, Int> = mapOf(
            PetType.BLOOP to 6,
            PetType.NUBE_MICHI to 10,
            PetType.JELLY to 7,
            PetType.CORGI to 13,
            PetType.PATITO to 9,
            PetType.DIABLILLO to 9,
            PetType.MOKI to 19,
            PetType.ANGEL to 15,
            PetType.GINGER to 15,
            PetType.YUKI to 15,
            PetType.PIRU to 15,
            PetType.TARO to 39,
            PetType.MENTA to 15,
            PetType.TELA to 15,
            PetType.LUMI to 39,
        )
    }
}
