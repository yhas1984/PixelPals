package com.pixelpals.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pixelpals.app.status.PetDashboardActivity

class PetService : Service() {

    companion object {
        private const val TAG = "PetService"
        @Volatile
        var isRunning: Boolean = false
        const val CHANNEL_ID = "pixelpals_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_HIDE = "com.pixelpals.app.ACTION_HIDE"
        const val ACTION_SHOW = "com.pixelpals.app.ACTION_SHOW"
        const val ACTION_STOP = "com.pixelpals.app.ACTION_STOP"
        const val ACTION_CONSUME_TREASURE = "com.pixelpals.app.ACTION_CONSUME_TREASURE"
        const val ACTION_REFRESH_PET = "com.pixelpals.app.ACTION_REFRESH_PET"
        private const val EXTRA_REFRESH_MESSAGE = "REFRESH_MESSAGE"
        private const val EXTRA_REFRESH_CELEBRATE = "REFRESH_CELEBRATE"
        private const val PET_SIZE_DP = 80
        private const val HOME_POLL_INTERVAL_MS = 2000L
        private const val HOME_POLL_INTERVAL_SLOW_MS = 30_000L

        fun requestPetRefresh(context: Context, message: String? = null, celebrate: Boolean = false) {
            if (!isRunning) return
            val intent = Intent(context, PetService::class.java).apply {
                action = ACTION_REFRESH_PET
                putExtra(EXTRA_REFRESH_MESSAGE, message)
                putExtra(EXTRA_REFRESH_CELEBRATE, celebrate)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestPetChange(context: Context, petType: PetType) {
            if (!isRunning) return
            val intent = Intent(context, PetService::class.java).apply {
                putExtra(PetSelectionActivity.EXTRA_PET_TYPE, petType.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var petView: PetView? = null
    private var screenReceiver: ScreenStateReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var airplaneReceiver: BroadcastReceiver? = null
    private var isViewAttached = false
    private var currentPetType: PetType = PetType.CORGI
    private var isForegroundStarted = false
    private lateinit var selectedPetStore: SelectedPetStore

    private val homeCheckHandler = Handler(Looper.getMainLooper())
    private var userManuallyHidden = false
    private var lastAppliedPetVisible: Boolean? = null

    private val baseOverlayFlags =
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    private val homeCheckRunnable = object : Runnable {
        override fun run() {
            try {
                if (isViewAttached) {
                    refreshKeyboardVisibility()
                    val hadUsageAccess = DesktopForegroundHelper.hasUsageAccess(this@PetService)
                    refreshPetVisibilityForForeground()
                    val nextInterval = if (hadUsageAccess) {
                        HOME_POLL_INTERVAL_MS
                    } else {
                        HOME_POLL_INTERVAL_SLOW_MS
                    }
                    homeCheckHandler.postDelayed(this, nextInterval)
                } else {
                    homeCheckHandler.postDelayed(this, HOME_POLL_INTERVAL_SLOW_MS)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Polling cycle failed; keeping service alive", e)
                homeCheckHandler.postDelayed(this, HOME_POLL_INTERVAL_MS)
            }
        }
    }

    private var lastImeVisible: Boolean? = null

    private fun refreshKeyboardVisibility() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val wm = windowManager ?: return
        val metrics = runCatching { wm.currentWindowMetrics }.getOrNull() ?: return
        val insets = metrics.windowInsets
        val visible = insets.isVisible(WindowInsets.Type.ime())
        if (visible == lastImeVisible) return
        lastImeVisible = visible
        val height = insets.getInsets(WindowInsets.Type.ime()).bottom
        petView?.onKeyboardChanged(visible, height)
        if (!visible) {
            // Re-afirma la posición tras cerrar el teclado por si el sistema re-layout la ventana.
            val view = petView ?: return
            val params = view.getWindowParams() ?: return
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        selectedPetStore = SelectedPetStore(this)
        currentPetType = selectedPetStore.load()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != ACTION_STOP) ensureForeground()

        when (intent?.action) {
            ACTION_HIDE -> {
                userManuallyHidden = true
                applyPetOverlayVisible(false)
                return START_STICKY
            }
            ACTION_SHOW -> {
                userManuallyHidden = false
                applyPetOverlayVisible(shouldShowPetForPolicy())
                return START_STICKY
            }
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_CONSUME_TREASURE -> {
                ensureOverlayReady()
                val emoji = intent.getStringExtra("TREASURE_EMOJI") ?: "✨"
                petView?.consumeTreasure(emoji)
                return START_STICKY
            }
            ACTION_REFRESH_PET -> {
                ensureOverlayReady()
                petView?.refreshFromRepository(
                    message = intent.getStringExtra(EXTRA_REFRESH_MESSAGE),
                    celebrate = intent.getBooleanExtra(EXTRA_REFRESH_CELEBRATE, false),
                )
                return START_STICKY
            }
        }

        val petTypeName = intent?.getStringExtra(PetSelectionActivity.EXTRA_PET_TYPE)
        if (petTypeName != null) {
            currentPetType = try { PetType.valueOf(petTypeName) } catch (e: Exception) { selectedPetStore.load() }
            selectedPetStore.save(currentPetType)
            if (isViewAttached) removePetOverlay()
        } else {
            currentPetType = selectedPetStore.load()
        }

        ensureOverlayReady()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createPetOverlay() {
        if (isViewAttached) return
        windowManager = getSystemService(WindowManager::class.java)
        if (windowManager == null) {
            Log.e(TAG, "WindowManager unavailable")
            stopSelf()
            return
        }
        if (!canDrawOverlays()) {
            Log.e(TAG, "Overlay permission missing")
            stopSelf()
            return
        }

        val metrics = getDisplayMetrics()
        val petSize = (PET_SIZE_DP * resources.displayMetrics.density).toInt()
        val viewSize = (petSize * 1.4f).toInt()

        petView = PetView(this, metrics.widthPixels, metrics.heightPixels, petSize, currentPetType)

        // Evita robar foco al sistema (mejora back/gestos), manteniendo el overlay touchable.
        // adjustNothing: el sistema no debe panear/insetar la ventana cuando abre el teclado;
        // la mascota se reposiciona por nuestra cuenta según el IME.
        val params = WindowManager.LayoutParams(
            viewSize,
            viewSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseOverlayFlags,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            x = metrics.widthPixels / 2 - viewSize / 2
            y = metrics.heightPixels / 3
        }
        try {
            windowManager?.addView(petView, params)
            isViewAttached = true
            lastAppliedPetVisible = null
            applyPetOverlayVisible(shouldShowPetForPolicy())
            startHomeForegroundPolling()
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "Overlay attach failed", e)
            stopSelf()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Overlay attach failed", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Overlay attach failed", e)
            stopSelf()
        }
    }

    private fun removePetOverlay() {
        homeCheckHandler.removeCallbacks(homeCheckRunnable)
        petView?.let {
            it.pauseAnimation()
            if (isViewAttached) {
                try { windowManager?.removeViewImmediate(it) } catch (_: Exception) { Log.w(TAG, "Overlay detach failed") }
                isViewAttached = false
            }
        }
        petView = null
        lastAppliedPetVisible = null
    }

    /**
     * Con acceso de uso: solo visible en el lanzador. Sin acceso: siempre visible salvo ocultar manual.
     * No se puede poner un TYPE_APPLICATION_OVERLAY detrás de otras apps; se oculta para no taparlas.
     */
    private fun shouldShowPetForPolicy(): Boolean {
        if (userManuallyHidden) return false
        if (!DesktopForegroundHelper.hasUsageAccess(this)) return true
        return DesktopForegroundHelper.isLauncherForeground(this)
    }

    private fun refreshPetVisibilityForForeground() {
        if (!isViewAttached || petView == null) return
        if (userManuallyHidden) return
        if (!DesktopForegroundHelper.hasUsageAccess(this)) {
            applyPetOverlayVisible(true)
            return
        }
        val launcher = DesktopForegroundHelper.isLauncherForeground(this)
        applyPetOverlayVisible(launcher)
    }

    private fun applyPetOverlayVisible(visible: Boolean) {
        val v = petView ?: return
        val already = lastAppliedPetVisible == visible &&
            v.visibility == if (visible) View.VISIBLE else View.GONE
        if (already) return
        lastAppliedPetVisible = visible
        v.visibility = if (visible) View.VISIBLE else View.GONE
        updateOverlayTouchThrough(!visible)
        if (visible) v.resumeAnimation() else v.pauseAnimation()
    }

    private fun updateOverlayTouchThrough(passThrough: Boolean) {
        val v = petView ?: return
        val wm = windowManager ?: return
        val p = v.layoutParams as? WindowManager.LayoutParams ?: return
        val newFlags = if (passThrough) {
            baseOverlayFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseOverlayFlags
        }
        if (p.flags == newFlags) return
        p.flags = newFlags
        try {
            wm.updateViewLayout(v, p)
        } catch (e: Exception) {
            Log.w(TAG, "Overlay flag update failed")
        }
    }

    private fun startHomeForegroundPolling() {
        homeCheckHandler.removeCallbacks(homeCheckRunnable)
        homeCheckHandler.postDelayed(homeCheckRunnable, HOME_POLL_INTERVAL_MS)
    }

    private fun canDrawOverlays(): Boolean = android.provider.Settings.canDrawOverlays(this)

    private fun getDisplayMetrics(): DisplayMetrics {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager?.currentWindowMetrics?.bounds ?: Rect()
            DisplayMetrics().apply {
                widthPixels = bounds.width().coerceAtLeast(1)
                heightPixels = bounds.height().coerceAtLeast(1)
                density = resources.displayMetrics.density
                densityDpi = resources.displayMetrics.densityDpi
            }
        } else {
            DisplayMetrics().also {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.getRealMetrics(it)
                if (it.widthPixels <= 0 || it.heightPixels <= 0) {
                    it.setTo(resources.displayMetrics)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(isHidden: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, PetDashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, PetService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_active_title))
            .setContentText(getString(R.string.notif_active_subtitle))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(android.R.string.cancel), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(isHidden: Boolean) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(isHidden))
        } catch (_: Exception) {
            Log.w(TAG, "Notification update failed")
        }
    }

    private fun registerBatteryReceiver() {
        if (batteryReceiver != null) return
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val percent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                petView?.onBatteryChanged(percent, isCharging)
            }
        }
        try {
            ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {
            Log.w(TAG, "Battery receiver registration failed")
            batteryReceiver = null
        }
    }
    
    private fun registerAirplaneReceiver() {
        if (airplaneReceiver != null) return
        airplaneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val isAirplane = intent.getBooleanExtra("state", false)
                petView?.onAirplaneModeChanged(isAirplane)
            }
        }
        try {
            ContextCompat.registerReceiver(this, airplaneReceiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {
            Log.w(TAG, "Airplane receiver registration failed")
            airplaneReceiver = null
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = ScreenStateReceiver { on ->
            if (on) {
                if (petView?.visibility == View.VISIBLE) petView?.resumeAnimation()
            } else {
                petView?.pauseAnimation()
            }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
        try {
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {
            Log.w(TAG, "Screen receiver registration failed")
            screenReceiver = null
        }
    }

    private fun ensureForeground() {
        if (!isForegroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification(false))
            isForegroundStarted = true
        } else {
            updateNotification(false)
        }
    }

    private fun ensureOverlayReady() {
        if (!isViewAttached) {
            createPetOverlay()
            if (!isViewAttached) return
            registerScreenReceiver()
            registerBatteryReceiver()
            registerAirplaneReceiver()
        } else {
            startHomeForegroundPolling()
            if (!userManuallyHidden) {
                applyPetOverlayVisible(shouldShowPetForPolicy())
            }
        }
    }

    override fun onDestroy() {
        removePetOverlay()
        isForegroundStarted = false
        try { 
            batteryReceiver?.let { unregisterReceiver(it) }
            airplaneReceiver?.let { unregisterReceiver(it) }
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {}
        batteryReceiver = null
        airplaneReceiver = null
        screenReceiver = null
        isRunning = false
        super.onDestroy()
    }
}
