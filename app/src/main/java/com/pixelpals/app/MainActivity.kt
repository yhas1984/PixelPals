package com.pixelpals.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.databinding.ActivityMainBinding
import com.pixelpals.app.feature.store.StoreActivity
import com.pixelpals.app.feature.treasure.TreasureAlbumActivity
import com.pixelpals.app.status.PetDashboardActivity
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.RootNavigation
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * MainActivity — Pantalla de Onboarding y Permisos
 *
 * Presenta al usuario una pantalla atractiva que:
 * 1. Explica qué hace la app
 * 2. Solicita permiso de overlay (SYSTEM_ALERT_WINDOW)
 * 3. Solicita permiso de notificaciones (Android 13+)
 * 4. Abre la selección de mascota cuando todo está listo
 */
class MainActivity : AppCompatActivity() {

    // ── View Binding ──────────────────────────────────────────
    private lateinit var binding: ActivityMainBinding
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(this) }
    private val selectedPetStore: SelectedPetStore by lazy { SelectedPetStore(this) }

    // ── Launchers ──────────────────────────────────────────────
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionUI()
        maybeAutoRequestNotification()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissionUI()
    }

    // ──────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.app_name)
        edgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        clearPetAfterTaskManagerStop()
        applySystemBarsInsets()
        setupRootNavigation()
        setupLanguageSelector()
        setupClickListeners()
        animateEntrance()
        updatePermissionUI()
    }

    override fun onResume() {
        super.onResume()
        PetService.refreshNotificationChannel(this)
        updatePermissionUI()
    }

    // ── Click Listeners ───────────────────────────────────────
    private fun setupLanguageSelector() {
        updateLanguageSelector()
        binding.btnLanguageSystem.setOnClickListener {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
        binding.btnLanguageEnglish.setOnClickListener {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
        binding.btnLanguageSpanish.setOnClickListener {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("es"))
        }
    }

    private fun updateLanguageSelector() {
        val language = AppCompatDelegate.getApplicationLocales().get(0)?.language
        binding.btnLanguageSystem.isChecked = language == null
        binding.btnLanguageEnglish.isChecked = language == "en"
        binding.btnLanguageSpanish.isChecked = language == "es"
    }

    private fun clearPetAfterTaskManagerStop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !selectedPetStore.isPetEnabled()) return
        val enabledAt = selectedPetStore.getPetEnabledAt() ?: return
        val activityManager = getSystemService(ActivityManager::class.java) ?: return
        val lastExit = activityManager.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull()
        if (lastExit?.reason == ApplicationExitInfo.REASON_USER_REQUESTED && lastExit.timestamp >= enabledAt) {
            selectedPetStore.setPetEnabled(false)
        }
    }

    private fun setupClickListeners() {
        binding.btnOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnNotification.setOnClickListener {
            requestNotificationPermission()
        }

        binding.btnUsage.setOnClickListener {
            analytics.track("usage_access_requested")
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        binding.btnLaunch.setOnClickListener {
            analytics.track("main_launch_tapped")
            launchPet()
        }

        binding.btnStopPet.setOnClickListener {
            PetService.stopPet(this)
            updatePermissionUI()
            Toast.makeText(this, getString(R.string.pet_stopped), Toast.LENGTH_SHORT).show()
        }

        binding.btnAlbum.setOnClickListener {
            analytics.track("album_opened")
            val intent = Intent(this, TreasureAlbumActivity::class.java)
            startActivity(intent)
        }

        binding.btnDashboard.setOnClickListener {
            analytics.track("dashboard_opened_from_main")
            startActivity(Intent(this, PetDashboardActivity::class.java))
        }

        binding.btnStore.setOnClickListener {
            analytics.track("store_opened_from_main")
            startActivity(Intent(this, StoreActivity::class.java))
        }
    }

    private fun setupRootNavigation() {
        val navigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        RootNavigation.install(this, PixelPalsDestination.HOME, navigation)
        ViewCompat.setOnApplyWindowInsetsListener(navigation) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(navigation)
    }

    // ── Permissions ───────────────────────────────────────────
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            analytics.track("overlay_permission_requested")
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
                analytics.track("notification_permission_requested")
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private var autoNotificationRequested = false

    private fun maybeAutoRequestNotification() {
        if (autoNotificationRequested) return
        if (hasOverlayPermission() && !hasNotificationPermission()) {
            autoNotificationRequested = true
            requestNotificationPermission()
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

    private fun hasUsageAccessForDesktopOnly(): Boolean {
        return DesktopForegroundHelper.hasUsageAccess(this)
    }

    // ── Update UI ─────────────────────────────────────────────
    private fun updatePermissionUI() {
        val overlayGranted = hasOverlayPermission()
        val notifGranted = hasNotificationPermission()
        val usageGranted = hasUsageAccessForDesktopOnly()

        binding.txtPermissionSummary.text = getString(
            R.string.usage_optional_note
        )

        val launchReason = when {
            !overlayGranted && !notifGranted -> getString(R.string.launch_disabled_reason_both)
            !overlayGranted -> getString(R.string.launch_disabled_reason_overlay)
            !notifGranted -> getString(R.string.launch_disabled_reason_notification)
            else -> getString(R.string.launch_ready)
        }
        binding.txtLaunchReason.text = launchReason
        binding.txtLaunchReason.setTextColor(
            ContextCompat.getColor(
                this,
                if (overlayGranted && notifGranted) R.color.status_success_fg else R.color.status_info_fg
            )
        )
        binding.btnLaunch.contentDescription = if (overlayGranted && notifGranted) {
            getString(R.string.launch_ready)
        } else {
            launchReason
        }

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

        if (usageGranted) {
            binding.statusUsage.text = getString(R.string.permission_granted)
            binding.statusUsage.setTextColor(ContextCompat.getColor(this, R.color.green_success))
            binding.iconUsage.setColorFilter(ContextCompat.getColor(this, R.color.green_success))
            binding.btnUsage.isEnabled = false
            binding.btnUsage.alpha = 0.5f
            binding.btnUsage.text = getString(R.string.permission_granted)
        } else {
            binding.statusUsage.text = getString(R.string.permission_required)
            binding.statusUsage.setTextColor(ContextCompat.getColor(this, R.color.coral_accent))
            binding.iconUsage.setColorFilter(ContextCompat.getColor(this, R.color.coral_accent))
            binding.btnUsage.isEnabled = true
            binding.btnUsage.alpha = 1f
            binding.btnUsage.text = getString(R.string.grant_permission)
        }

        // Launch button — only enabled when both permissions granted
        val allGranted = overlayGranted && notifGranted
        binding.btnLaunch.isEnabled = allGranted
        binding.btnLaunch.alpha = if (allGranted) 1f else 0.4f
        val petEnabled = selectedPetStore.isPetEnabled()
        binding.btnStopPet.isEnabled = petEnabled
        binding.btnStopPet.alpha = if (petEnabled) 1f else 0.5f
        binding.btnStopPet.text = getString(
            if (petEnabled) R.string.stop_pet else R.string.pet_stopped
        )
        binding.txtPermissionSummary.text = if (usageGranted) {
            getString(R.string.permissions_optional_label)
        } else {
            getString(R.string.usage_optional_note)
        }
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
    }

    // ── Entrance Animations ───────────────────────────────────
    private fun animateEntrance() {
        val views = listOf(
            binding.titleText,
            binding.subtitleText,
            binding.languageSelector,
            binding.cardPermissionSummary,
            binding.cardOverlay,
            binding.cardNotification,
            binding.cardUsage,
            binding.btnLaunch,
            binding.btnStopPet,
            binding.btnAlbum,
            binding.btnDashboard,
            binding.btnStore
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

    private fun edgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.surface_base)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.surface_base)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun applySystemBarsInsets() {
        val view = binding.mainScroll
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
