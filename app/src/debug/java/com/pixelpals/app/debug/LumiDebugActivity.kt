package com.pixelpals.app.debug

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pixelpals.app.R

class LumiDebugActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var reviewStatusText: TextView
    private var reviewDirection: Float = 1f
    private var reviewSpeed: Float = 1f

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
        if (Settings.canDrawOverlays(this)) {
            LumiDebugOverlayService.start(this)
            LumiDebugOverlayService.setReviewInputEnabled(this, true)
        }
    }

    override fun onResume(): Unit {
        super.onResume()
        updateStatus()
    }

    private fun createContent(): LinearLayout {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * density).toInt(), (32 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        }
        val title = TextView(this).apply {
            text = getString(R.string.debug_lumi_title)
            textSize = 26f
        }
        statusText = TextView(this).apply {
            textSize = 16f
            setPadding(0, (16 * density).toInt(), 0, (16 * density).toInt())
        }
        val permissionButton = Button(this).apply {
            text = getString(R.string.debug_lumi_permission)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            }
        }
        val stopButton = Button(this).apply {
            text = getString(R.string.debug_lumi_stop)
            setOnClickListener {
                LumiDebugOverlayService.stop(this@LumiDebugActivity)
                updateStatus()
            }
        }
        val reviewTitle = TextView(this).apply {
            text = getString(R.string.debug_lumi_review_section)
            textSize = 20f
            setPadding(0, (24 * density).toInt(), 0, (4 * density).toInt())
        }
        val reviewHint = TextView(this).apply {
            text = getString(R.string.debug_lumi_review_hint)
            textSize = 14f
        }
        reviewStatusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }
        val directionButton = Button(this).apply {
            text = getDirectionLabel()
            setOnClickListener {
                reviewDirection *= -1f
                text = getDirectionLabel()
            }
        }
        val speedButton = Button(this).apply {
            text = getSpeedLabel()
            setOnClickListener {
                reviewSpeed = if (reviewSpeed == 1f) 0.25f else 1f
                text = getSpeedLabel()
            }
        }
        val autonomousButton = Button(this).apply {
            text = getString(R.string.debug_lumi_review_autonomous)
            setOnClickListener {
                LumiDebugOverlayService.resumeAutonomous(this@LumiDebugActivity)
                reviewStatusText.text = getString(R.string.debug_lumi_review_autonomous_active)
            }
        }
        root.addView(title)
        root.addView(statusText)
        root.addView(permissionButton)
        root.addView(reviewTitle)
        root.addView(reviewHint)
        root.addView(reviewStatusText)
        root.addView(createButtonRow(directionButton, speedButton, autonomousButton))
        root.addView(createButtonRow(
            createReviewButton(R.string.debug_lumi_review_idle, LumiReviewClip.IDLE),
            createReviewButton(R.string.debug_lumi_review_walk, LumiReviewClip.WALK),
            createReviewButton(R.string.debug_lumi_review_turn, LumiReviewClip.TURN),
        ))
        root.addView(createButtonRow(
            createReviewButton(R.string.debug_lumi_review_hop_up, LumiReviewClip.HOP_UP),
            createReviewButton(R.string.debug_lumi_review_hop_down, LumiReviewClip.HOP_DOWN),
            createReviewButton(R.string.debug_lumi_review_front, LumiReviewClip.FRONT_SOCIAL),
        ))
        root.addView(createButtonRow(
            createReviewButton(R.string.debug_lumi_review_pounce, LumiReviewClip.POUNCE),
            createReviewButton(R.string.debug_lumi_review_sleep, LumiReviewClip.SLEEP),
            createReviewButton(R.string.debug_lumi_review_magic, LumiReviewClip.MAGIC),
        ))
        root.addView(stopButton)
        return root
    }

    private fun createButtonRow(vararg buttons: Button): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        buttons.forEach { button ->
            button.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(button)
        }
        return row
    }

    private fun createReviewButton(labelRes: Int, clip: LumiReviewClip): Button {
        return Button(this).apply {
            text = getString(labelRes)
            setOnClickListener {
                LumiDebugOverlayService.startManualReview(this@LumiDebugActivity, clip, reviewDirection, reviewSpeed)
                reviewStatusText.text = getString(R.string.debug_lumi_review_playing, getString(labelRes))
            }
        }
    }

    private fun getDirectionLabel(): String {
        return getString(if (reviewDirection > 0f) R.string.debug_lumi_review_direction_right else R.string.debug_lumi_review_direction_left)
    }

    private fun getSpeedLabel(): String {
        return getString(if (reviewSpeed == 1f) R.string.debug_lumi_review_speed_real else R.string.debug_lumi_review_speed_slow)
    }

    private fun updateStatus(): Unit {
        if (!::statusText.isInitialized) return
        statusText.text = if (Settings.canDrawOverlays(this)) {
            getString(R.string.debug_lumi_active)
        } else {
            getString(R.string.debug_lumi_permission_needed)
        }
    }
}
