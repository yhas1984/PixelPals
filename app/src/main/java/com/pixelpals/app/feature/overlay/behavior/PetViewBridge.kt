package com.pixelpals.app.feature.overlay.behavior

import android.graphics.ColorFilter
import android.view.WindowManager
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.data.catalog.AccessoryCatalogItem
import com.pixelpals.app.data.catalog.PetModifier
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

    /**
     * Fracción (negativa, desde el centro del view) donde está la cabeza del pet.
     * Ej: -0.2 → la cabeza está a 20% del petSpriteSize por encima del centro.
     * Lo calcula el behavior a partir del bbox del frame o del pivot del atlas.
     */
    val headAnchorYRatio: Float

    val petStatus: PetStatusSnapshot
    val petPersonality: PetPersonality
    val equippedAccessory: AccessoryCatalogItem?

    /** Lista de modificadores del accesorio equipado (velocidad, partículas, etc.). */
    fun activeModifiers(): List<PetModifier>

    /**
     * Nombres de los frames del outfit activo (cargados desde assets),
     * o null si no hay outfit. El outfit REEMPLAZA los frames del pet.
     */
    val outfitFrameAssets: List<String>?

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
