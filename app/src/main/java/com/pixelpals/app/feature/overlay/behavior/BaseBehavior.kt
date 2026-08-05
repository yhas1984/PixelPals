package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.View
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.status.PetMood
import org.json.JSONObject
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min

/**
 * BaseBehavior — Motor de movimiento y comportamiento.
 */
abstract class BaseBehavior(
    protected val bridge: PetViewBridge,
    protected open val random: PetRandom
) : PetBehavior {

    protected var time: Float = 0f
    protected var interactionTimer: Float = 0f 

    // Lista que soporta frames nulos para no perder el orden de los índices
    protected val frames = mutableListOf<Bitmap?>()
    protected val spriteFrameRects = mutableListOf<Rect>()
    protected var spriteSheetBitmap: Bitmap? = null
    protected var spriteSheetSpec: PetAtlasSpec? = null
    protected var spriteBleedInsetPx: Int = 2
    protected var spriteFilterBitmap: Boolean = false
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    protected val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    protected var isLoading = true
    protected var velX = 0f
    protected var velY = 0f
    protected var targetX = 0f
    protected var targetY = 0f
    protected var decisionTimer = 0f
    private var lastBatteryStateKey = ""
    private var lastEnvironmentReactionAt = 0L

    // Acumuladores de movimiento sub-píxel para que, incluso con velocidades bajas,
    // el movimiento se vuelva perceptible (evita que (vel * dt).toInt() quede en 0).
    private var carryX: Float = 0f
    private var carryY: Float = 0f

    abstract val resourceIds: List<Int>

    protected fun loadFramesAsync() {
        val startedAt = System.currentTimeMillis()
        scope.launch {
            val context = (bridge as View).context
            val spriteSize = bridge.petSpriteSize

            val decodeOne: (Int) -> Bitmap? = { id ->
                if (id == 0) null
                else {
                    try {
                        val key = cacheKey(id, spriteSize)
                        val cached = FRAME_CACHE[key]
                        if (cached != null) {
                            cached
                        } else {
                            val b = BitmapFactory.decodeResource(context.resources, id)
                            val scaled = b?.let {
                                Bitmap.createScaledBitmap(it, spriteSize, spriteSize, true)
                            }
                            if (scaled != null && b !== scaled) b.recycle()
                            if (scaled != null) FRAME_CACHE[key] = scaled
                            scaled
                        }
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
            bridge.invalidate()

            // 2) Carga completa en background.
            withContext(Dispatchers.IO) {
                for (idx in initialCount until total) {
                    tmp[idx] = decodeOne(resourceIds[idx])
                }
            }
            frames.clear()
            frames.addAll(tmp)

            bridge.invalidate()
        }
    }

    protected fun loadSpriteSheetAsync(sheetResId: Int, frameRects: List<Rect>) {
        if (sheetResId == 0 || frameRects.isEmpty()) {
            isLoading = false
            return
        }

        val startedAt = System.currentTimeMillis()
        isLoading = true
        scope.launch {
            val context = (bridge as View).context
            val loadedSheet = withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeResource(context.resources, sheetResId)
                } catch (_: Exception) {
                    null
                }
            }

            spriteSheetBitmap = loadedSheet
            spriteSheetSpec = null
            spriteFrameRects.clear()
            spriteFrameRects.addAll(frameRects)
            spriteBleedInsetPx = 2
            spriteFilterBitmap = false
            isLoading = loadedSheet == null

            bridge.invalidate()
        }
    }

    protected fun loadSpriteSheetAssetAsync(
        specAssetPath: String,
        onLoaded: ((PetAtlasSpec) -> Unit)? = null
    ) {
        val startedAt = System.currentTimeMillis()
        isLoading = true
        scope.launch {
            val context = (bridge as View).context
            val result = withContext(Dispatchers.IO) {
                try {
                    val specJson = context.assets.open(specAssetPath).bufferedReader().use { it.readText() }
                    val spec = PetAtlasSpec.fromJson(JSONObject(specJson))
                    // Cache de atlas por ruta: cambiar de pet no re-decodifica el PNG grande.
                    val bitmap = ATLAS_CACHE.getOrPut(spec.atlasPath) {
                        context.assets.open(spec.atlasPath).use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }
                    Triple(spec, bitmap, null as Exception?)
                } catch (e: Exception) {
                    Triple(null, null, e)
                }
            }

            val spec = result.first
            val loadedSheet = result.second
            val error = result.third

            spriteSheetSpec = spec
            spriteSheetBitmap = loadedSheet
            spriteFrameRects.clear()
            if (spec != null) {
                spriteFrameRects.addAll(buildFrameRects(spec))
                spriteBleedInsetPx = spec.renderHints.recommendedBleedInsetPx.coerceAtLeast(0)
                spriteFilterBitmap = spec.renderHints.filterBitmap
            }
            isLoading = loadedSheet == null || spec == null

            if (spec != null && loadedSheet != null) {
                onLoaded?.invoke(spec)
            }
            bridge.invalidate()
        }
    }

    private fun buildFrameRects(spec: PetAtlasSpec): List<Rect> {
        return List(spec.frameCount) { index ->
            val row = index / spec.columns
            val col = index % spec.columns
            Rect(
                col * spec.frameWidth,
                row * spec.frameHeight,
                (col + 1) * spec.frameWidth,
                (row + 1) * spec.frameHeight
            )
        }
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        sanitizeMotion()
        time += dt
        
        updateDecision(dt)
        applyMovement(dt)
    }

    protected open fun updateDecision(dt: Float) {
        decisionTimer -= dt
        if (decisionTimer <= 0) {
            targetX = random.nextInt(50, (bridge.screenWidth - bridge.petSpriteSize - 50).coerceAtLeast(51)).toFloat()
            targetY = random.nextInt(100, (bridge.screenHeight - bridge.petSpriteSize - 200).coerceAtLeast(101)).toFloat()

            val dx = targetX - bridge.windowX
            val dy = targetY - bridge.windowY
            val dist = kotlin.math.sqrt(dx*dx + dy*dy)

            if (dist > 10) {
                val speed = getBaseSpeed()
                velX = (dx / dist) * speed
                velY = (dy / dist) * speed
            }

            decisionTimer = random.nextFloat() * 3f + 1f
        }
    }

    protected open fun getBaseSpeed(): Float = 100f

    protected fun currentMood(): PetMood = bridge.petStatus.mood

    protected fun moodSpeedMultiplier(): Float {
        return when (currentMood()) {
            PetMood.EXCITED -> 1.12f
            PetMood.HAPPY -> 1.0f
            PetMood.BORED -> 0.94f
            PetMood.HUNGRY -> 0.9f
            PetMood.DIRTY -> 0.92f
            PetMood.SLEEPY -> 0.84f
        }
    }

    protected fun applyMovement(dt: Float) {
        val params = bridge.getWindowParams() ?: return

        val adjustedDt = dt * moodSpeedMultiplier()
        carryX += velX * adjustedDt
        carryY += velY * adjustedDt

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

        clampWindowParams(params, minX, maxX, minY, maxY)
        bridge.updateWindowLayout(params)
    }

    protected fun clampWindowParams(
        params: android.view.WindowManager.LayoutParams,
        minX: Int = 0,
        maxX: Int = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0),
        minY: Int = 50,
        maxY: Int = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY)
    ) {
        params.x = params.x.coerceIn(minX, maxX)
        params.y = params.y.coerceIn(minY, maxY)
    }

    protected fun sanitizeMotion() {
        if (!velX.isFinite()) velX = 0f
        if (!velY.isFinite()) velY = 0f
        if (!targetX.isFinite()) targetX = 0f
        if (!targetY.isFinite()) targetY = 0f
        if (!decisionTimer.isFinite()) decisionTimer = 0f
    }

    override fun updateDrag(dt: Float) {
        time += dt
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
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

    override fun onBatteryStatusChanged(percent: Int, isCharging: Boolean) {
        val key = if (isCharging) {
            "charging"
        } else if (percent <= LOW_BATTERY_THRESHOLD) {
            "low_$percent"
        } else {
            "ok"
        }
        if (key == lastBatteryStateKey) return
        lastBatteryStateKey = key
        when {
            isCharging -> maybeShowEnvironmentBubble("⚡")
            percent <= LOW_BATTERY_THRESHOLD -> maybeShowEnvironmentBubble("🔋")
        }
    }

    override fun onAirplaneModeChanged(isAirplane: Boolean) {
        if (isAirplane) maybeShowEnvironmentBubble("✈️")
    }

    override fun onKeyboardVisibilityChanged(visible: Boolean, height: Int) {
        if (!visible) return
        val params = bridge.getWindowParams() ?: return
        val minY = 50
        val maxY = (bridge.screenHeight - height - bridge.petSpriteSize - 100).coerceAtLeast(minY)
        if (params.y > maxY) {
            params.y = maxY
            bridge.updateWindowLayout(params)
        }
        bridge.showBubble("🤫")
    }

    protected fun maybeShowEnvironmentBubble(emoji: String) {
        val now = System.currentTimeMillis()
        if (now - lastEnvironmentReactionAt < ENVIRONMENT_REACTION_COOLDOWN_MS) return
        lastEnvironmentReactionAt = now
        bridge.showBubble(emoji)
        bridge.playHaptic(20)
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
        val moodAlphaMultiplier = when (currentMood()) {
            PetMood.SLEEPY -> 0.92f
            PetMood.DIRTY -> 0.95f
            else -> 1f
        }
        paint.alpha = (bridge.animAlpha.coerceIn(0f, 1f) * moodAlphaMultiplier * 255).toInt()
        paint.colorFilter = bridge.animColorFilter ?: bridge.cosmeticColorFilter ?: moodColorFilter()

        val frameIdx = bridge.currentFrame.coerceAtLeast(0)
        val bitmap = if (frames.isNotEmpty()) {
            frames[frameIdx.coerceIn(0, frames.size - 1)]
        } else {
            null
        }
        val spriteSheet = spriteSheetBitmap
        val srcRect = if (spriteSheet != null && spriteFrameRects.isNotEmpty()) {
            spriteFrameRects[frameIdx.coerceIn(0, spriteFrameRects.size - 1)]
        } else {
            null
        }

        if (isLoading || (bitmap == null && (spriteSheet == null || srcRect == null))) return

        canvas.save()
        canvas.translate(cx + bridge.renderOffsetX, cy + bridge.renderOffsetY)
        canvas.rotate(bridge.renderRotation)
        canvas.scale(bridge.renderScaleX, bridge.renderScaleY)
        when {
            bitmap != null -> canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
            spriteSheet != null && srcRect != null -> {
                val halfSize = bridge.petSpriteSize / 2f
                val dstRect = RectF(-halfSize, -halfSize, halfSize, halfSize)
                val bleedInset = spriteBleedInsetPx.coerceAtLeast(0)
                val insetSrcRect = Rect(
                    (srcRect.left + bleedInset).coerceAtMost(srcRect.right),
                    (srcRect.top + bleedInset).coerceAtMost(srcRect.bottom),
                    (srcRect.right - bleedInset).coerceAtLeast(srcRect.left),
                    (srcRect.bottom - bleedInset).coerceAtLeast(srcRect.top)
                )
                val previousFilter = paint.isFilterBitmap
                paint.isFilterBitmap = spriteFilterBitmap
                canvas.drawBitmap(spriteSheet, insetSrcRect, dstRect, paint)
                paint.isFilterBitmap = previousFilter
            }
        }
        canvas.restore()
    }

    private fun moodColorFilter(): ColorMatrixColorFilter? {
        return when (currentMood()) {
            PetMood.DIRTY -> {
                val matrix = ColorMatrix().apply { setSaturation(0.85f) }
                ColorMatrixColorFilter(matrix)
            }
            PetMood.SLEEPY -> {
                val matrix = ColorMatrix().apply { setScale(0.92f, 0.92f, 1.02f, 1f) }
                ColorMatrixColorFilter(matrix)
            }
            else -> null
        }
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
        bridge.animAlpha = 1f
    }

    override fun destroy() {
        scope.cancel()
        // Los frames cacheados (FRAME_CACHE) son compartidos entre instancias de pet:
        // no se reciclan aquí, los reutiliza la siguiente mascota (evita re-decodificar).
        frames.filterNotNull().distinct().forEach { bitmap ->
            if (!FRAME_CACHE.containsValue(bitmap) && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        frames.clear()
        if (spriteSheetBitmap != null && !ATLAS_CACHE.containsValue(spriteSheetBitmap)) {
            spriteSheetBitmap?.recycle()
        }
        spriteSheetBitmap = null
    }

    private companion object {
        const val LOW_BATTERY_THRESHOLD = 20
        const val ENVIRONMENT_REACTION_COOLDOWN_MS = 8L * 60L * 1000L

        /** Cache de frames escalados por (drawableResId, petSpriteSize). */
        val FRAME_CACHE = java.util.concurrent.ConcurrentHashMap<Long, Bitmap>()

        /** Cache de atlases decodificados por ruta de asset. */
        val ATLAS_CACHE = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

        fun cacheKey(resId: Int, size: Int): Long = (resId.toLong() shl 32) or size.toLong()
    }
}
