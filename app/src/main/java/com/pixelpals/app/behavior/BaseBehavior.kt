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
 * Ahora incluye lógica para navegar por TODA la pantalla.
 */
abstract class BaseBehavior(
    protected val bridge: PetViewBridge
) : PetBehavior {

    protected var time: Float = 0f
    protected val frames = mutableListOf<Bitmap>()
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    protected val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    protected var isLoading = true

    // Parámetros de movimiento
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
                resourceIds.mapNotNull { id ->
                    try {
                        val b = BitmapFactory.decodeResource(context.resources, id)
                        b?.let {
                            Bitmap.createScaledBitmap(it, bridge.petSpriteSize, bridge.petSpriteSize, true)
                        }
                    } catch (e: Exception) { null }
                }
            }
            if (loadedFrames.isNotEmpty()) {
                frames.clear()
                frames.addAll(loadedFrames)
                isLoading = false
                bridge.invalidate()
            }
        }
    }

    override fun updateIdle(dt: Float) {
        time += dt
        if (frames.isNotEmpty()) {
            // Reducción del 30% de velocidad (8fps -> 5.6fps)
            bridge.currentFrame = (time * 5.6f).toInt() % frames.size
        }
        
        // Lógica de navegación básica por defecto
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
            
            decisionTimer = Random.nextFloat() * 4f + 2f
        }
    }

    protected open fun getBaseSpeed(): Float = 100f

    protected fun applyMovement(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        
        params.x += (velX * dt).toInt()
        params.y += (velY * dt).toInt()

        if (params.x < 0 || params.x > bridge.screenWidth - bridge.petSpriteSize) velX *= -1
        if (params.y < 50 || params.y > bridge.screenHeight - bridge.petSpriteSize - 100) velY *= -1

        if (velX > 5) bridge.animScaleX = -1f
        else if (velX < -5) bridge.animScaleX = 1f

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
        bridge.trackInteraction()
        bridge.playHaptic(50)
    }

    override fun updateInteracting(dt: Float) {
        if (dt > 1.5f) {
            bridge.state = PetState.IDLE
            reset()
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {
        if (isLoading || frames.isEmpty()) return
        val frameIdx = bridge.currentFrame.coerceIn(0, frames.size - 1)
        val bitmap = frames[frameIdx]
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
