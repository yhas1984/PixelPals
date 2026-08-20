package com.pixelpals.app.debug

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pixelpals.app.PetService
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetAnimationPlayer
import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.core.runtime.PetEvent
import com.pixelpals.app.core.runtime.PetSurface
import com.pixelpals.app.core.runtime.PetVector
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import com.pixelpals.app.status.PetMood
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Debug laboratory that uses the same PetRuntime as the overlay candidate. */
@SuppressLint("SetTextI18n")
class PetAtlasLabActivity : AppCompatActivity() {
    private lateinit var lab: PetAtlasLabView
    private val clipAdapter by lazy { spinnerAdapter(mutableListOf()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (32 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            setBackgroundColor(Color.rgb(24, 24, 28))
        }
        root.addView(TextView(this).apply {
            text = "PixelPals Runtime Lab"
            textSize = 20f
            setTextColor(Color.WHITE)
        })
        val petSpinner = spinner(PETS.keys.toList())
        val implementationSpinner = spinner(listOf("Atlas", "Runtime"))
        val clipSpinner = Spinner(this).apply { adapter = clipAdapter }
        root.addView(weightedRow(petSpinner, implementationSpinner, clipSpinner))

        val moodSpinner = spinner(PetMood.entries.map { "Mood ${it.name}" })
        val bondSpinner = spinner(BOND_VALUES.map { "Bond $it" })
        val temperatureSpinner = spinner(TEMPERATURE_VALUES.map { "${it.toInt()} C" })
        val surfaceSpinner = spinner(PetSurface.entries.map { it.name })
        root.addView(weightedRow(moodSpinner, bondSpinner))
        root.addView(weightedRow(temperatureSpinner, surfaceSpinner))

        val diagnostics = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 11f
            maxLines = 3
        }
        root.addView(diagnostics)
        lab = PetAtlasLabView(this) { clipIds, status ->
            if (clipIds != null) {
                clipAdapter.clear()
                clipAdapter.addAll(clipIds)
                clipAdapter.notifyDataSetChanged()
            }
            if (status != null) diagnostics.text = status
        }
        root.addView(lab, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
        }
        controls.addView(weightedRow(
            button("Pause") { lab.togglePause() },
            button("Step") { lab.stepFrame() },
            button("Mirror") { lab.toggleMirror() },
            button("BG") { lab.nextBackground() },
        ))
        controls.addView(weightedRow(
            button("0.25x") { lab.setSpeed(.25f) },
            button("1x") { lab.setSpeed(1f) },
            button("2x") { lab.setSpeed(2f) },
            button("Reset") { lab.reset() },
        ))
        controls.addView(weightedRow(
            button("Tap") { lab.runTap() },
            button("Hold") { lab.runHold() },
            button("Drag") { lab.runDrag() },
            button("Fling") { lab.runFling() },
        ))
        controls.addView(weightedRow(
            button("Cancel") { lab.runCancel() },
            button("Record") { lab.toggleRecording() },
            button("Replay") { lab.replay() },
            button("Overlay") { lab.startAutonomous() },
        ))
        root.addView(controls)
        setContentView(root)

        petSpinner.onSelection { lab.loadPet(PETS.values.elementAtOrNull(it) ?: PETS.values.first()) }
        implementationSpinner.onSelection { lab.setRuntimeEnabled(it == 1) }
        clipSpinner.onSelection { lab.selectClip(it) }
        moodSpinner.onSelection { lab.updateConfig(mood = PetMood.entries[it]) }
        bondSpinner.onSelection { lab.updateConfig(bond = BOND_VALUES[it]) }
        temperatureSpinner.onSelection { lab.updateConfig(temperature = TEMPERATURE_VALUES[it]) }
        surfaceSpinner.onSelection { lab.updateConfig(surface = PetSurface.entries[it]) }
        lab.loadPet(PETS.values.first())
    }

    private fun spinner(items: List<String>): Spinner = Spinner(this).apply {
        adapter = spinnerAdapter(items)
        setPadding(4, 0, 4, 0)
    }

