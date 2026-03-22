package com.pixelpals.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * MainActivity — Pantalla de Onboarding y Permisos
 *
 * Presenta al usuario una pantalla atractiva que:
 * 1. Explica qué hace la app
 * 2. Solicita permiso de overlay (SYSTEM_ALERT_WINDOW)
 * 3. Solicita permiso de notificaciones (Android 13+)
 * 4. Lanza el PetService cuando todo está listo
 */
class MainActivity : AppCompatActivity() {

    // ── Launchers ──────────────────────────────────────────────
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionUI()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissionUI()
    }

    // ── Views ──────────────────────────────────────────────────
    private lateinit var btnOverlay: Button
    private lateinit var btnNotification: Button
    private lateinit var btnLaunch: Button
    private lateinit var btnAlbum: Button
    private lateinit var iconOverlay: ImageView
    private lateinit var iconNotification: ImageView
    private lateinit var statusOverlay: TextView
    private lateinit var statusNotification: TextView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var cardOverlay: LinearLayout
    private lateinit var cardNotification: LinearLayout

    // ──────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupClickListeners()
        animateEntrance()
        updatePermissionUI()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()
    }

    // ── View Binding (manual) ─────────────────────────────────
    private fun bindViews() {
        titleText = findViewById(R.id.titleText)
        subtitleText = findViewById(R.id.subtitleText)
        cardOverlay = findViewById(R.id.cardOverlay)
        cardNotification = findViewById(R.id.cardNotification)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnNotification = findViewById(R.id.btnNotification)
        btnLaunch = findViewById(R.id.btnLaunch)
        btnAlbum = findViewById(R.id.btnAlbum)
        iconOverlay = findViewById(R.id.iconOverlay)
        iconNotification = findViewById(R.id.iconNotification)
        statusOverlay = findViewById(R.id.statusOverlay)
        statusNotification = findViewById(R.id.statusNotification)
    }

    // ── Click Listeners ───────────────────────────────────────
    private fun setupClickListeners() {
        btnOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        btnNotification.setOnClickListener {
            requestNotificationPermission()
        }

        btnLaunch.setOnClickListener {
            launchPet()
        }

        btnAlbum.setOnClickListener {
            val intent = Intent(this, TreasureAlbumActivity::class.java)
            startActivity(intent)
        }
    }

    // ── Permissions ───────────────────────────────────────────
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-13 doesn't need runtime permission
        }
    }

    // ── Update UI ─────────────────────────────────────────────
    private fun updatePermissionUI() {
        val overlayGranted = hasOverlayPermission()
        val notifGranted = hasNotificationPermission()

        // Overlay card
        if (overlayGranted) {
            statusOverlay.text = getString(R.string.permission_granted)
            statusOverlay.setTextColor(ContextCompat.getColor(this, R.color.green_success))
            iconOverlay.setColorFilter(ContextCompat.getColor(this, R.color.green_success))
            btnOverlay.isEnabled = false
            btnOverlay.alpha = 0.5f
            btnOverlay.text = getString(R.string.permission_granted)
        } else {
            statusOverlay.text = getString(R.string.permission_required)
            statusOverlay.setTextColor(ContextCompat.getColor(this, R.color.coral_accent))
            iconOverlay.setColorFilter(ContextCompat.getColor(this, R.color.coral_accent))
            btnOverlay.isEnabled = true
            btnOverlay.alpha = 1f
            btnOverlay.text = getString(R.string.grant_permission)
        }

        // Notification card
        if (notifGranted) {
            statusNotification.text = getString(R.string.permission_granted)
            statusNotification.setTextColor(ContextCompat.getColor(this, R.color.green_success))
            iconNotification.setColorFilter(ContextCompat.getColor(this, R.color.green_success))
            btnNotification.isEnabled = false
            btnNotification.alpha = 0.5f
            btnNotification.text = getString(R.string.permission_granted)
        } else {
            statusNotification.text = getString(R.string.permission_required)
            statusNotification.setTextColor(ContextCompat.getColor(this, R.color.coral_accent))
            iconNotification.setColorFilter(ContextCompat.getColor(this, R.color.coral_accent))
            btnNotification.isEnabled = true
            btnNotification.alpha = 1f
            btnNotification.text = getString(R.string.grant_permission)
        }

        // Launch button — only enabled when both permissions granted
        val allGranted = overlayGranted && notifGranted
        btnLaunch.isEnabled = allGranted
        btnLaunch.alpha = if (allGranted) 1f else 0.4f
    }

    // ── Launch Pet ────────────────────────────────────────────
    private fun launchPet() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, getString(R.string.overlay_needed), Toast.LENGTH_SHORT).show()
            return
        }

        // Navigate to pet selection screen
        val intent = Intent(this, PetSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }

    // ── Entrance Animations ───────────────────────────────────
    private fun animateEntrance() {
        val views = listOf(titleText, subtitleText, cardOverlay, cardNotification, btnLaunch)
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 60f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay((index * 120).toLong())
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }
}
