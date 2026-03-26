package com.pixelpals.app.behavior
import com.pixelpals.app.PetState

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.content.Context
import android.view.View
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.math.abs
import kotlin.random.Random
import com.pixelpals.app.R

/**
 * CorgiBehavior — Playful dog with sitting/standing states.
 * Optimized with asynchronous loading and smooth animations.
 */
class CorgiBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    private var time = 0f
    private var corgiPose = CorgiPose.SITTING
    private var corgiIdleTimer = 0f
    
    private val frames = mutableListOf<Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isLoading = true

    enum class CorgiPose { SITTING, STANDING, WALKING, BARKING, JUMPING }

    init {
        loadFramesAsync()
    }

    private fun loadFramesAsync() {
        scope.launch {
            val context = (petView as View).context
            val resIds = listOf(
                R.drawable.corgi_0, R.drawable.corgi_1, R.drawable.corgi_2,
                R.drawable.corgi_3, R.drawable.corgi_4, R.drawable.corgi_5,
                R.drawable.corgi_6, R.drawable.corgi_7, R.drawable.corgi_8,
                R.drawable.corgi_9, R.drawable.corgi_10, R.drawable.corgi_11, R.drawable.corgi_12
            )
            
            val loadedFrames = withContext(Dispatchers.IO) {
                resIds.mapNotNull { id ->
                    try {
                        val b = BitmapFactory.decodeResource(context.resources, id)
                        b?.let {
                            Bitmap.createScaledBitmap(it, petView.petSpriteSize, petView.petSpriteSize, true)
                        }
                    } catch (e: Exception) { null }
                }
            }
            
            frames.addAll(loadedFrames)
            isLoading = false
            petView.invalidate()
        }
    }

    override fun updateIdle(dt: Float) {
        if (isLoading) return
        time += dt
        corgiIdleTimer += dt

        when (corgiPose) {
            CorgiPose.SITTING -> {
                petView.currentFrame = 0
                petView.animOffsetY = sin(time * 2.0f) * 3f // Más suave
                if (corgiIdleTimer > 4f) {
                    corgiPose = CorgiPose.STANDING
                    corgiIdleTimer = 0f
                }
            }
            CorgiPose.STANDING -> {
                petView.currentFrame = 1
                petView.animOffsetX = sin(time * 3.0f) * 2f
                if (corgiIdleTimer > 2f) {
                    corgiPose = CorgiPose.SITTING
                    corgiIdleTimer = 0f
                }
            }
            else -> { petView.currentFrame = 0 }
        }

        if (Random.nextFloat() < 0.005f) {
            petView.showBubble(listOf("¡Guau!", "❤️", "🦴", "🐾").random())
        }
    }

    override fun updateDrag(dt: Float) {
        time += dt
        petView.animRotation = sin(time * 20f) * 12f
    }

    override fun updateFalling(dt: Float) {
        petView.currentFrame = 5
        petView.animRotation += dt * 360f // Gira al caer
    }

    override fun updateJumping(dt: Float) {
        petView.currentFrame = 5
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        petView.showBubble("❤️")
        petView.playHaptic(40)
    }

    override fun updateInteracting(dt: Float) {
        when {
            dt < 0.3f -> {
                petView.currentFrame = 7
                petView.animScaleY = 0.90f
            }
            dt < 0.6f -> { petView.currentFrame = 8; petView.animOffsetY = -10f }
            dt < 1.2f -> {
                petView.currentFrame = 9
                petView.animScaleY = 1.15f
            }
            dt < 2.0f -> {
                petView.currentFrame = 7
                petView.animScaleY = 1f
            }
            else -> {
                petView.state = PetState.IDLE
                reset()
            }
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {
        if (isLoading || frames.isEmpty()) return
        
        val frameIdx = petView.currentFrame.coerceIn(0, frames.size - 1)
        val bitmap = frames[frameIdx]
        
        canvas.save()
        canvas.translate(cx + petView.animOffsetX, cy + petView.animOffsetY)
        canvas.rotate(petView.animRotation)
        canvas.scale(petView.animScaleX, petView.animScaleY)
        
        paint.alpha = (petView.animAlpha * 255).toInt()
        paint.colorFilter = petView.animColorFilter
        
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
        canvas.restore()
    }

    override fun reset() {
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
        petView.animOffsetX = 0f
        petView.animOffsetY = 0f
    }
}
