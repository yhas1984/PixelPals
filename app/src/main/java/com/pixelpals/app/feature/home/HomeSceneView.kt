package com.pixelpals.app.feature.home

import com.pixelpals.app.feature.care.PetDreamPainter

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.database.CompanionHomeEntity
import com.pixelpals.app.database.HomeDecorationEntity
import java.time.LocalTime
import kotlin.math.*

class HomeSceneView(context: Context) : View(context) {
    private val dreamPainter: PetDreamPainter = PetDreamPainter()
    private val painter: HomeScenePainter = HomeScenePainter()
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val actor: RectF = RectF()
    private val draggedBounds: RectF = RectF()
    private val gridBounds: Array<RectF> = Array(HomeGrid.COLUMNS * HomeGrid.ROWS) { index ->
        val column: Int = index % HomeGrid.COLUMNS
        val row: Int = index / HomeGrid.COLUMNS
        RectF(65f + column * 175f, 330f + row * 103f, 210f + column * 175f, 475f + row * 103f)
    }
    private var profile: CompanionProfile = CompanionProfiles.forPet(PetType.CORGI)
    private var hour: Int = LocalTime.now().hour
    private var lastMinute: Long = 0L
    private val preferences: CompanionPreferences = CompanionPreferences(context)
    private var locomotion: HomeLocomotion? = null
    var pet: PetType = PetType.CORGI
        private set
    private var traits = CompanionTraits.derive(PetType.CORGI, null, 0)
    var home: CompanionHomeEntity? = null
        set(value) { field = value; traits = CompanionTraits.derive(pet, value, bond) }
    var environment: HomeEnvironment = HomeEnvironment.COZY
    var placements: List<HomeDecorationEntity> = emptyList()
        set(value) { if (field != value) field = value.sortedBy { it.row } }
    var treasure: String? = null
    var isTravelling: Boolean = false
    var isEditing: Boolean = false
        set(value) {
            field = value
            if (!value) { pendingPlacement = null; dragged = null }
            onEditingChanged?.invoke(value)
            invalidate()
        }
    var onEditingChanged: ((Boolean) -> Unit)? = null
    var showPet: Boolean = true
    var energy: Int = 75
    var isUnwell: Boolean = false
    var bond: Int = 0
        set(value) { field = value; traits = CompanionTraits.derive(pet, home, value) }
    private val cosmetics: SceneCosmeticPainter = SceneCosmeticPainter()
    private var cosmeticFilter: ColorFilter? = null
    var cosmeticEffect: com.pixelpals.app.data.catalog.CosmeticEffect? = null
        set(value) {
            if (field == value) return
            field = value
            cosmeticFilter = (value as? com.pixelpals.app.data.catalog.CosmeticEffect.TintEffect)?.let { ColorMatrixColorFilter(it.toColorMatrix()) }
            invalidate()
        }
    var onObject: ((Decoration) -> Unit)? = null
    var onPet: (() -> Unit)? = null
    var onPlace: ((String, Int, Int) -> Unit)? = null
    private var pendingPlacement: String? = null
    fun beginPlacement(id: String): Unit {
        isEditing = true
        pendingPlacement = id
        dragged = HomeDecorationEntity(pet.name.lowercase(), id, 2, 1)
        dragX = 500f; dragY = 500f
        invalidate()
    }
    private var dragged: HomeDecorationEntity? = null
    private var dragX: Float = 0f
    private var dragY: Float = 0f
    private var didMove: Boolean = false
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var activeTime: Long = 0L
    private val deviceRest = com.pixelpals.app.core.rest.DeviceRestState(context)
    private var motion: CompanionMotion = CompanionMotion()
    internal var reviewSeed: Int? = null
    internal var previewTimeScale: Float = 1f
    internal var previewReducedMotion: Boolean = false
    private val isMotionReduced: Boolean get() = previewReducedMotion || preferences.reducedMotion || !ValueAnimator.areAnimatorsEnabled()
    private var describedDream: Boolean? = null
    private var lastFrame: Long = 0L
    private var running: Boolean = false
    private val tick: Runnable = object : Runnable {
        override fun run(): Unit {
            running = false
            if (!isAttachedToWindow || !isShown) { lastFrame = 0; return }
            val now: Long = SystemClock.uptimeMillis()
            if (lastFrame > 0 && now - lastFrame < 16) { schedule(); return }
            val minute: Long = System.currentTimeMillis() / 60_000
            if (minute != lastMinute) { hour = LocalTime.now().hour; lastMinute = minute }
            val delta: Long = if (lastFrame > 0) (now - lastFrame).coerceAtMost(100) else 0
            if (showPet && !isEditing && !isTravelling) advanceScene((delta * previewTimeScale.coerceIn(.1f, 1f)).toLong())
            lastFrame = now
            val dreaming: Boolean = motion.activity == CompanionActivity.REST && !isTravelling && !isEditing && showPet &&
                (isMotionReduced || motion.elapsed >= 1.4f)
            if (describedDream != dreaming) {
                describedDream = dreaming
                contentDescription = context.getString(if (dreaming) R.string.home_scene_dreaming else R.string.home_scene_description,
                    context.getString(pet.displayNameResId))
            }
            invalidate(); schedule()
        }
    }

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.home_scene_description, context.getString(pet.displayNameResId))
    }

    /** Advances only visible scene time; also used by deterministic Android render reviews. */
    /** Selects a path for frame review without waiting for a random autonomous choice. */
    internal fun requestMotionReview(intent: CompanionIntent): Unit = motion.beginIntent(intent)

    internal val renderedActorSize: Float get() = actor.width()

    internal val activity: CompanionActivity get() = motion.activity

    internal fun advanceScene(delta: Long): Unit {
        activeTime += delta.coerceIn(0, 100)
        if (reviewSeed == null && motion.advanceScheduledRest(deviceRest.shouldRest(preferences.restSchedule), delta / 1000f)) return
        if (isMotionReduced) { motion.settleWithoutMovement(energy <= 25 || isUnwell); return }
        val toy = placements.firstOrNull { it.decorationId == home?.favoriteObject && DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }
            ?: placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }
        val bed = placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.BED }
        val toyCenter: Float = toy?.let { objectBounds(it).centerX() } ?: 500f
        val toyStand: Float = if (toy == null) 500f else toyCenter + if (toyCenter < 500f) 100f else -100f
        motion.context = CompanionIntentContext(energy, isUnwell, toy != null, bed != null, profile.curiosity,
            ((home?.playCount ?: 0) / 30f).coerceIn(0f, 1f), (.3f + bond / 140f).coerceIn(0f, 1f))
        motion.advance(delta / 1000f, toyStand,
            bed?.let { objectBounds(it).centerX() } ?: 500f, profile.tempo * traits.tempo * traits.initiative, bond,
            toy?.let { objectBounds(it).bottom - 30f } ?: 650f, bed?.let { objectBounds(it).bottom - 30f } ?: 650f, toy?.let { toyCenter < toyStand })
    }

    suspend fun loadPet(type: PetType): Unit {
        pet = type
        describedDream = null
        profile = CompanionProfiles.forPet(type)
        traits = CompanionTraits.derive(type, home, bond)
        motion = CompanionMotion(CompanionIntentSelector(reviewSeed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default))
        locomotion = null
        val walk: HomeLocomotion = HomeLocomotion.load(context, type)
        if (pet != type) return
        locomotion = walk
        contentDescription = context.getString(R.string.home_scene_description, context.getString(type.displayNameResId))
        invalidate(); schedule()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Unit {
        val width: Int = MeasureSpec.getSize(widthMeasureSpec)
        val height: Int = (width * .76f).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    private fun objectBounds(item: HomeDecorationEntity): RectF = gridBounds[(item.row * HomeGrid.COLUMNS + item.column).coerceIn(0, gridBounds.lastIndex)]

    override fun onDraw(canvas: Canvas): Unit {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        canvas.save(); canvas.scale(width / 1000f, height / 760f)
        canvas.clipRect(0f, 0f, 1000f, 760f)
        painter.drawBackground(canvas, environment, hour)
        if (isEditing) drawGrid(canvas)
        val actorVisible = showPet && !isTravelling
        drawDecorations(canvas) { !actorVisible || objectBounds(it).bottom - 30f <= motion.y }
        if (actorVisible) drawCompanion(canvas)
        if (actorVisible) drawDecorations(canvas) { objectBounds(it).bottom - 30f > motion.y }
        dragged?.let { item -> DecorationCatalog.find(item.decorationId)?.let {
            val slot: Pair<Int, Int> = placementSlot(dragX, dragY)
            draggedBounds.set(gridBounds[slot.second * HomeGrid.COLUMNS + slot.first])
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = if (canPlace(item.decorationId, slot)) 0xff426b56.toInt() else 0xffac3e42.toInt()
            canvas.drawRoundRect(draggedBounds, 18f, 18f, paint)
            paint.style = Paint.Style.FILL
            painter.drawObject(canvas, it, draggedBounds, treasure)
        } }
        canvas.restore()
    }

    private fun placementSlot(x: Float, y: Float): Pair<Int, Int> =
        ((x - 137.5f) / 175f).roundToInt().coerceIn(0, HomeGrid.COLUMNS - 1) to
            ((y - 402.5f) / 103f).roundToInt().coerceIn(0, HomeGrid.ROWS - 1)

    private fun canPlace(id: String, slot: Pair<Int, Int>): Boolean = placements.none {
        it.decorationId != id && it.column == slot.first && it.row == slot.second
    }

    private inline fun drawDecorations(canvas: Canvas, include: (HomeDecorationEntity) -> Boolean): Unit {
        placements.forEach { position ->
            if (include(position) && position.decorationId != dragged?.decorationId) {
                DecorationCatalog.find(position.decorationId)?.let { drawDecoration(canvas, it, position) }
            }
        }
    }

    private fun drawDecoration(canvas: Canvas, item: Decoration, position: HomeDecorationEntity): Unit {
        val bounds: RectF = objectBounds(position)
        val activeToy: HomeDecorationEntity? = placements.firstOrNull { it.decorationId == home?.favoriteObject && DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }
            ?: placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }
        canvas.save()
        if (!isMotionReduced && !isEditing && motion.activity == CompanionActivity.PLAY && !motion.isTurning && position == activeToy) {
            val nudge: Float = locomotion?.toyResponse(motion.elapsed) ?: 0f
            val direction: Float = if (motion.x < bounds.centerX()) 1f else -1f
            canvas.translate(nudge * 24f * direction, -nudge * 14f)
            canvas.rotate(nudge * 25f * direction, bounds.centerX(), bounds.bottom - 30f)
        }
        painter.drawObject(canvas, item, bounds, treasure)
        canvas.restore()
    }

    private fun drawCompanion(canvas: Canvas): Unit {
        val sprites: HomeLocomotion = locomotion ?: return
        val reduced: Boolean = isMotionReduced || isEditing
        val x: Float = motion.x
        val size: Float = 290f
        val gait: HomeGait = HomeGait.forPet(pet)
        val phase: Float = motion.distanceTravelled / 105f * (2f * PI.toFloat())
        val amount: Float = if (reduced) 0f else (motion.speed / 75f).coerceIn(0f, 1f)
        val lift: Float = if (reduced) 0f else if (gait.isFloating && motion.activity != CompanionActivity.REST)
            7f + sin(activeTime / 1000f * 2f) * 3f else abs(sin(phase)) * gait.bounce * amount
        val preparation: Float = if (!reduced && motion.isPreparing) sin(motion.elapsed / CompanionMotion.ANTICIPATION_SECONDS * PI.toFloat()) else 0f
        val breath: Float = if (reduced) 0f else sin(activeTime / 1000f * 2f) * 1.1f
        actor.set(x - size / 2, motion.y - size, x + size / 2, motion.y)
        paint.color = 0x25736954
        val shadowWidth: Float = size * .21f * (1f - lift / 100f)
        canvas.drawOval(x - shadowWidth, motion.y - 21f, x + shadowWidth, motion.y - 7f, paint)
        paint.color = Color.WHITE
        paint.colorFilter = cosmeticFilter
        canvas.save()
        canvas.translate(0f, -lift)
        canvas.rotate(if (reduced) 0f else sin(phase) * gait.sway * amount, x, motion.y)
        canvas.scale(1f + preparation * .035f, 1f - preparation * .035f + breath / size, x, motion.y)
        if (if (motion.isTurning) motion.turnFromFacingLeft else motion.isFacingLeft) canvas.scale(-1f, 1f, x, motion.y)
        sprites.draw(canvas, paint, actor, motion, reduced)
        canvas.restore()
        paint.colorFilter = null
        cosmetics.draw(canvas, cosmeticEffect, actor, if (reduced) 0f else activeTime / 1000f)
        if (motion.activity == CompanionActivity.REST && !isEditing && (reduced || motion.elapsed >= 1.4f))
            dreamPainter.draw(canvas, x, motion.y, size, motion.elapsed, reduced)
    }

    private fun drawGrid(canvas: Canvas): Unit {
        paint.color = 0x88708060.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        for (row: Int in 0 until HomeGrid.ROWS) for (column: Int in 0 until HomeGrid.COLUMNS) {
            canvas.drawRoundRect(gridBounds[row * HomeGrid.COLUMNS + column], 16f, 16f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return false
        val x: Float = event.x / width * 1000
        val y: Float = event.y / height * 760
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; didMove = false
                dragged = pendingPlacement?.let { HomeDecorationEntity(pet.name.lowercase(), it, 2, 1) } ?: if (isEditing) placements.lastOrNull { objectBounds(it).contains(x, y) } else null
                dragX = x; dragY = y
                if (dragged != null) parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(x - downX, y - downY) > 20f) didMove = true
                if (dragged != null) { dragX = x; dragY = y; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                val item: HomeDecorationEntity? = dragged
                dragged = null
                parent?.requestDisallowInterceptTouchEvent(false)
                if (item != null && (didMove || pendingPlacement != null)) {
                    val slot: Pair<Int, Int> = placementSlot(x, y)
                    if (canPlace(item.decorationId, slot)) {
                        pendingPlacement = null
                        onPlace?.invoke(item.decorationId, slot.first, slot.second)
                    } else {
                        android.widget.Toast.makeText(context, R.string.home_occupied, android.widget.Toast.LENGTH_SHORT).show()
                        announceForAccessibility(context.getString(R.string.home_occupied))
                    }
                } else if (!didMove) {
                    val hit: HomeDecorationEntity? = placements.lastOrNull { objectBounds(it).contains(x, y) }
                    val definition: Decoration? = hit?.let { DecorationCatalog.find(it.decorationId) }
                    if (definition != null) onObject?.invoke(definition) else if (!isTravelling) performClick()
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { dragged = null; parent?.requestDisallowInterceptTouchEvent(false); invalidate() }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); if (!isEditing && !isTravelling && showPet) onPet?.invoke(); return true }
    fun pause(): Unit { removeCallbacks(tick); running = false; lastFrame = 0 }
    fun resume(): Unit { schedule() }
    private fun schedule(): Unit {
        if (running || !isAttachedToWindow || !isShown) return
        running = true
        if (!showPet) postDelayed(tick, 60_000)
        else if (isMotionReduced || isTravelling) postDelayed(tick, 1000)
        else postOnAnimation(tick)
    }
    override fun onAttachedToWindow(): Unit { super.onAttachedToWindow(); schedule() }
    override fun onWindowVisibilityChanged(visibility: Int): Unit { super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) schedule() else pause() }
    override fun onDetachedFromWindow(): Unit { pause(); super.onDetachedFromWindow() }
}