    private fun weightedRow(vararg children: View): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        children.forEach { child -> addView(child, LinearLayout.LayoutParams(0, -2, 1f)) }
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 11f
        minHeight = 0
        minimumHeight = 0
        setOnClickListener { action() }
    }

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> = object : ArrayAdapter<String>(
        this,
        android.R.layout.simple_spinner_item,
        items,
    ) {
        init {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
            super.getView(position, convertView, parent).also { (it as? TextView)?.setTextColor(Color.WHITE) }

        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
            super.getDropDownView(position, convertView, parent).also { (it as? TextView)?.setTextColor(Color.BLACK) }
    }

    private fun Spinner.onSelection(action: (Int) -> Unit) {
        onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) = action(position)
        }
    }

    private companion object {
        val BOND_VALUES: List<Int> = listOf(0, 12, 35, 70)
        val TEMPERATURE_VALUES: List<Float> = listOf(24f, 38f, 40f, 42f)
        val PETS: LinkedHashMap<String, PetLabAsset> = linkedMapOf(
            "Taro" to PetLabAsset("taro", "pets/taro/taro_motion_v2.json", PetType.TARO),
            "Tela" to PetLabAsset("tela", "pets/tela/tela_motion_v2.json", PetType.TELA),
            "Yuki" to PetLabAsset("yuki", "pets/yuki/yuki_sheet_v1.json", PetType.YUKI),
            "Lumi" to PetLabAsset("lumi", "pets/lumi/lumi_motion_v2.json", PetType.LUMI),
        )
    }
}

private data class PetLabAsset(val id: String, val specPath: String, val petType: PetType)
private data class RecordedLabEvent(val atMillis: Long, val event: PetEvent)

