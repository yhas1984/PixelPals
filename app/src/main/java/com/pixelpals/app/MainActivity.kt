package com.pixelpals.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pixelpals.app.databinding.ActivityMainBinding

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

    // ── View Binding ──────────────────────────────────────────
    private lateinit var binding: ActivityMainBinding

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

    // ──────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        animateEntrance()
        updatePermissionUI()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()
    }

    // ── Click Listeners ───────────────────────────────────────
    private fun setupClickListeners() {
        binding.btnOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnNotification.setOnClickListener {
            requestNotificationPermission()
        }

        binding.btnLaunch.setOnClickListener {
            launchPet()
        }

        binding.btnAlbum.setOnClickListener {
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
            binding.statusOverlay.text = getString(R.string.permission_granted)
            binding.statusOverlay.setTextColor(ContextCompat.getColor(this, R.color.green_success))
            binding.iconOverlay.setColorFilter(ContextCompat.getColor(this, R.color.green_success))
            binding.btnOverlay.isEnabled = false
            binding.btnOverlay.alpha = 0.5f
            binding.btnOverlay.text = getString(R.string.permission_granted)
        } else {
            binding.statusOverlay.text = getString(R.string.permission_required)
            binding.statusOverlay.setTextColor(ContextCompat.getColor(this, R.color.coral_accent))
            binding.iconOverlay.setColorFilter(ContextCompat.getColor(this, R.color.coral_accent))
            binding.btnOverlay.isEnabled = true
            binding.btnOverlay.alpha = 1f
            binding.btnOverlay.text = getString(R.string.grant_permission)
        }

        // Notification card
        if (notifGranted) {
            binding.statusNotification.text = getString(R.string.permission_granted)
            binding.statusNotification.setTextColor(ContextCompat.getColor(this, R.color.green_success))
            binding.iconNotification.setColorFilter(ContextCompat.getColor(this, R.color.green_success))
            binding.btnNotification.isEnabled = false
            binding.btnNotification.alpha = 0.5f
            binding.btnNotification.text = getString(R.string.permission_granted)
        } else {
            binding.statusNotification.text = getString(R.string.permission_required)
            binding.statusNotification.setTextColor(ContextCompat.getColor(this, R.color.coral_accent))
            binding.iconNotification.setColorFilter(ContextCompat.getColor(this, R.color.coral_accent))
            binding.btnNotification.isEnabled = true
            binding.btnNotification.alpha = 1f
            binding.btnNotification.text = getString(R.string.grant_permission)
        }

        // Launch button — only enabled when both permissions granted
        val allGranted = overlayGranted && notifGranted
        binding.btnLaunch.isEnabled = allGranted
        binding.btnLaunch.alpha = if (allGranted) 1f else 0.4f
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
        val views = listOf(
            binding.titleText,
            binding.subtitleText,
            binding.cardOverlay,
            binding.cardNotification,
            binding.btnLaunch
        )
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
