package com.pixelpals.app.debug

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.BitmapFactory.Options
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
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetAnimationPlayer
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Common debug-only atlas laboratory for V2 assets and the real pet runtime. */
class PetAtlasLabActivity : AppCompatActivity() {
    private lateinit var lab: PetAtlasLabView
    private lateinit var clipSpinner: Spinner
    private val clipsAdapter by lazy { spinnerAdapter(mutableListOf()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (36 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            setBackgroundColor(Color.rgb(24, 24, 28))
        }
        root.addView(TextView(this).apply {
            text = "PixelPals V2 Atlas Lab"
            textSize = 22f
            setTextColor(Color.WHITE)
        })
        val petSpinner = Spinner(this)
        petSpinner.adapter = spinnerAdapter(PETS.keys.toList())
        root.addView(petSpinner)
        clipSpinner = Spinner(this).apply { adapter = clipsAdapter }
        root.addView(clipSpinner)
        lab = PetAtlasLabView(this) { clips ->
            clipsAdapter.clear()
            clipsAdapter.addAll(clips)
            clipsAdapter.notifyDataSetChanged()
        }
        root.addView(lab, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.VERTICAL }
        controls.addView(row(button("Pause") { lab.togglePause() }, button("Step") { lab.stepFrame() }, button("Mirror") { lab.toggleMirror() }))
        controls.addView(row(button("0.25x") { lab.setSpeed(.25f) }, button("1x") { lab.setSpeed(1f) }, button("2x") { lab.setSpeed(2f) }, button("Background") { lab.nextBackground() }))
        controls.addView(row(button("Autonomous real") { lab.startAutonomous() }, button("Reset") { lab.resetClip() }))
        root.addView(controls)
        setContentView(root)

        petSpinner.setSelection(0)
        petSpinner.setOnItemSelectedListener(SimpleItemSelectedListener { lab.loadPet(PETS.values.elementAtOrNull(it) ?: PETS.values.first()) })
        clipSpinner.setOnItemSelectedListener(SimpleItemSelectedListener { lab.selectClip(it) })
        lab.loadPet(PETS.values.first())
    }

    private fun row(vararg children: View): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        children.forEach { addView(it) }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> = object : ArrayAdapter<String>(
        this,
        android.R.layout.simple_spinner_item,
        items,
    ) {
        init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            return super.getView(position, convertView, parent).also { (it as? TextView)?.setTextColor(Color.WHITE) }
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            return super.getDropDownView(position, convertView, parent).also { (it as? TextView)?.setTextColor(Color.BLACK) }
        }
    }

    private class SimpleItemSelectedListener(private val action: (Int) -> Unit) : android.widget.AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = action(position)
    }

    companion object {
        private val PETS = linkedMapOf(
            "Tela" to PetLabAsset("tela", "pets/tela/tela_motion_v2.json", PetType.TELA),
            "Taro" to PetLabAsset("taro", "pets/taro/taro_motion_v2.json", PetType.TARO),
            "Lumi" to PetLabAsset("lumi", "pets/lumi/lumi_motion_v2.json", PetType.LUMI),
        )
    }
}

private data class PetLabAsset(val id: String, val specPath: String, val petType: PetType)

private class PetAtlasLabView(
    private val activity: PetAtlasLabActivity,
    private val onClipsChanged: (List<String>) -> Unit,
) : View(activity) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val tick = object : Runnable {
        override fun run() {
            if (!paused) player.update(1f / 60f * speed)
            invalidate()
            handler.postDelayed(this, 16L)
        }
    }
    private var spec: PetAtlasSpec? = null
    private var bitmap: Bitmap? = null
    private var player = PetAnimationPlayer()
    private var paused = false
    private var speed = 1f
    private var mirrored = false
    private var background = 0
    private var selectedClip = ""
    private var loadedPet: PetLabAsset? = null

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
                    val options = Options().apply {
                        // The lab previews 384px cells into a much smaller viewport.
                        // Decode at half resolution to avoid killing the debug activity
                        // on devices with low free memory; the destination size stays
                        // unchanged and the source rect is scaled below.
                        inSampleSize = 2
                        inScaled = false
                    }
                    val image = activity.assets.open(parsed.atlasPath).use { BitmapFactory.decodeStream(it, null, options) }
                    parsed to image
                }.getOrNull()
            }
            spec = loaded?.first
            bitmap = loaded?.second
            val clips = spec?.clips.orEmpty().map { it.id }
            onClipsChanged(clips)
            selectedClip = clips.firstOrNull().orEmpty()
            player = PetAnimationPlayer(spec?.clips.orEmpty().map { PetAnimationClip(it.id, it.frames, it.loop, it.frameDurationMs / 1000f) })
            if (selectedClip.isNotBlank()) player.setClip(selectedClip)
            invalidate()
        }
    }

    fun selectClip(index: Int) {
        val id = spec?.clips?.getOrNull(index)?.id ?: return
        selectedClip = id
        player.setClip(id)
        invalidate()
    }

    fun togglePause() { paused = !paused }
    fun toggleMirror() { mirrored = !mirrored; invalidate() }
    fun setSpeed(value: Float) { speed = value; invalidate() }
    fun nextBackground() { background = (background + 1) % 4; invalidate() }
    fun resetClip() { player.reset(); paused = false; invalidate() }
    fun stepFrame() {
        val duration = spec?.clip(selectedClip)?.frameDurationMs ?: return
        player.update(duration / 1000f)
        paused = true
        invalidate()
    }

    fun startAutonomous() {
        loadedPet?.let { PetService.requestPetChange(activity, it.petType) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val atlas = bitmap ?: return
        val currentSpec = spec ?: return
        canvas.drawColor(when (background) {
            0 -> Color.rgb(226, 226, 232)
            1 -> Color.WHITE
            2 -> Color.rgb(10, 10, 16)
            else -> Color.rgb(255, 0, 180)
        })
        val frame = player.currentFrame()
        val atlasScaleX = atlas.width.toFloat() / (currentSpec.columns * currentSpec.frameWidth)
        val atlasScaleY = atlas.height.toFloat() / (currentSpec.rows * currentSpec.frameHeight)
        val source = Rect(
            ((frame % currentSpec.columns) * currentSpec.frameWidth * atlasScaleX).toInt(),
            ((frame / currentSpec.columns) * currentSpec.frameHeight * atlasScaleY).toInt(),
            (((frame % currentSpec.columns) + 1) * currentSpec.frameWidth * atlasScaleX).toInt(),
            (((frame / currentSpec.columns) + 1) * currentSpec.frameHeight * atlasScaleY).toInt(),
        )
        val size = (minOf(width, height) * .78f).coerceAtLeast(1f)
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(if (mirrored) -1f else 1f, 1f)
        canvas.drawBitmap(atlas, source, RectF(-size / 2f, -size / 2f, size / 2f, size / 2f), paint)
        val pivot = currentSpec.pivot
        if (pivot != null) {
            gridPaint.color = Color.YELLOW
            val px = (pivot.x.toFloat() / currentSpec.frameWidth - .5f) * size
            val py = (pivot.y.toFloat() / currentSpec.frameHeight - .5f) * size
            canvas.drawLine(px - 10f, py, px + 10f, py, gridPaint)
            canvas.drawLine(px, py - 10f, px, py + 10f, gridPaint)
        }
        canvas.restore()
        gridPaint.color = if (background == 2) Color.WHITE else Color.DKGRAY
        canvas.drawRect(left, top, left + size, top + size, gridPaint)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        scope.coroutineContext.cancel()
        bitmap?.let { if (!it.isRecycled) it.recycle() }
        super.onDetachedFromWindow()
    }
}
