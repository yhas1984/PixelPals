package com.pixelpals.app.debug

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pixelpals.app.R

class LumiMasterPoseReviewActivity : AppCompatActivity() {
    private val handler: Handler = Handler(Looper.getMainLooper())
    private lateinit var imageView: ImageView
    private lateinit var poseName: TextView
    private lateinit var poseStatus: TextView
    private lateinit var indexText: TextView
    private lateinit var playButton: Button
    private var poseIndex: Int = 0
    private var isPlaying: Boolean = true
    private val poses: List<Pose> = listOf(
        Pose("idle_neutral", "CANDIDATE • NEEDS RETOUCH", R.drawable.debug_lumi_idle_neutral),
        Pose("idle_breath_in", "MANUAL PAINT REQUIRED", R.drawable.debug_lumi_idle_breath_in),
        Pose("look_profile", "MANUAL PAINT REQUIRED", R.drawable.debug_lumi_look_profile),
        Pose("walk_contact_right", "CANDIDATE • NEEDS RETOUCH", R.drawable.debug_lumi_walk_contact_right),
        Pose("pounce_air", "CANDIDATE • NEEDS RETOUCH", R.drawable.debug_lumi_pounce_air),
        Pose("sleep_curl", "CANDIDATE • NEEDS RETOUCH", R.drawable.debug_lumi_sleep_curl),
    )

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
        showPose()
        scheduleNextPose()
    }

    override fun onDestroy(): Unit {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun createContent(): View {
        val density = resources.displayMetrics.density
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (28 * density).toInt(), (20 * density).toInt(), (28 * density).toInt())
            setBackgroundColor(Color.rgb(16, 25, 34))
        }
        val title = createText(getString(R.string.debug_lumi_review_title), 28f, Color.rgb(255, 244, 221), true)
        val subtitle = createText(getString(R.string.debug_lumi_review_subtitle), 14f, Color.rgb(184, 197, 206), false)
        imageView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            setBackgroundColor(Color.rgb(38, 53, 64))
            contentDescription = getString(R.string.debug_lumi_review_image_description)
        }
        poseName = createText("", 22f, Color.rgb(255, 195, 106), true)
        poseStatus = createText("", 14f, Color.rgb(255, 173, 158), true)
        indexText = createText("", 13f, Color.rgb(184, 197, 206), false)
        val previous = createButton(getString(R.string.debug_lumi_review_previous)) { showPreviousPose() }
        val next = createButton(getString(R.string.debug_lumi_review_next)) { showNextPose() }
        playButton = createButton(getString(R.string.debug_lumi_review_pause)) { togglePlayback() }
        val overlay = createButton(getString(R.string.debug_lumi_review_overlay)) { startOverlay() }
        val permissions = createButton(getString(R.string.debug_lumi_permission)) {
            startActivity(android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(previous, buttonParams(density))
        controls.addView(next, buttonParams(density))
        controls.addView(playButton, buttonParams(density))
        root.addView(title)
        root.addView(subtitle, textParams(density, 8))
        root.addView(imageView, imageParams(density))
        root.addView(poseName, textParams(density, 14))
        root.addView(poseStatus, textParams(density, 4))
        root.addView(indexText, textParams(density, 4))
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        root.addView(overlay, fullButtonParams(density))
        root.addView(permissions, fullButtonParams(density))
        scroll.addView(root)
        return scroll
    }

    private fun createText(text: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    private fun createButton(text: String, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }
    }

    private fun imageParams(density: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, (minOf(430f, resources.displayMetrics.widthPixels / density - 40f) * density).toInt()).apply {
            topMargin = (18 * density).toInt()
        }
    }

    private fun textParams(density: Float, topMarginDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, -2).apply { topMargin = (topMarginDp * density).toInt() }
    }

    private fun buttonParams(density: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, (52 * density).toInt(), 1f).apply {
            setMargins((3 * density).toInt(), (6 * density).toInt(), (3 * density).toInt(), 0)
        }
    }

    private fun fullButtonParams(density: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, (52 * density).toInt()).apply {
            setMargins(0, (8 * density).toInt(), 0, 0)
        }
    }

    private fun showPose(): Unit {
        val pose = poses[poseIndex]
        imageView.setImageDrawable(BitmapDrawable(resources, BitmapFactory.decodeResource(resources, pose.drawableId)))
        poseName.text = pose.name
        poseStatus.text = pose.status
        indexText.text = getString(R.string.debug_lumi_review_index, poseIndex + 1, poses.size)
    }

    private fun showPreviousPose(): Unit {
        poseIndex = (poseIndex - 1 + poses.size) % poses.size
        showPose()
    }

    private fun showNextPose(): Unit {
        poseIndex = (poseIndex + 1) % poses.size
        showPose()
    }

    private fun togglePlayback(): Unit {
        isPlaying = !isPlaying
        playButton.text = getString(if (isPlaying) R.string.debug_lumi_review_pause else R.string.debug_lumi_review_play)
        if (isPlaying) scheduleNextPose() else handler.removeCallbacksAndMessages(null)
    }

    private fun scheduleNextPose(): Unit {
        handler.removeCallbacksAndMessages(null)
        if (!isPlaying) return
        handler.postDelayed({ showNextPose(); scheduleNextPose() }, 2200L)
    }

    private fun startOverlay(): Unit {
        if (Settings.canDrawOverlays(this)) {
            LumiDebugOverlayService.start(this)
        } else {
            startActivity(android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
        }
    }

    private data class Pose(val name: String, val status: String, val drawableId: Int)
}