@SuppressLint("ViewConstructor", "SetTextI18n")
private class PetAtlasLabView(
    private val activity: PetAtlasLabActivity,
    private val onLabChanged: (clipIds: List<String>?, status: String?) -> Unit,
) : View(activity) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        textSize = 28f
    }
    private val sourceRect = Rect()
    private val destinationRect = RectF()
    private val tick = object : Runnable {
        override fun run() {
            if (!paused) {
                if (runtimeEnabled) {
                    runtimeSession?.dispatch(PetEvent.Tick(1f / 60f * speed))
                } else {
                    player.update(1f / 60f * speed)
                }
            }
            diagnosticsElapsed += 1f / 60f
            if (diagnosticsElapsed >= DIAGNOSTICS_INTERVAL_SECONDS) {
                diagnosticsElapsed = 0f
                publishDiagnostics()
            }
            invalidate()
            handler.postDelayed(this, FRAME_DELAY_MILLIS)
        }
    }

    private var spec: PetAtlasSpec? = null
    private var bitmap: Bitmap? = null
    private var player = PetAnimationPlayer()
    private var runtimeSession: PetRuntimeLabSession? = null
    private var config = RuntimeLabConfig()
    private var paused = false
    private var runtimeEnabled = false
    private var speed = 1f
    private var mirrored = false
    private var background = 0
    private var selectedClip = ""
    private var loadedPet: PetLabAsset? = null
    private var diagnosticsElapsed = 0f
    private var isRecording = false
    private var recordingStartedAt = 0L
    private val recordedEvents = mutableListOf<RecordedLabEvent>()
    private var cachedSpriteSize = 48
    private var cachedBounds = PetBounds(0, 0, PetBounds.TOP_MARGIN_PX, PetBounds.TOP_MARGIN_PX)

    init {
        handler.post(tick)
    }

    fun loadPet(asset: PetLabAsset) {
        loadedPet = asset
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val json = activity.assets.open(asset.specPath).bufferedReader().use { it.readText() }
                    val parsed = PetAtlasSpec.fromJson(JSONObject(json))
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inScaled = false
                    }
                    val image = activity.assets.open(parsed.atlasPath).use { input ->
                        BitmapFactory.decodeStream(input, null, options)
                    }
                    parsed to image
                }.getOrNull()
            }
            bitmap?.takeIf { old -> old !== loaded?.second && !old.isRecycled }?.recycle()
            spec = loaded?.first
            bitmap = loaded?.second
            val clips = spec?.clips.orEmpty().map { it.id }
            onLabChanged(clips, null)
            selectedClip = clips.firstOrNull().orEmpty()
            player = PetAnimationPlayer(spec?.clips.orEmpty().map { clip ->
                PetAnimationClip(clip.id, clip.frames, clip.loop, clip.frameDurationMs / 1000f)
            })
            if (selectedClip.isNotBlank()) player.setClip(selectedClip)
            rebuildRuntime()
            invalidate()
        }
    }

    fun setRuntimeEnabled(enabled: Boolean) {
        runtimeEnabled = enabled
        rebuildRuntime()
    }

    fun updateConfig(
        mood: PetMood = config.mood,
        bond: Int = config.bond,
        temperature: Float = config.temperatureCelsius,
        surface: PetSurface = config.surface,
    ) {
        config = config.copy(mood = mood, bond = bond, temperatureCelsius = temperature, surface = surface)
        rebuildRuntime()
    }

    fun selectClip(index: Int) {
        val id = spec?.clips?.getOrNull(index)?.id ?: return
        selectedClip = id
        player.setClip(id)
        invalidate()
    }

    fun togglePause() {
        paused = !paused
        if (runtimeEnabled) dispatch(if (paused) PetEvent.Paused else PetEvent.Resumed)
    }

    fun toggleMirror() {
        mirrored = !mirrored
        invalidate()
    }

    fun setSpeed(value: Float) {
        speed = value
        publishDiagnostics()
    }

    fun nextBackground() {
        background = (background + 1) % 4
        invalidate()
    }

    fun reset() {
        player.reset()
        paused = false
        rebuildRuntime()
    }

    fun stepFrame() {
        val duration = spec?.clip(selectedClip)?.frameDurationMs?.div(1000f) ?: 1f / 60f
        if (runtimeEnabled) dispatch(PetEvent.Tick(duration)) else player.update(duration)
        paused = true
        invalidate()
    }

    fun runTap() = dispatch(PetEvent.Tap)

    fun runHold() {
        dispatch(PetEvent.HoldStarted)
        handler.postDelayed({ dispatch(PetEvent.HoldReleased) }, HOLD_SCRIPT_MILLIS)
    }

    fun runDrag() {
        val start = runtimeSession?.output?.position ?: PetVector(200f, 200f)
        val grab = PetVector(24f, 24f)
        dispatch(PetEvent.DragStarted(PetVector(start.x + grab.x, start.y + grab.y), grab))
        dispatch(PetEvent.DragMoved(PetVector(start.x + 180f, start.y - 180f)))
        dispatch(PetEvent.Released())
    }

    fun runFling() {
        val start = runtimeSession?.output?.position ?: PetVector(200f, 200f)
        val grab = PetVector(24f, 24f)
        dispatch(PetEvent.DragStarted(PetVector(start.x + grab.x, start.y + grab.y), grab))
        dispatch(PetEvent.DragMoved(PetVector(start.x + 120f, start.y - 120f)))
        dispatch(PetEvent.Flung(PetVector(1_500f, -1_700f)))
    }

    fun runCancel() = dispatch(PetEvent.Cancelled)

    fun toggleRecording() {
        isRecording = !isRecording
        if (isRecording) {
            recordedEvents.clear()
            recordingStartedAt = android.os.SystemClock.uptimeMillis()
        } else {
            saveReplay()
        }
        publishDiagnostics()
    }

    fun replay() {
        if (recordedEvents.isEmpty()) {
            loadSavedReplay()
        }
        if (recordedEvents.isEmpty()) return
        isRecording = false
        rebuildRuntime()
        recordedEvents.forEach { recorded ->
            handler.postDelayed({ dispatch(recorded.event, shouldRecord = false) }, recorded.atMillis)
        }
    }

    fun startAutonomous() {
        loadedPet?.let { asset -> PetService.requestPetChange(activity, asset.petType) }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        cachedSpriteSize = (minOf(width, height) * 0.24f).toInt().coerceAtLeast(48)
        cachedBounds = PetBounds.compute(
            screenWidth = width,
            screenHeight = height,
            petSpriteSize = cachedSpriteSize,
            topSystemInsetPx = 0,
            bottomSystemInsetPx = 0,
        )
        rebuildRuntime()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val atlas = bitmap ?: return
        val currentSpec = spec ?: return
        canvas.drawColor(backgroundColor())
        val frame = if (runtimeEnabled) runtimeSession?.output?.frame ?: player.currentFrame() else player.currentFrame()
        updateFrameRect(atlas, currentSpec, frame)
        val spriteSize = logicalSpriteSize().toFloat()
        val output = runtimeSession?.output
        val centerX: Float
        val centerY: Float
        if (runtimeEnabled && output != null) {
            centerX = output.position.x + spriteSize / 2f
            centerY = output.position.y + spriteSize / 2f
        } else {
            centerX = width / 2f
            centerY = height / 2f
        }
        val drawSize = if (runtimeEnabled) spriteSize else (minOf(width, height) * .68f).coerceAtLeast(1f)
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(if (mirrored) -1f else 1f, 1f)
        output?.let { runtimeOutput ->
            if (runtimeEnabled) {
                canvas.rotate(runtimeOutput.transform.rotationDegrees)
                canvas.scale(
                    runtimeOutput.facing.scaleX * runtimeOutput.transform.scaleX,
                    runtimeOutput.transform.scaleY,
                )
            }
        }
        paint.alpha = ((output?.transform?.alpha ?: 1f) * 255).toInt()
        destinationRect.set(-drawSize / 2f, -drawSize / 2f, drawSize / 2f, drawSize / 2f)
        canvas.drawBitmap(atlas, sourceRect, destinationRect, paint)
        currentSpec.pivot?.let { pivot ->
            guidePaint.color = Color.YELLOW
            val pivotX = (pivot.x.toFloat() / currentSpec.frameWidth - .5f) * drawSize
            val pivotY = (pivot.y.toFloat() / currentSpec.frameHeight - .5f) * drawSize
            canvas.drawLine(pivotX - 10f, pivotY, pivotX + 10f, pivotY, guidePaint)
            canvas.drawLine(pivotX, pivotY - 10f, pivotX, pivotY + 10f, guidePaint)
        }
        canvas.restore()
        guidePaint.color = if (background == 2) Color.WHITE else Color.DKGRAY
        if (runtimeEnabled) {
            val bounds = runtimeBounds()
            canvas.drawRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                (bounds.right + logicalSpriteSize()).toFloat(),
                (bounds.floor + logicalSpriteSize()).toFloat(),
                guidePaint,
            )
            canvas.drawLine(0f, bounds.floor + spriteSize, width.toFloat(), bounds.floor + spriteSize, guidePaint)
        } else {
            canvas.drawRect(
                centerX - drawSize / 2f,
                centerY - drawSize / 2f,
                centerX + drawSize / 2f,
                centerY + drawSize / 2f,
                guidePaint,
            )
        }
        canvas.drawText(if (runtimeEnabled) "RUNTIME" else "LEGACY ATLAS", 12f, 34f, labelPaint)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        scope.coroutineContext.cancel()
        bitmap?.let { image -> if (!image.isRecycled) image.recycle() }
        super.onDetachedFromWindow()
    }

    private fun rebuildRuntime() {
        val asset = loadedPet ?: return
        val currentSpec = spec ?: return
        if (width <= 0 || height <= 0) return
        runtimeSession = PetRuntimeLabSession.create(asset.petType, currentSpec, config, runtimeBounds())
        publishDiagnostics()
    }

    private fun dispatch(event: PetEvent, shouldRecord: Boolean = true) {
        if (!runtimeEnabled) return
        runtimeSession?.dispatch(event)
        if (isRecording && shouldRecord && event !is PetEvent.Tick) {
            recordedEvents += RecordedLabEvent(
                atMillis = android.os.SystemClock.uptimeMillis() - recordingStartedAt,
                event = event,
            )
        }
        publishDiagnostics()
        invalidate()
    }

    private fun publishDiagnostics() {
        val asset = loadedPet ?: return
        val output = runtimeSession?.output
        val runtimeStatus = if (!runtimeEnabled) {
            "${asset.id} | clip=$selectedClip frame=${player.currentFrame()} | speed=${speed}x"
        } else if (output == null) {
            "${asset.id} | runtime pending for this gate | seed=${config.seed}"
        } else {
            "${asset.id} | ${output.intent}/${output.surface} | ${output.clipId}:${output.frame} " +
                "pos=${output.position.x.toInt()},${output.position.y.toInt()} " +
                "v=${output.velocity.x.toInt()},${output.velocity.y.toInt()} | " +
                "bond=${config.bond} temp=${config.temperatureCelsius.toInt()} seed=${config.seed}" +
                if (isRecording) " | RECORDING" else ""
        }
        onLabChanged(null, runtimeStatus)
    }

    private fun saveReplay() {
        val directory = File(activity.cacheDir, "pet_runtime_replays").apply { mkdirs() }
        val eventsJson = JSONArray()
        recordedEvents.forEach { recorded ->
            eventsJson.put(JSONObject().apply {
                put("atMillis", recorded.atMillis)
                put("type", eventType(recorded.event))
                putEventPayload(recorded.event)
            })
        }
        val payload = JSONObject().apply {
            put("version", 1)
            put("petId", loadedPet?.id)
            put("seed", config.seed)
            put("events", eventsJson)
        }
        File(directory, "last_replay.json").writeText(payload.toString(2))
    }

    private fun loadSavedReplay() {
        val replayFile = File(File(activity.cacheDir, "pet_runtime_replays"), "last_replay.json")
        if (!replayFile.isFile) return
        val replay = runCatching { JSONObject(replayFile.readText()) }.getOrNull() ?: return
        if (replay.optString("petId") != loadedPet?.id) return
        val events = replay.optJSONArray("events") ?: return
        val parsed = buildList {
            for (index in 0 until events.length()) {
                val item = events.optJSONObject(index) ?: continue
                val event = item.toPetEvent() ?: continue
                add(RecordedLabEvent(item.optLong("atMillis").coerceAtLeast(0L), event))
            }
        }
        if (parsed.isEmpty()) return
        config = config.copy(seed = replay.optInt("seed", config.seed))
        recordedEvents.clear()
        recordedEvents.addAll(parsed)
    }

    private fun JSONObject.toPetEvent(): PetEvent? = when (optString("type")) {
        "tap" -> PetEvent.Tap
        "hold_start" -> PetEvent.HoldStarted
        "hold_release" -> PetEvent.HoldReleased
        "drag_start" -> PetEvent.DragStarted(
            pointer = PetVector(getFiniteFloat("pointerX"), getFiniteFloat("pointerY")),
            grabOffset = PetVector(getFiniteFloat("grabX"), getFiniteFloat("grabY")),
        )
        "drag_move" -> PetEvent.DragMoved(
            PetVector(getFiniteFloat("pointerX"), getFiniteFloat("pointerY")),
        )
        "fling" -> PetEvent.Flung(
            PetVector(getFiniteFloat("velocityX"), getFiniteFloat("velocityY")),
        )
        "release" -> PetEvent.Released(
            PetVector(getFiniteFloat("velocityX"), getFiniteFloat("velocityY")),
        )
        "cancel" -> PetEvent.Cancelled
        "pause" -> PetEvent.Paused
        "resume" -> PetEvent.Resumed
        "destroy" -> PetEvent.Destroyed
        "recovery_complete" -> PetEvent.RecoveryCompleted
        else -> null
    }

    private fun JSONObject.getFiniteFloat(key: String): Float {
        val value = optDouble(key, 0.0).toFloat()
        return if (value.isFinite()) value else 0f
    }

    private fun JSONObject.putEventPayload(event: PetEvent) {
        when (event) {
            is PetEvent.DragStarted -> {
                put("pointerX", event.pointer.x)
                put("pointerY", event.pointer.y)
                put("grabX", event.grabOffset.x)
                put("grabY", event.grabOffset.y)
            }
            is PetEvent.DragMoved -> {
                put("pointerX", event.pointer.x)
                put("pointerY", event.pointer.y)
            }
            is PetEvent.Flung -> {
                put("velocityX", event.velocity.x)
                put("velocityY", event.velocity.y)
            }
            is PetEvent.Released -> {
                put("velocityX", event.velocity.x)
                put("velocityY", event.velocity.y)
            }
            else -> Unit
        }
    }

    private fun eventType(event: PetEvent): String = when (event) {
        PetEvent.Tap -> "tap"
        PetEvent.HoldStarted -> "hold_start"
        PetEvent.HoldReleased -> "hold_release"
        is PetEvent.DragStarted -> "drag_start"
        is PetEvent.DragMoved -> "drag_move"
        is PetEvent.Flung -> "fling"
        is PetEvent.Released -> "release"
        PetEvent.Cancelled -> "cancel"
        PetEvent.Paused -> "pause"
        PetEvent.Resumed -> "resume"
        PetEvent.Destroyed -> "destroy"
        PetEvent.RecoveryCompleted -> "recovery_complete"
        is PetEvent.Tick -> "tick"
        is PetEvent.StatusChanged -> "status"
        is PetEvent.EnvironmentChanged -> "environment"
    }

    private fun updateFrameRect(atlas: Bitmap, currentSpec: PetAtlasSpec, frame: Int) {
        val safeFrame = frame.coerceIn(0, currentSpec.frameCount - 1)
        val atlasScaleX = atlas.width.toFloat() / (currentSpec.columns * currentSpec.frameWidth)
        val atlasScaleY = atlas.height.toFloat() / (currentSpec.rows * currentSpec.frameHeight)
        val column = safeFrame % currentSpec.columns
        val row = safeFrame / currentSpec.columns
        sourceRect.set(
            (column * currentSpec.frameWidth * atlasScaleX).toInt(),
            (row * currentSpec.frameHeight * atlasScaleY).toInt(),
            ((column + 1) * currentSpec.frameWidth * atlasScaleX).toInt(),
            ((row + 1) * currentSpec.frameHeight * atlasScaleY).toInt(),
        )
    }

    private fun logicalSpriteSize(): Int = cachedSpriteSize

    private fun runtimeBounds(): PetBounds = cachedBounds

    private fun backgroundColor(): Int = when (background) {
        0 -> Color.rgb(226, 226, 232)
        1 -> Color.WHITE
        2 -> Color.rgb(10, 10, 16)
        else -> Color.rgb(255, 0, 180)
    }

    private companion object {
        const val FRAME_DELAY_MILLIS: Long = 16L
        const val HOLD_SCRIPT_MILLIS: Long = 700L
        const val DIAGNOSTICS_INTERVAL_SECONDS: Float = 0.25f
    }
}
