package com.pixelpals.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.databinding.ActivityMainBinding
import com.pixelpals.app.feature.treasure.TreasureAlbumActivity
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.RootNavigator
import com.pixelpals.app.status.PetDashboardActivity

class HomeFragment : Fragment() {
    companion object {
        private const val STATE_HAS_ANIMATED: String = "home_has_animated"
    }

    private var bindingReference: ActivityMainBinding? = null
    private val binding: ActivityMainBinding
        get() = requireNotNull(bindingReference)
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(requireContext()) }
    private val selectedPetStore: SelectedPetStore by lazy { SelectedPetStore(requireContext()) }
    private var hasAnimated: Boolean = false
    private var isNotificationRequestAutomatic: Boolean = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        updatePermissionUi()
        requestNotificationAutomatically()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        updatePermissionUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasAnimated = savedInstanceState?.getBoolean(STATE_HAS_ANIMATED) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val inflatedBinding: ActivityMainBinding = ActivityMainBinding.inflate(inflater, container, false)
        bindingReference = inflatedBinding
        return inflatedBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureLanguageSelector()
        configureActions()
        updatePermissionUi()
        if (!hasAnimated) {
            animateEntrance()
            hasAnimated = true
        }
    }

    override fun onResume() {
        super.onResume()
        PetService.refreshNotificationChannel(requireContext())
        updatePermissionUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_HAS_ANIMATED, hasAnimated)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        bindingReference = null
        super.onDestroyView()
    }

    private fun configureLanguageSelector() {
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

    private fun configureActions() {
        binding.btnOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnNotification.setOnClickListener { requestNotificationPermission() }
        binding.btnUsage.setOnClickListener {
            analytics.track("usage_access_requested")
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnLaunch.setOnClickListener {
            analytics.track("main_launch_tapped")
            openPetSelection()
        }
        binding.btnStopPet.setOnClickListener {
            PetService.stopPet(requireContext())
            updatePermissionUi()
            Toast.makeText(requireContext(), getString(R.string.pet_stopped), Toast.LENGTH_SHORT).show()
        }
        binding.btnAlbum.setOnClickListener {
            analytics.track("album_opened")
            startActivity(Intent(requireContext(), TreasureAlbumActivity::class.java))
        }
        binding.btnDashboard.setOnClickListener {
            analytics.track("dashboard_opened_from_main")
            startActivity(Intent(requireContext(), PetDashboardActivity::class.java))
        }
    }

    private fun updateLanguageSelector() {
        val language: String? = AppCompatDelegate.getApplicationLocales().get(0)?.language
        binding.btnLanguageSystem.isChecked = language == null
        binding.btnLanguageEnglish.isChecked = language == "en"
        binding.btnLanguageSpanish.isChecked = language == "es"
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(requireContext())) return
        analytics.track("overlay_permission_requested")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${requireContext().packageName}"),
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val isGranted: Boolean = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!isGranted) {
            analytics.track("notification_permission_requested")
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestNotificationAutomatically() {
        if (isNotificationRequestAutomatic) return
        if (hasOverlayPermission() && !hasNotificationPermission()) {
            isNotificationRequestAutomatic = true
            requestNotificationPermission()
        }
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(requireContext())

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasUsageAccess(): Boolean = DesktopForegroundHelper.hasUsageAccess(requireContext())

    private fun updatePermissionUi() {
        if (bindingReference == null) return
        val isOverlayGranted: Boolean = hasOverlayPermission()
        val isNotificationGranted: Boolean = hasNotificationPermission()
        val isUsageGranted: Boolean = hasUsageAccess()
        updatePermissionSummary(isOverlayGranted, isNotificationGranted, isUsageGranted)
        updateOverlayCard(isOverlayGranted)
        updateNotificationCard(isNotificationGranted)
        updateUsageCard(isUsageGranted)
        val areRequiredPermissionsGranted: Boolean = isOverlayGranted && isNotificationGranted
        binding.btnLaunch.isEnabled = areRequiredPermissionsGranted
        binding.btnLaunch.alpha = if (areRequiredPermissionsGranted) 1f else 0.4f
        val isPetEnabled: Boolean = selectedPetStore.isPetEnabled()
        binding.btnStopPet.isEnabled = isPetEnabled
        binding.btnStopPet.alpha = if (isPetEnabled) 1f else 0.5f
        binding.btnStopPet.text = getString(if (isPetEnabled) R.string.stop_pet else R.string.pet_stopped)
    }

    private fun updatePermissionSummary(
        isOverlayGranted: Boolean,
        isNotificationGranted: Boolean,
        isUsageGranted: Boolean,
    ) {
        val launchReason: String = when {
            !isOverlayGranted && !isNotificationGranted -> getString(R.string.launch_disabled_reason_both)
            !isOverlayGranted -> getString(R.string.launch_disabled_reason_overlay)
            !isNotificationGranted -> getString(R.string.launch_disabled_reason_notification)
            else -> getString(R.string.launch_ready)
        }
        binding.txtLaunchReason.text = launchReason
        binding.txtLaunchReason.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isOverlayGranted && isNotificationGranted) {
                    R.color.status_success_fg
                } else {
                    R.color.status_info_fg
                },
            ),
        )
        binding.btnLaunch.contentDescription = launchReason
        binding.txtPermissionSummary.text = getString(
            if (isUsageGranted) R.string.permissions_optional_label else R.string.usage_optional_note,
        )
    }

    private fun updateOverlayCard(isGranted: Boolean) {
        val color: Int = ContextCompat.getColor(
            requireContext(),
            if (isGranted) R.color.green_success else R.color.coral_accent,
        )
        binding.statusOverlay.text = getString(
            if (isGranted) R.string.permission_granted else R.string.permission_required,
        )
        binding.statusOverlay.setTextColor(color)
        binding.iconOverlay.setColorFilter(color)
        binding.btnOverlay.isEnabled = !isGranted
        binding.btnOverlay.alpha = if (isGranted) 0.5f else 1f
        binding.btnOverlay.text = getString(
            if (isGranted) R.string.permission_granted else R.string.grant_permission,
        )
    }

    private fun updateNotificationCard(isGranted: Boolean) {
        val color: Int = ContextCompat.getColor(
            requireContext(),
            if (isGranted) R.color.green_success else R.color.coral_accent,
        )
        binding.statusNotification.text = getString(
            if (isGranted) R.string.permission_granted else R.string.permission_required,
        )
        binding.statusNotification.setTextColor(color)
        binding.iconNotification.setColorFilter(color)
        binding.btnNotification.isEnabled = !isGranted
        binding.btnNotification.alpha = if (isGranted) 0.5f else 1f
        binding.btnNotification.text = getString(
            if (isGranted) R.string.permission_granted else R.string.grant_permission,
        )
    }

    private fun updateUsageCard(isGranted: Boolean) {
        val color: Int = ContextCompat.getColor(
            requireContext(),
            if (isGranted) R.color.green_success else R.color.coral_accent,
        )
        binding.statusUsage.text = getString(
            if (isGranted) R.string.permission_granted else R.string.permission_required,
        )
        binding.statusUsage.setTextColor(color)
        binding.iconUsage.setColorFilter(color)
        binding.btnUsage.isEnabled = !isGranted
        binding.btnUsage.alpha = if (isGranted) 0.5f else 1f
        binding.btnUsage.text = getString(
            if (isGranted) R.string.permission_granted else R.string.grant_permission,
        )
    }

    private fun openPetSelection() {
        if (!hasOverlayPermission()) {
            Toast.makeText(requireContext(), getString(R.string.overlay_needed), Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasNotificationPermission()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.notification_needed),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        (requireActivity() as RootNavigator).navigate(PixelPalsDestination.PETS)
    }

    private fun animateEntrance() {
        val views: List<View> = listOf(
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
        )
        views.forEachIndexed { index, item ->
            item.alpha = 0f
            item.translationY = 60f
            item.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay((index * 120).toLong())
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }
}
