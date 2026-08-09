package com.pixelpals.app.feature.overlay.behavior

import android.graphics.ColorFilter
import android.view.WindowManager
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetPersonality
import com.pixelpals.app.status.PetStatusSnapshot

/**
 * PetViewBridge — Interface for behaviors to access PetView state and perform actions.
 * Decouples the specific pet logic from the main rendering View.
 */
interface PetViewBridge {
    // --- Animation State ---
    var currentFrame: Int
    var animScaleX: Float
    var animScaleY: Float
    var animOffsetX: Float
    var animOffsetY: Float
    var animRotation: Float
    var animAlpha: Float
    var animColorFilter: ColorFilter?

    /** Filtro de color del cosmético equipado (tint) — persiste entre resets. */
    var cosmeticColorFilter: ColorFilter?
    val renderScaleX: Float
    val renderScaleY: Float
    val renderOffsetX: Float
    val renderOffsetY: Float
    val renderRotation: Float

    // --- Physics & Position ---
    var velocityX: Float
    var velocityY: Float

    /** Gets the current window layout parameters for direct position manipulation */
    fun getWindowParams(): WindowManager.LayoutParams?

    /** Requests the WindowManager to update the view's layout (position/size) */
    fun updateWindowLayout(params: WindowManager.LayoutParams)

    // --- State Management ---
    var state: PetState

    // --- Environment Info ---
    val screenWidth: Int
    val screenHeight: Int
    val petSpriteSize: Int
    val groundY: Int
    val petStatus: PetStatusSnapshot
    val petPersonality: PetPersonality

    /**
     * Factor de escala de DIBUJO por pet (no afecta a la física): normaliza el
     * tamaño visible de todos los pets al de Moki (el contenido de cada sprite
     * ocupa un % distinto de su frame). Solo lo usa [BaseBehavior.onDraw].
     */
    val spriteScale: Float

    /** Fracción de contenido (alto) del frame IDLE de este pet (0..1): sirve de
     *  referencia para que ningún frame de animación se dibuje más alto que el
     *  idle (los frames "estirados" se comprimen; los bajos —squash, sniff,
     *  dormir, gatear— se mantienen naturales). */
    val spriteIdleContentFraction: Float

    /** Fracción de contenido (alto) de CADA frame del pet (0..1), índice = frame.
     *  Medidas con bbox de alfa (PIL) sobre los assets; ver tools/normalize_frames.py. */
    val spriteFrameContentFractions: FloatArray

    /** Inset superior del sistema (barra de estado/notch) en px; 0 si no aplica. */
    val topSystemInsetPx: Int get() = 0

    /** Inset inferior del sistema (barra de navegación/gestos) en px; 0 si no aplica. */
    val bottomSystemInsetPx: Int get() = 0

    // Window position (absolute screen coordinates)
    var windowX: Int
    var windowY: Int

    // --- Actions & Feedback ---
    fun showBubble(text: String)
    fun hideBubble()
    fun playHaptic(durationMs: Long)
    fun invalidate()
    fun teleportToRandomEdge()

    /** Notifies the system that an interaction (XP) happened */
    fun trackInteraction()

    // --- Bridge Methods for PetService ---
    fun resumeAnimation()
    fun pauseAnimation()
    fun consumeTreasure(emoji: String)
    fun recordCareAction(action: CareAction)
    fun onBatteryChanged(percent: Int, isCharging: Boolean)
    fun onKeyboardChanged(visible: Boolean, height: Int)
    fun onAirplaneModeChanged(isAirplane: Boolean)
}
