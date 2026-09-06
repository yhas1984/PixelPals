package com.pixelpals.app.feature.home

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.pixelpals.app.R
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.database.CompanionHomeEntity
import com.pixelpals.app.database.HomeDecorationEntity
import com.pixelpals.app.feature.care.CarePoseLoader
import com.pixelpals.app.feature.care.CarePosePack
import java.time.LocalTime
import kotlin.math.*

class HomeSceneView(context: Context) : View(context) {
    private val painter: HomeScenePainter = HomeScenePainter()
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source: Rect = Rect()
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
    private var pack: CarePosePack? = null
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
    var showPet: Boolean = true
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
    private var dragged: HomeDecorationEntity? = null
    private var dragX: Float = 0f
    private var dragY: Float = 0f
    private var didMove: Boolean = false
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var activeTime: Long = 0L
    private var motion: CompanionMotion = CompanionMotion()
    private var lastFrame: Long = 0L
    private var running: Boolean = false
    private val tick: Runnable = object : Runnable {
        override fun run(): Unit {
            running = false
            if (!isAttachedToWindow || !isShown) { lastFrame = 0; return }
            val now: Long = SystemClock.uptimeMillis()
            val minute: Long = System.currentTimeMillis() / 60_000
            if (minute != lastMinute) { hour = LocalTime.now().hour; lastMinute = minute }
            val delta: Long = if (lastFrame > 0) (now - lastFrame).coerceAtMost(100) else 0
            activeTime += delta
            if (!preferences.reducedMotion && ValueAnimator.areAnimatorsEnabled() && showPet && !isEditing && !isTravelling) {
                val toy: HomeDecorationEntity? = placements.firstOrNull { it.decorationId == home?.favoriteObject }
                    ?: placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }
                val bed: HomeDecorationEntity? = placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.BED }
                val learned: Float = traits.tempo
                motion.advance(delta / 1000f, toy?.let { objectBounds(it).centerX() } ?: 500f,
                    bed?.let { objectBounds(it).centerX() } ?: 500f, profile.tempo * learned * traits.initiative, bond,
                    toy?.let { objectBounds(it).bottom - 30f } ?: 650f, bed?.let { objectBounds(it).bottom - 30f } ?: 650f)
            }
            lastFrame = now
            invalidate(); schedule()
        }
    }

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.home_scene_description, context.getString(pet.displayNameResId))
    }

    suspend fun loadPet(type: PetType): Unit {
        pet = type
        profile = CompanionProfiles.forPet(type)
        traits = CompanionTraits.derive(type, home, bond)
        motion = CompanionMotion()
        pack = null
        locomotion = null
        val walk = HomeLocomotion.load(context, type)
        val loaded: CarePosePack = CarePoseLoader.load(context.assets, type)
        if (pet != type) return
        pack = loaded
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
        placements.forEach { position ->
            val item: Decoration = DecorationCatalog.find(position.decorationId) ?: return@forEach
            if (position.decorationId != dragged?.decorationId) painter.drawObject(canvas, item, objectBounds(position), treasure)
        }
        if (showPet && !isTravelling) drawCompanion(canvas)
        dragged?.let { item -> DecorationCatalog.find(item.decorationId)?.let {
            draggedBounds.set(dragX - 72f, dragY - 100f, dragX + 72f, dragY + 45f)
            painter.drawObject(canvas, it, draggedBounds, treasure)
        } }
        canvas.restore()
    }

    private fun drawCompanion(canvas: Canvas): Unit {
        val poses: CarePosePack = pack ?: return
        val reduced: Boolean = preferences.reducedMotion || !ValueAnimator.areAnimatorsEnabled()
        val learnedTempo: Float = traits.tempo
        val time: Float = if (reduced || isEditing) 0f else activeTime / 1000f * profile.tempo * learnedTempo
        val bed: HomeDecorationEntity? = placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.BED }
        val toy: HomeDecorationEntity? = placements.firstOrNull { it.decorationId == home?.favoriteObject }
            ?: placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }
        val x: Float = motion.x
        val size: Float = (290f + bond.coerceIn(0, 100) * .12f) * (.75f + (motion.y - 430f) / 880f)
        val resting: Boolean = motion.activity == CompanionActivity.REST && bed != null && !reduced
        val action: CareSceneAction = if (resting) CareSceneAction.REST else if (motion.activity == CompanionActivity.PLAY && toy != null) CareSceneAction.PLAY else CareSceneAction.PET
        val timing: Long = if (reduced) 0 else ((motion.elapsed % 3f) * 1000).toLong()
        val frame: Int = poses.spec.getFrame(action, timing)
        val atlas = poses.spec.atlas
        source.set(frame % atlas.columns * atlas.frameWidth, frame / atlas.columns * atlas.frameHeight,
            (frame % atlas.columns + 1) * atlas.frameWidth, (frame / atlas.columns + 1) * atlas.frameHeight)
        val breath: Float = if (reduced) 0f else sin(time * 2f) * 2f
        actor.set(x - size / 2, motion.y - size - breath, x + size / 2, motion.y)
        paint.color = 0x25736954; canvas.drawOval(x - 65f, motion.y - 28f, x + 65f, motion.y - 8f, paint)
        paint.color = Color.WHITE
        paint.colorFilter = cosmeticFilter
        canvas.save()
        if (motion.isFacingLeft) canvas.scale(-1f, 1f, x, motion.y)
        if (!reduced && (motion.activity == CompanionActivity.APPROACH_TOY || motion.activity == CompanionActivity.APPROACH_BED))
            locomotion?.draw(canvas, paint, actor, activeTime)
        else canvas.drawBitmap(poses.bitmap, source, actor, paint)
        canvas.restore()
        paint.colorFilter = null
        cosmetics.draw(canvas, cosmeticEffect, actor, if (reduced) 0f else activeTime / 1000f)
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
                dragged = if (isEditing) placements.lastOrNull { objectBounds(it).contains(x, y) } else null
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
                if (item != null && didMove) {
                    onPlace?.invoke(item.decorationId, ((x - 65) / 175).toInt().coerceIn(0, 4), ((y - 330) / 103).toInt().coerceIn(0, 2))
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
        postDelayed(tick, if (!showPet) 60_000 else if (preferences.reducedMotion || !ValueAnimator.areAnimatorsEnabled() || isTravelling) 1000 else 50)
    }
    override fun onAttachedToWindow(): Unit { super.onAttachedToWindow(); schedule() }
    override fun onWindowVisibilityChanged(visibility: Int): Unit { super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) schedule() else pause() }
    override fun onDetachedFromWindow(): Unit { pause(); super.onDetachedFromWindow() }
}
