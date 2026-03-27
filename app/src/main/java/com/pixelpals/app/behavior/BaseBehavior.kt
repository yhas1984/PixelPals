package com.pixelpals.app.behavior

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.view.View
import com.pixelpals.app.PetState
import org.json.JSONObject
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

    // Acumuladores de movimiento sub-píxel para que, incluso con velocidades bajas,
    // el movimiento se vuelva perceptible (evita que (vel * dt).toInt() quede en 0).
    private var carryX: Float = 0f
    private var carryY: Float = 0f

    abstract val resourceIds: List<Int>

    init {
        loadFramesAsync()
    }

    private fun loadFramesAsync() {
        val startedAt = System.currentTimeMillis()
        scope.launch {
            val context = (bridge as View).context

            val decodeOne: (Int) -> Bitmap? = { id ->
                if (id == 0) null
                else {
                    try {
                        val b = BitmapFactory.decodeResource(context.resources, id)
                        b?.let { Bitmap.createScaledBitmap(it, bridge.petSpriteSize, bridge.petSpriteSize, true) }
                    } catch (_: Exception) { null }
                }
            }

            val total = resourceIds.size
            // Para mascotas con pocos frames (ej. Bloop 0..8) cargamos todo de golpe para que
            // las transiciones (incluida la transparencia) no ocurran con frames aun nulos.
            val initialCount = if (total <= 9) total else minOf(8, total)
            val tmp = MutableList<Bitmap?>(total) { null }

            // 1) Carga inicial para que el pet no se vea "en blanco" mientras decodifica todo.
            val initialElapsed = withContext(Dispatchers.IO) {
                val loaded = resourceIds.take(initialCount).map { id -> decodeOne(id) }
                loaded to (System.currentTimeMillis() - startedAt)
            }
            for (i in 0 until initialCount) tmp[i] = initialElapsed.first[i]
            frames.clear()
            frames.addAll(tmp)
            isLoading = false
            try {
                val payload = JSONObject().apply {
                    put("sessionId", "a40953")
                    put("runId", "post-fix")
                    put("hypothesisId", "H4")
                    put("location", "BaseBehavior.kt:loadFramesAsync")
                    put("message", "Carga inicial de frames completada")
                    put("data", JSONObject().apply {
                        put("behavior", this@BaseBehavior::class.java.simpleName)
                        put("resourceCount", resourceIds.size)
                        put("initialLoadedFrames", initialCount)
                        put("elapsedMs", initialElapsed.second)
                    })
                    put("timestamp", System.currentTimeMillis())
                }
                Log.i("AGENT_DEBUG", payload.toString())
            } catch (_: Exception) {}
            bridge.invalidate()

            // 2) Carga completa en background.
            withContext(Dispatchers.IO) {
                for (idx in initialCount until total) {
                    tmp[idx] = decodeOne(resourceIds[idx])
                }
            }
            frames.clear()
            frames.addAll(tmp)

            try {
                val payload = JSONObject().apply {
                    put("sessionId", "a40953")
                    put("runId", "post-fix")
                    put("hypothesisId", "H4")
                    put("location", "BaseBehavior.kt:loadFramesAsync")
                    put("message", "Carga completa de frames completada")
                    put("data", JSONObject().apply {
                        put("behavior", this@BaseBehavior::class.java.simpleName)
                        put("resourceCount", resourceIds.size)
                        put("loadedFrames", frames.count { it != null })
                        put("elapsedMs", System.currentTimeMillis() - startedAt)
                    })
                    put("timestamp", System.currentTimeMillis())
                }
                Log.i("AGENT_DEBUG", payload.toString())
            } catch (_: Exception) {}
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

        carryX += velX * dt
        carryY += velY * dt

        val moveX = carryX.toInt()
        val moveY = carryY.toInt()

        params.x += moveX
        params.y += moveY

        carryX -= moveX.toFloat()
        carryY -= moveY.toFloat()

        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)
        val minY = 50
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY)

        if (params.x < minX) {
            params.x = minX
            velX *= -1
            decisionTimer = 0f
            carryX = 0f
        } else if (params.x > maxX) {
            params.x = maxX
            velX *= -1
            decisionTimer = 0f
            carryX = 0f
        }

        if (params.y < minY) {
            params.y = minY
            velY *= -1
            decisionTimer = 0f
            carryY = 0f
        } else if (params.y > maxY) {
            params.y = maxY
            velY *= -1
            decisionTimer = 0f
            carryY = 0f
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
        bridge.state = PetState.IDLE
        reset()
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
        paint.alpha = (bridge.animAlpha.coerceIn(0f, 1f) * 255).toInt()
        paint.colorFilter = bridge.animColorFilter

        canvas.save()
        canvas.translate(cx + bridge.renderOffsetX, cy + bridge.renderOffsetY)
        canvas.rotate(bridge.renderRotation)
        canvas.scale(bridge.renderScaleX, bridge.renderScaleY)
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
        canvas.restore()
    }

    override fun reset() {
        velX = 0f
        velY = 0f
        targetX = 0f
        targetY = 0f
        decisionTimer = 0f
        carryX = 0f
        carryY = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animColorFilter = null
    }

    override fun destroy() {
        scope.cancel()
    }
}
