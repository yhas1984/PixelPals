package com.pixelpals.app.debug

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TaroDebugActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var reviewText: TextView
    private var direction = 1f
    private var speed = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
        if (Settings.canDrawOverlays(this)) {
            TaroDebugOverlayService.start(this)
            TaroDebugOverlayService.setSpeed(this, speed)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            statusText.text = if (Settings.canDrawOverlays(this)) {
                getString(com.pixelpals.app.R.string.debug_taro_active)
            } else {
                getString(com.pixelpals.app.R.string.debug_taro_permission_needed)
            }
        }
    }

    private fun createContent(): LinearLayout {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * density).toInt(), (32 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        }
        root.addView(TextView(this).apply {
            text = getString(com.pixelpals.app.R.string.debug_taro_title)
            textSize = 26f
        })
        statusText = TextView(this).apply {
            textSize = 16f
            setPadding(0, (16 * density).toInt(), 0, (12 * density).toInt())
        }
        root.addView(statusText)
        root.addView(Button(this).apply {
            text = getString(com.pixelpals.app.R.string.debug_taro_permission)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            }
        })
        root.addView(TextView(this).apply {
            text = getString(com.pixelpals.app.R.string.debug_taro_review_section)
            textSize = 20f
            setPadding(0, (20 * density).toInt(), 0, (4 * density).toInt())
        })
        root.addView(TextView(this).apply {
            text = getString(com.pixelpals.app.R.string.debug_taro_review_hint)
            textSize = 14f
        })
        reviewText = TextView(this).apply {
            textSize = 14f
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }
        root.addView(reviewText)
        lateinit var speedButton: Button
        speedButton = button(getSpeedLabel()) {
            speed = when (speed) {
                1f -> 2f
                2f -> 4f
                else -> 1f
            }
            speedButton.text = getSpeedLabel()
            TaroDebugOverlayService.setSpeed(this, speed)
            updateReviewText()
        }
        root.addView(row(
            button(getString(com.pixelpals.app.R.string.debug_taro_direction)) { direction *= -1f; updateReviewText() },
            speedButton,
            button(getString(com.pixelpals.app.R.string.debug_taro_autonomous)) {
                TaroDebugOverlayService.resumeAutonomous(this)
                reviewText.text = getString(com.pixelpals.app.R.string.debug_taro_autonomous_active)
            },
        ))
        val clips = TaroReviewClip.entries.toList()
        clips.chunked(3).forEach { group ->
            root.addView(row(*group.map { clip ->
                button(clip.clipId.replace('_', ' ')) {
                    TaroDebugOverlayService.startManualReview(this, clip, direction, speed)
                    reviewText.text = getString(com.pixelpals.app.R.string.debug_taro_reviewing, clip.clipId)
                }
            }.toTypedArray()))
        }
        root.addView(Button(this).apply {
            text = getString(com.pixelpals.app.R.string.debug_taro_stop)
            setOnClickListener { TaroDebugOverlayService.stop(this@TaroDebugActivity) }
        })
        updateReviewText()
        return root
    }

    private fun row(vararg buttons: Button): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        buttons.forEach { button ->
            button.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(button)
        }
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun updateReviewText() {
        if (::reviewText.isInitialized) {
            reviewText.text = getString(
                com.pixelpals.app.R.string.debug_taro_review_settings,
                if (direction > 0f) "right" else "left",
                getSpeedLabel(),
            )
        }
    }

    private fun getSpeedLabel(): String = when (speed) {
        1f -> "Speed 1x"
        2f -> "Speed 2x"
        else -> "Speed 4x"
    }
}
