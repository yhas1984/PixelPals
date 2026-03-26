package com.pixelpals.app.behavior

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.pixelpals.app.PetState
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.random.Random

/**
 * BaseBehavior — Motor de movimiento y comportamiento.
 */
abstract class BaseBehavior(
    protected val bridge: PetViewBridge
) : PetBehavior {

    protected var time: Float = 0f
    protected var interactionTimer: Float = 0f 
    
    // Lista que soporta frames nulos para no perder el orden de los índices
    protected val frames = mutableListOf<Bitmap?>()
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    protected val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    protected var isLoading = true

    protected var velX = 0f
    protected var velY = 0f
    protected var targetX = 0f
    protected var targetY = 0f
    protected var decisionTimer = 0f

    abstract val resourceIds: List<Int>

    init {
        loadFramesAsync()
    }

    private fun loadFramesAsync() {
        scope.launch {
            val context = (bridge as View).context
            val loadedFrames = withContext(Dispatchers.IO) {
                resourceIds.map { id ->
                    if (id == 0) null
                    else {
                        try {
                            val b = BitmapFactory.decodeResource(context.resources, id)
                            if (b != null) {
                                Bitmap.createScaledBitmap(b, bridge.petSpriteSize, bridge.petSpriteSize, true)
                            } else null
                        } catch (e: Exception) { null }
                    }
                }
            }
            frames.clear()
            frames.addAll(loadedFrames)
            isLoading = false
            bridge.invalidate()
        }
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt
        
        updateDecision(dt)
        applyMovement(dt)
    }

    protected open fun updateDecision(dt: Float) {
        decisionTimer -= dt
        if (decisionTimer <= 0) {
            targetX = Random.nextInt(50, bridge.screenWidth - bridge.petSpriteSize - 50).toFloat()
            targetY = Random.nextInt(100, bridge.screenHeight - bridge.petSpriteSize - 200).toFloat()
            
            val dx = targetX - bridge.windowX
            val dy = targetY - bridge.windowY
            val dist = kotlin.math.sqrt(dx*dx + dy*dy)
            
            if (dist > 10) {
                val speed = getBaseSpeed()
                velX = (dx / dist) * speed
                velY = (dy / dist) * speed
            }
            
            decisionTimer = Random.nextFloat() * 3f + 1f 
        }
    }

    protected open fun getBaseSpeed(): Float = 100f 

    protected fun applyMovement(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        
        params.x += (velX * dt).toInt()
        params.y += (velY * dt).toInt()

        if (params.x < 0 || params.x > bridge.screenWidth - bridge.petSpriteSize) {
            velX *= -1
            decisionTimer = 0f
        }
        if (params.y < 50 || params.y > bridge.screenHeight - bridge.petSpriteSize - 100) {
            velY *= -1
            decisionTimer = 0f
        }

        bridge.updateWindowLayout(params)
    }

    override fun updateDrag(dt: Float) {
        time += dt
        bridge.animRotation = sin(time * 20f) * 15f
        velX = 0f
        velY = 0f
    }

    override fun updateFalling(dt: Float) {
        time += dt
        bridge.animRotation += dt * 500f
    }

    override fun updateJumping(dt: Float) {
        time += dt
        bridge.animScaleY = 1.2f
        bridge.animScaleX = 0.8f
    }

    override fun updateAutonomous(dt: Float) {
        applyMovement(dt)
    }

    override fun onInteract() {
        bridge.state = PetState.INTERACTING
        interactionTimer = 0f 
        bridge.trackInteraction()
        bridge.playHaptic(50)
    }

    override fun updateInteracting(dt: Float) {
        interactionTimer += dt 
        
        if (interactionTimer > 3.0f) {
            bridge.state = PetState.IDLE
            reset()
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {
        if (isLoading || frames.isEmpty()) return
        val frameIdx = bridge.currentFrame.coerceIn(0, frames.size - 1)
        val bitmap = frames[frameIdx] ?: return // Si el frame no existe, no dibujar nada o ignorar

        canvas.save()
        canvas.translate(cx + bridge.animOffsetX, cy + bridge.animOffsetY)
        canvas.rotate(bridge.animRotation)
        canvas.scale(bridge.animScaleX, bridge.animScaleY)
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
        canvas.restore()
    }

    override fun reset() {
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }
}
