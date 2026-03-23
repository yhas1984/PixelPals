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
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * PetService — Foreground Service que gestiona la mascota flotante.
 *
 * Responsabilidades:
 *  - Crear/destruir la ventana overlay con PetView
 *  - Gestionar notificación con controles
 *  - Monitorizar batería y cargador → notificar a PetView
 *  - Detectar teclado → mover mascota automáticamente
 *  - Pausar en screen off → optimizar batería
 */
class PetService : Service() {

    companion object {
        private const val TAG = "PetService"
        const val CHANNEL_ID = "pixelpals_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_HIDE = "com.pixelpals.app.ACTION_HIDE"
        const val ACTION_SHOW = "com.pixelpals.app.ACTION_SHOW"
        const val ACTION_STOP = "com.pixelpals.app.ACTION_STOP"
        const val ACTION_CONSUME_TREASURE = "com.pixelpals.app.ACTION_CONSUME_TREASURE"
        private const val PET_SIZE_DP = 80  // Spec: 64-80dp max
    }

    private var windowManager: WindowManager? = null
    private var petView: PetView? = null
    private var keyboardProbe: View? = null
    private var screenReceiver: ScreenStateReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var airplaneReceiver: BroadcastReceiver? = null
    private var petProgress: PetProgress? = null
    private var isViewAttached = false
    private var currentPetType: PetType = PetType.CORGI

