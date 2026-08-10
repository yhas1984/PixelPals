package com.pixelpals.app.feature.overlay.behavior

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.repository.PetProgress
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetPersonality
import com.pixelpals.app.status.PetStatusSnapshot

/** Bridge de prueba compartido por los tests instrumentados de behaviors. */
class TestPetBridge(context: Context, petType: PetType) : View(context), PetViewBridge {
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
    override var cosmeticColorFilter: ColorFilter? = null
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
    override val spriteScale: Float = 1f
    override val spriteIdleContentFraction: Float = 1f
    override val spriteFrameContentFractions: FloatArray = floatArrayOf(1f)
    override val groundY: Int
        get() = bounds.floor
    override val topSystemInsetPx: Int = 100
    override val bottomSystemInsetPx: Int = 200
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
        PetType.YUKI -> PetPersonality.DREAMY
        PetType.PIRU -> PetPersonality.CURIOUS
        PetType.TARO -> PetPersonality.DREAMY
        PetType.MENTA -> PetPersonality.ELEGANT
        PetType.TELA -> PetPersonality.CURIOUS
    }
    override var windowX: Int = params.x
    override var windowY: Int = params.y

    override fun getWindowParams(): WindowManager.LayoutParams = params

    override fun updateWindowLayout(params: WindowManager.LayoutParams) {
        windowX = params.x
        windowY = params.y
    }

    override fun showBubble(text: String) = Unit
    override fun hideBubble() = Unit
    override fun invalidate() = Unit
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
