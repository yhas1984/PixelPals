package com.pixelpals.app.feature.overlay.behavior

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.data.repository.PetProgress
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.AccessoryCatalogItem
import com.pixelpals.app.core.motion.DefaultPetRandom
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetPersonality
import com.pixelpals.app.status.PetStatusSnapshot
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
                advance(behavior, bridge, 25f)
                behavior.onDraw(
                    Canvas(Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)),
                    80f,
                    80f
                )
                assertBridgeIsValid(petType, bridge)
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
        assertTrue("$petType x out of bounds: ${params.x}", params.x in 0..(bridge.screenWidth - bridge.petSpriteSize))
        assertTrue("$petType y out of bounds: ${params.y}", params.y in 0..bridge.screenHeight)
        assertTrue("$petType scaleX is invalid", bridge.animScaleX.isFinite())
        assertTrue("$petType scaleY is invalid", bridge.animScaleY.isFinite())
        assertTrue("$petType rotation is invalid", bridge.animRotation.isFinite())
        assertTrue("$petType alpha is invalid", bridge.animAlpha.isFinite() && bridge.animAlpha in 0f..1f)
        assertTrue("$petType frame is invalid", bridge.currentFrame >= 0)
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

    private class TestPetBridge(context: Context, petType: PetType) : View(context), PetViewBridge {
        private val params = WindowManager.LayoutParams(
            112,
            112,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            x = 300
            y = 600
        }

        override var currentFrame: Int = 0
        override var animScaleX: Float = 1f
        override var animScaleY: Float = 1f
        override var animOffsetX: Float = 0f
        override var animOffsetY: Float = 0f
        override var animRotation: Float = 0f
        override var animAlpha: Float = 1f
        override var animColorFilter: ColorFilter? = null
        override val renderScaleX: Float get() = animScaleX
        override val renderScaleY: Float get() = animScaleY
        override val renderOffsetX: Float get() = animOffsetX
        override val renderOffsetY: Float get() = animOffsetY
        override val renderRotation: Float get() = animRotation
        override var velocityX: Float = 0f
        override var velocityY: Float = 0f
        override var state: PetState = PetState.IDLE
        override val screenWidth: Int = 1_080
        override val screenHeight: Int = 2_400
        override val petSpriteSize: Int = 80
        override val groundY: Int = screenHeight - petSpriteSize
        override val petStatus = PetStatusSnapshot(
            petId = petType.name.lowercase(),
            health = 90,
            energy = 80,
            hunger = 75,
            hygiene = 85,
            bond = 30,
            mood = PetMood.HAPPY,
            careStreakDays = 2,
            softCurrency = 10,
            dominantSuggestion = CareAction.PLAY,
            memoriesUnlocked = 1
        )
        override val petPersonality: PetPersonality = when (petType) {
            PetType.BLOOP -> PetPersonality.DREAMY
            PetType.NUBE_MICHI -> PetPersonality.SWEET
            PetType.JELLY -> PetPersonality.BOUNCY
            PetType.CORGI -> PetPersonality.LOYAL
            PetType.GINGER -> PetPersonality.ELEGANT
            PetType.ANGEL -> PetPersonality.ANGELIC
            PetType.PATITO -> PetPersonality.CURIOUS
            PetType.DIABLILLO -> PetPersonality.CHAOTIC
            PetType.MOKI -> PetPersonality.CURIOUS
        }
        override val equippedAccessory: AccessoryCatalogItem? = null
        override val headAnchorYRatio: Float = -0.20f
        override fun activeModifiers(): List<com.pixelpals.app.data.catalog.PetModifier> = emptyList()
        override val outfitFrameAssets: List<String>? = null
        override var windowX: Int = params.x
        override var windowY: Int = params.y

        override fun getWindowParams(): WindowManager.LayoutParams = params

        override fun updateWindowLayout(params: WindowManager.LayoutParams) {
            windowX = params.x
            windowY = params.y
        }

        override fun showBubble(text: String) = Unit
        override fun hideBubble() = Unit
        override fun playHaptic(durationMs: Long) = Unit
        override fun teleportToRandomEdge() = Unit
        override fun trackInteraction() = Unit
        override fun resumeAnimation() = Unit
        override fun pauseAnimation() = Unit
        override fun consumeTreasure(emoji: String) = Unit
        override fun recordCareAction(action: CareAction) = Unit
        override fun onBatteryChanged(percent: Int, isCharging: Boolean) = Unit
        override fun onKeyboardChanged(visible: Boolean, height: Int) = Unit
        override fun onAirplaneModeChanged(isAirplane: Boolean) = Unit
    }

    private companion object {
        const val STEP_SECONDS = 1f / 60f
    }
}
