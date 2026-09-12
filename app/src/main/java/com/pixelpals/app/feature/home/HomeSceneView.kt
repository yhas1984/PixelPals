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
            val changed: Boolean = field != value
            field = value
            if (!value) { pendingPlacement = null; dragged = null }
            onEditingChanged?.invoke(value)
            if (changed) { cancelTick(); schedule() }
            invalidate()
        }
    private var isDrawingPostcard: Boolean = false
    internal fun drawPostcard(canvas: Canvas) {
        val previous: Boolean = isDrawingPostcard
        isDrawingPostcard = true
        try { draw(canvas) } finally { isDrawingPostcard = previous }
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
    private val toyAngles: MutableMap<String, Float> = mutableMapOf()
    private val deviceRest = com.pixelpals.app.core.rest.DeviceRestState(context)
    private var treeVisit: GingerTreeVisit? = null
    private var yukiHeat: YukiHomeHeat = YukiHomeHeat()
    private val thermalMemory: com.pixelpals.app.core.thermal.YukiThermalMemory =
        com.pixelpals.app.core.thermal.YukiThermalMemory(context)
    private var nextTemperatureRead: Long = 0L
    internal var temperatureReader: () -> Float? = {
        try {
            context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeUnless { it == Int.MIN_VALUE }?.div(10f)
        } catch (_: SecurityException) { null }
    }
    private var web: TelaHomeWeb = TelaHomeWeb()
    private var jellyHomeMotion: JellyHomeMotion = JellyHomeMotion()
    private val webPainter: TelaHomeWebPainter = TelaHomeWebPainter()
    var onWebHuntRequested: ((Int) -> Unit)? = null
    var onWebMealReady: (() -> Unit)? = null
    fun huntFly(index: Int = -1): Boolean {
        if (pet != PetType.TELA || isEditing || isTravelling || !showPet || isPaused) return false
        val selected: Int = if (index < 0) web.flies.indices.firstOrNull(web::isFlyVisible) ?: return false else index
        val started: Boolean = web.hunt(selected)
        if (started) { invalidate(); schedule() }
        return started
    }
    fun cancelWebHunt(): Unit { web.cancelHunt(); invalidate() }
    fun finishWebMeal(success: Boolean): Unit { web.finishMeal(success); invalidate() }

    fun exploreTree(): Unit {
        if (pet != PetType.GINGER || isEditing || isTravelling || !showPet || isMotionReduced || treeVisit != null) return
        treeVisit = GingerTreeVisit(motion.x, motion.y)
        invalidate(); schedule()
    }
    private var motion: CompanionMotion = CompanionMotion()
    internal var reviewSeed: Int? = null
    internal var previewTimeScale: Float = 1f
    internal var previewReducedMotion: Boolean = false
    private val isMotionReduced: Boolean get() = previewReducedMotion || preferences.reducedMotion || !ValueAnimator.areAnimatorsEnabled()
    private var describedDream: Boolean? = null
    private var lastFrame: Long = 0L
    private var running: Boolean = false
    private var isPaused: Boolean = false
    private val tick: Runnable = object : Runnable {
        override fun run(): Unit {
            running = false
            if (isPaused || !isAttachedToWindow || !isShown) { lastFrame = 0; return }
            val now: Long = SystemClock.uptimeMillis()
            if (lastFrame > 0 && now - lastFrame < 16) { schedule(); return }
            val minute: Long = System.currentTimeMillis() / 60_000
            if (minute != lastMinute) { hour = LocalTime.now().hour; lastMinute = minute }
            val delta: Long = if (lastFrame > 0) (now - lastFrame).coerceAtMost(100) else 0
            if (showPet && !isEditing && !isTravelling) advanceScene((delta * previewTimeScale.coerceIn(.1f, 1f)).toLong())
            lastFrame = now
            val dreaming: Boolean = treeVisit == null && motion.activity == CompanionActivity.REST && !isTravelling && !isEditing && showPet &&
                (isMotionReduced || motion.elapsed >= 1.4f)
            if (isClickable && describedDream != dreaming) {
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
        if (pet == PetType.YUKI) {
            if (activeTime >= nextTemperatureRead) {
                val temperature: Float? = temperatureReader()
                yukiHeat.updateTemperature(temperature, thermalMemory.update(temperature))
                nextTemperatureRead = activeTime + 4_000L
            }
            val wasHot: Boolean = yukiHeat.active
            yukiHeat.advance(delta / 1000f, isMotionReduced)
            if (yukiHeat.active) {
                motion.settleWithoutMovement(false)
                return
            }
            if (wasHot) motion.settleWithoutMovement(false)
        }
        if (pet == PetType.TELA) {
            val resting: Boolean = reviewSeed == null && deviceRest.shouldRest(preferences.restSchedule)
            val sleeping: Boolean = motion.advanceScheduledRest((resting || energy <= 25 || isUnwell) && !web.isHunting, delta / 1000f)
            val waking: Boolean = motion.advanceScheduledWake(delta / 1000f)
            if (web.advance(delta / 1000f, isMotionReduced, sleeping || waking,
                    profile.tempo * traits.tempo, traits.initiative)) onWebMealReady?.invoke()
            return
        }
        treeVisit?.let { visit ->
            visit.advance(delta / 1000f)
            if (visit.phase == GingerTreeVisit.Phase.DONE) {
                motion.finishExcursion(visit.facingLeft)
                treeVisit = null
            }
            return
        }
        val toy = placements.firstOrNull { it.decorationId == home?.favoriteObject && DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }
            ?: placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }
        val bed = placements.firstOrNull { it.decorationId == CareDecorationSelection.bed(placements) }
        if (toy != null && !isMotionReduced && !isEditing && motion.activity == CompanionActivity.PLAY &&
            !motion.isTurning && !motion.isApproaching && !motion.isPreparing &&
            (toy.decorationId == "pinwheel" || (toy.decorationId == "ball" && pet == com.pixelpals.app.core.domain.PetType.TARO))) {
            toyAngles[toy.decorationId] = ((toyAngles[toy.decorationId] ?: 0f) + delta.coerceIn(0, 100) * .09f) % 360f
        }
        if (reviewSeed == null && motion.advanceScheduledRest(
                deviceRest.shouldRest(preferences.restSchedule), delta / 1000f,
                bed?.takeUnless { isMotionReduced }?.let { objectBounds(it).centerX() },
                bed?.let { objectBounds(it).bottom - 30f } ?: 650f,
                profile.tempo * traits.tempo * traits.initiative)) {
            if (pet == PetType.JELLY) jellyHomeMotion.advance(delta / 1000f, motion.distanceTravelled, motion.activity, motion.speed, isMotionReduced)
            return
        }
        if (isMotionReduced) {
            motion.settleWithoutMovement(energy <= 25 || isUnwell, reduced = true)
            if (pet == PetType.JELLY) jellyHomeMotion.advance(delta / 1000f, motion.distanceTravelled, motion.activity, motion.speed, true)
            return
        }
        val toyCenter: Float = toy?.let { objectBounds(it).centerX() } ?: 500f
        val toyStand: Float = if (toy == null) 500f else toyCenter + if (toyCenter < 500f) 100f else -100f
        motion.context = CompanionIntentContext(energy, isUnwell, toy != null, bed != null, profile.curiosity,
            ((home?.playCount ?: 0) / 30f).coerceIn(0f, 1f), (.3f + bond / 140f).coerceIn(0f, 1f))
        motion.advance(delta / 1000f, toyStand,
            bed?.let { objectBounds(it).centerX() } ?: 500f, profile.tempo * traits.tempo * traits.initiative, bond,
            toy?.let { objectBounds(it).bottom - 30f } ?: 650f, bed?.let { objectBounds(it).bottom - 30f } ?: 650f, toy?.let { toyCenter < toyStand })
        if (pet == PetType.JELLY) jellyHomeMotion.advance(delta / 1000f, motion.distanceTravelled,
            motion.activity, motion.speed, isMotionReduced)
    }

    suspend fun loadPet(type: PetType): Unit {
        if (pet != type) {
            isEditing = false
            didMove = true // Ignore the release of a gesture begun with the previous pet.
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        pet = type
        jellyHomeMotion = JellyHomeMotion()
        treeVisit = null
        yukiHeat = YukiHomeHeat()
        nextTemperatureRead = 0L
        web = TelaHomeWeb()
        describedDream = null
        profile = CompanionProfiles.forPet(type)
        traits = CompanionTraits.derive(type, home, bond)
        locomotion = null
        val walk: HomeLocomotion = HomeLocomotion.load(context, type)
        if (pet != type) return
        motion = CompanionMotion(
            CompanionIntentSelector(reviewSeed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default),
            walk.turnDurationSeconds,
            walk.turnCommitSeconds,
            gingerPostures = walk.gingerPosturePack,
            gingerRestArtwork = walk.gingerRestPack,
        )
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
        painter.drawBackground(canvas, environment, hour, pet)
        if (isEditing && !isDrawingPostcard) drawGrid(canvas)
        val actorVisible = showPet && !isTravelling
        drawDecorations(canvas) { pet == PetType.TELA || !actorVisible || HomeDepth.isBehindPet(objectBounds(it).bottom - 30f, motion.y) }
        if (pet == PetType.TELA) webPainter.draw(canvas, web, activeTime / 1000f, isMotionReduced)
        if (actorVisible) drawCompanion(canvas)
        if (pet == PetType.TELA && web.isEating) webPainter.drawFlies(canvas, web, activeTime / 1000f, isMotionReduced)
        if (actorVisible && pet != PetType.TELA) drawDecorations(canvas) { !HomeDepth.isBehindPet(objectBounds(it).bottom - 30f, motion.y) }
        if (!isDrawingPostcard) dragged?.let { item -> DecorationCatalog.find(item.decorationId)?.let {
            val slot: Pair<Int, Int> = placementSlot(dragX, dragY)
            draggedBounds.set(gridBounds[slot.second * HomeGrid.COLUMNS + slot.first])
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = if (canPlace(item.decorationId, slot)) 0xff426b56.toInt() else 0xffac3e42.toInt()
            canvas.drawRoundRect(draggedBounds, 18f, 18f, paint)
            paint.style = Paint.Style.FILL
            painter.drawObject(canvas, it, draggedBounds, treasure, pet)
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
            if (include(position) && (isDrawingPostcard || position.decorationId != dragged?.decorationId)) {
                DecorationCatalog.find(position.decorationId)?.let { drawDecoration(canvas, it, position) }
            }
        }
    }

    private fun drawDecoration(canvas: Canvas, item: Decoration, position: HomeDecorationEntity): Unit {
        val bounds: RectF = objectBounds(position)
        val activeToy: HomeDecorationEntity? = placements.firstOrNull { it.decorationId == home?.favoriteObject && DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }
            ?: placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }
        canvas.save()
        val spinningToy: Boolean = item.id == "pinwheel" || (item.id == "ball" && pet == com.pixelpals.app.core.domain.PetType.TARO)
        val toyRotation: Float = if (spinningToy) toyAngles[item.id] ?: 0f else 0f
        if (!isMotionReduced && motion.activity == CompanionActivity.PLAY && !motion.isTurning && position == activeToy) {
            val nudge: Float = if (pet == PetType.JELLY) jellyHomeMotion.toyResponse
                else locomotion?.toyResponse(motion.elapsed) ?: 0f
            val direction: Float = if (motion.x < bounds.centerX()) 1f else -1f
            if (!spinningToy) {
                canvas.translate(nudge * 24f * direction, -nudge * 14f)
                canvas.rotate(nudge * 25f * direction, bounds.centerX(), bounds.bottom - 30f)
            }
        }
        painter.drawObject(canvas, item, bounds, treasure, pet, toyRotation)
        canvas.restore()
    }

    private fun drawCompanion(canvas: Canvas): Unit {
        val sprites: HomeLocomotion = locomotion ?: return
        // Editing pauses scene time; keep the exact pose instead of switching to an idle frame.
        val reduced: Boolean = isMotionReduced
        val visit = treeVisit
        val size: Float = 290f * com.pixelpals.app.core.motion.PetArtworkScale.speciesSize(pet)
        val onWeb: Boolean = pet == PetType.TELA
        val webRadians: Float = web.angleDegrees * PI.toFloat() / 180f
        val x: Float = if (onWeb) web.x - cos(webRadians) * size * .24f else visit?.x ?: motion.x
        val actorY: Float = if (onWeb) web.y - sin(webRadians) * size * .24f + size * .4f else visit?.y ?: motion.y
        val gait: HomeGait = HomeGait.forPet(pet)
        val jellyPose: JellyHomeMotion.Pose? = if (pet == PetType.JELLY) jellyHomeMotion.pose else null
        val phase: Float = (if (pet == PetType.CORGI) com.pixelpals.app.core.motion.CorgiGait.phaseAt(motion.distanceTravelled, size)
            else motion.distanceTravelled / 105f) * (2f * PI.toFloat())
        val amount: Float = if (visit != null || reduced) 0f else (motion.speed / 75f).coerceIn(0f, 1f)
        val lift: Float = if (pet == PetType.JELLY || reduced) 0f else if (gait.isFloating && motion.activity != CompanionActivity.REST)
            7f + sin(activeTime / 1000f * 2f) * 3f else abs(sin(phase)) * gait.bounce * amount
        val preparation: Float = if (visit == null && !reduced && motion.isPreparing) sin(motion.elapsed / CompanionMotion.ANTICIPATION_SECONDS * PI.toFloat()) else 0f
        val breath: Float = if (pet == PetType.JELLY || reduced) 0f else sin(activeTime / 1000f * 2f) * 1.1f
        actor.set(x - size / 2, actorY - size, x + size / 2, actorY)
        paint.color = 0x25736954
        val shadowWidth: Float = size * .21f * (1f - lift / 100f)
        if (!onWeb && pet != PetType.BLOOP && visit?.isAirborne != true)
            canvas.drawOval(x - shadowWidth, actorY - 21f, x + shadowWidth, actorY - 7f, paint)
        paint.color = Color.WHITE
        paint.colorFilter = cosmeticFilter
        canvas.save()
        canvas.translate(0f, -lift)
        canvas.rotate(if (pet == PetType.JELLY || reduced) 0f else sin(phase) * gait.sway * amount, x, actorY)
        // Anticipation must not stretch shells, bones or wings. Only the three
        // amorphous companions use whole-body deformation; other pets use their poses.
        if (pet == PetType.BLOOP || pet == PetType.NUBE_MICHI)
            canvas.scale(1f + preparation * .035f, 1f - preparation * .035f + breath / size, x, actorY)
        if (pet == PetType.JELLY && motion.activity != CompanionActivity.REST && motion.activity != CompanionActivity.WAKE) {
            val scale = jellyPose?.scaleY ?: 1f
            canvas.translate(0f, jellyPose?.lift ?: 0f)
            // The source art's feet sit a little above the logical actor bottom.
            // Keep that artistic ground fixed while the body compresses.
            val jellyGround = actorY - size * .04f
            canvas.scale(1f / scale, scale, x, jellyGround)
        }
        if (!onWeb && (visit?.facingLeft ?: if (motion.isTurning) motion.turnFromFacingLeft else motion.isFacingLeft)) canvas.scale(-1f, 1f, x, actorY)
        if (onWeb) canvas.rotate(web.angleDegrees, x, actorY - size * .4f)
        val webClip: String? = if (onWeb && motion.activity != CompanionActivity.REST && motion.activity != CompanionActivity.WAKE) "walk" else null
        val melting: Boolean = pet == PetType.YUKI && yukiHeat.active
        sprites.draw(canvas, paint, actor, motion, reduced, if (melting) "melt" else webClip ?: visit?.clip,
            if (melting) yukiHeat.elapsedSeconds else if (onWeb) web.distanceTravelled / 105f * 1.44f else visit?.clipSeconds ?: 0f)
        canvas.restore()
        paint.colorFilter = null
        canvas.save()
        if (onWeb) canvas.rotate(web.angleDegrees, x, actorY - size * .4f)
        cosmetics.draw(canvas, cosmeticEffect, actor, if (reduced) 0f else activeTime / 1000f)
        canvas.restore()
        if (visit == null && motion.activity == CompanionActivity.REST && !isEditing && (reduced || motion.elapsed >= 1.4f))
            dreamPainter.draw(canvas, x, actorY, size, motion.elapsed, reduced)
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
                    val fly: Int = if (pet == PetType.TELA && !isEditing && !isTravelling && showPet)
                        web.flies.indexOfFirst { hypot(x - it.x, y - it.y) <= 65f } else -1
                    if (fly >= 0 && web.isFlyVisible(fly)) {
                        onWebHuntRequested?.invoke(fly)
                        invalidate()
                        return true
                    }
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

    override fun performClick(): Boolean {
        super.performClick()
        if (!isEditing && !isTravelling && showPet) {
            if (pet == PetType.JELLY) jellyHomeMotion.touch()
            onPet?.invoke()
        }
        return true
    }
    private fun cancelTick(): Unit { removeCallbacks(tick); running = false; lastFrame = 0 }
    fun pause(): Unit { isPaused = true; cancelTick() }
    fun resume(): Unit { isPaused = false; nextTemperatureRead = 0L; schedule() }
    private fun schedule(): Unit {
        if (isPaused || running || !isAttachedToWindow || !isShown) return
        running = true
        if (!showPet) postDelayed(tick, 60_000)
        else if (isMotionReduced || isTravelling || isEditing) postDelayed(tick, 1000)
        else postOnAnimation(tick)
    }
    override fun onAttachedToWindow(): Unit { super.onAttachedToWindow(); schedule() }
    override fun onWindowVisibilityChanged(visibility: Int): Unit { super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) schedule() else cancelTick() }
    override fun onDetachedFromWindow(): Unit { cancelTick(); super.onDetachedFromWindow() }
}