    // ──────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                hidePet()
                updateNotification(isHidden = true)
                return START_STICKY
            }
            ACTION_SHOW -> {
                showPet()
                updateNotification(isHidden = false)
                return START_STICKY
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONSUME_TREASURE -> {
                val emoji = intent?.getStringExtra("TREASURE_EMOJI") ?: "✨"
                petView?.consumeTreasure(emoji)
                return START_STICKY
            }
        }

        // ── Read pet type from intent ──
        val petTypeName = intent?.getStringExtra(PetSelectionActivity.EXTRA_PET_TYPE)
        if (petTypeName != null) {
            val newType = try { PetType.valueOf(petTypeName) } catch (e: Exception) {
                Log.w(TAG, "Unknown pet type: $petTypeName, defaulting to CORGI", e)
                PetType.CORGI
            }

            if (isViewAttached && newType != currentPetType) {
                removePetOverlay()
            }
            currentPetType = newType
        }

        startForeground(NOTIFICATION_ID, buildNotification(isHidden = false))

        if (!isViewAttached) {
            petProgress = PetProgress(this)
            createPetOverlay()
            registerScreenReceiver()
            registerBatteryReceiver()
            registerAirplaneReceiver()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterScreenReceiver()
        unregisterBatteryReceiver()
        unregisterAirplaneReceiver()
        removeKeyboardProbe()
        removePetOverlay()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PixelPals Mascota",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene a tu mascota virtual viva en la pantalla"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(isHidden: Boolean, customMessage: String? = null): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = if (isHidden) ACTION_SHOW else ACTION_HIDE
        val toggleIntent = Intent(this, PetService::class.java).apply { action = toggleAction }
        val togglePending = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleText = if (isHidden) "🐾 Mostrar" else "😴 Ocultar"

        val stopIntent = Intent(this, PetService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val petEmoji = when (currentPetType) {
            PetType.BLOOP -> "👻"
            PetType.NUBE_MICHI -> "☁️"
            PetType.JELLY -> "🟢"
            PetType.CORGI -> "🐕"
            PetType.GINGER -> "🐱"
            PetType.PATITO -> "🦆"
            PetType.DIABLILLO -> "😈"
        }
        val lvl = petProgress?.let { "Lv${it.petLevel}" } ?: ""
        val xp = petProgress?.happinessPoints ?: 0
        val treasures = petProgress?.treasureCount ?: 0
        val contentText = customMessage ?: if (isHidden) {
            "${currentPetType.displayName} está dormido 💤"
        } else {
            "$petEmoji ${currentPetType.displayName} $lvl · ${xp}XP · 💎$treasures"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PixelPals")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, toggleText, togglePending)
            .addAction(0, "❌ Cerrar", stopPending)
            .build()
    }

    private fun updateNotification(isHidden: Boolean = false, message: String? = null) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(isHidden, message))
    }

    // ──────────────────────────────────────────────────────────
    // Pet Overlay
    // ──────────────────────────────────────────────────────────

    private fun createPetOverlay() {
        if (isViewAttached) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // Pet size: 80dp → pixels (120dp for Diablillo - 50% larger)
        val density = resources.displayMetrics.density
        val baseSize = if (currentPetType == PetType.DIABLILLO) 120 else PET_SIZE_DP
        val petSize = (baseSize * density).toInt()

        // View size slightly larger for shadow + bubbles
        val viewSize = (petSize * 1.4f).toInt()

        petView = PetView(this, screenWidth, screenHeight, petSize, currentPetType)

        val params = WindowManager.LayoutParams(
            viewSize,
            viewSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 2 - viewSize / 2
            y = screenHeight / 3
        }

        try {
            windowManager?.addView(petView, params)
            isViewAttached = true
            petProgress?.let { petView?.setProgress(it) }
            petView?.resumeAnimation()
            setupKeyboardDetection(screenWidth, screenHeight)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removePetOverlay() {
        petView?.let { view ->
            view.pauseAnimation()
            if (isViewAttached) {
                try { windowManager?.removeView(view) } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove pet overlay", e)
                }
                isViewAttached = false
            }
        }
        petView = null
    }

    private fun hidePet() {
        petView?.let {
            it.pauseAnimation()
            it.visibility = View.GONE
        }
    }

    private fun showPet() {
        petView?.let {
            it.visibility = View.VISIBLE
            it.resumeAnimation()
        }
    }

    // ──────────────────────────────────────────────────────────
    // Battery Awareness
    // ──────────────────────────────────────────────────────────

    private fun registerBatteryReceiver() {
        if (batteryReceiver != null) return

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                val batteryPercent = if (level >= 0 && scale > 0) {
                    (level * 100 / scale)
                } else 100

                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                petView?.onBatteryChanged(batteryPercent, isCharging)
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister battery receiver", e)
            }
        }
        batteryReceiver = null
    }

    // ──────────────────────────────────────────────────────────
    // Keyboard Detection
    // ──────────────────────────────────────────────────────────

    /**
     * Uses a zero-width probe view to detect keyboard appearance.
     * When the usable screen height shrinks, the keyboard is visible.
     */
    private fun setupKeyboardDetection(screenWidth: Int, screenHeight: Int) {
        val wm = windowManager ?: return

        keyboardProbe = View(this)

        val probeParams = WindowManager.LayoutParams(
            0,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try {
            wm.addView(keyboardProbe, probeParams)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add keyboard probe", e)
            return
        }

        var lastHeight = 0

        keyboardProbe?.viewTreeObserver?.addOnGlobalLayoutListener {
            val r = Rect()
            keyboardProbe?.getWindowVisibleDisplayFrame(r)
            val visibleHeight = r.bottom - r.top

            if (lastHeight == 0) {
                lastHeight = visibleHeight
                return@addOnGlobalLayoutListener
            }

            val diff = lastHeight - visibleHeight
            if (diff > screenHeight * 0.15f) {
                // Keyboard appeared
                petView?.onKeyboardChanged(true, diff)
            } else if (diff < screenHeight * 0.05f && lastHeight != visibleHeight) {
                // Keyboard hidden
                petView?.onKeyboardChanged(false, 0)
            }

            if (visibleHeight > 0) {
                lastHeight = visibleHeight
            }
        }
    }

    private fun removeKeyboardProbe() {
        keyboardProbe?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {
                Log.w(TAG, "Failed to remove keyboard probe", e)
            }
        }
        keyboardProbe = null
    }

    // ──────────────────────────────────────────────────────────
    // Airplane Mode Detection
    // ──────────────────────────────────────────────────────────

    private fun registerAirplaneReceiver() {
        if (airplaneReceiver != null) return
        airplaneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val isAirplane = intent.getBooleanExtra("state", false)
                petView?.onAirplaneModeChanged(isAirplane)
            }
        }
        val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        registerReceiver(airplaneReceiver, filter)
    }

    private fun unregisterAirplaneReceiver() {
        airplaneReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister airplane receiver", e)
            }
        }
        airplaneReceiver = null
    }

    // ──────────────────────────────────────────────────────────
    // Screen State Receiver
    // ──────────────────────────────────────────────────────────

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = ScreenStateReceiver { isScreenOn ->
            if (isScreenOn) petView?.resumeAnimation()
            else petView?.pauseAnimation()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister screen receiver", e)
            }
        }
        screenReceiver = null
    }
}
