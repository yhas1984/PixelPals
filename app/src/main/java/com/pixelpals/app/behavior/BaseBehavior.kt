package com.pixelpals.app.behavior

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.pixelpals.app.PetType

/**
 * BaseBehavior — Base class for pet behaviors.
 * Provides common variables and utilities shared across all pets.
 */
abstract class BaseBehavior(
    protected val context: Context,
    protected val petType: PetType,
    protected val petSpriteSize: Int,
    protected val screenWidth: Int,
    protected val screenHeight: Int
) : PetBehavior {

    // Shared state (set by PetView)
    var currentFrame: Int = 0
    var velocityX: Float = 0f
    var velocityY: Float = 0f
    var animScaleX: Float = 1f
    var animScaleY: Float = 1f
    var animOffsetX: Float = 0f
    var animOffsetY: Float = 0f
    var animRotation: Float = 0f
    var animAlpha: Float = 1f

    // Sprite frames
    lateinit var spriteFrames: List<Bitmap>

    // Handler for delayed actions
    protected val handler = Handler(Looper.getMainLooper())

    // Time accumulator
    protected var time: Float = 0f

    // Ground Y position
    protected val groundY: Int get() = screenHeight - petSpriteSize - 120

    // Window manager reference
    var windowManager: WindowManager? = null

    /** Update time */
    fun updateTime(dt: Float) {
        time += dt
    }

    /** Reset animations */
    override fun reset() {
        animScaleX = 1f
        animScaleY = 1f
        animAlpha = when (petType) {
            PetType.BLOOP -> 0.8f
            PetType.JELLY -> 0.95f
            else -> 1f
        }
        animOffsetX = 0f
        animOffsetY = 0f
        animRotation = 0f
    }

    /** Get window params */
    protected fun getWindowParams(): WindowManager.LayoutParams? {
        return try {
            val field = WindowManager.LayoutParams::class.java.getDeclaredField("x")
            // This is a simplified version - actual implementation uses reflection
            null
        } catch (e: Exception) {
            null
        }
    }
}
