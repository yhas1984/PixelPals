package com.pixelpals.app.behavior

import android.graphics.ColorFilter
import android.view.WindowManager
import com.pixelpals.app.PetState
import com.pixelpals.app.PetProgress

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
    fun setProgress(progress: PetProgress)
    fun consumeTreasure(emoji: String)
    fun onBatteryChanged(percent: Int, isCharging: Boolean)
    fun onKeyboardChanged(visible: Boolean, height: Int)
    fun onAirplaneModeChanged(isAirplane: Boolean)
}
