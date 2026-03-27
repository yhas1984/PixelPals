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
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class PetService : Service() {

    companion object {
        private const val TAG = "PetService"
        const val CHANNEL_ID = "pixelpals_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_HIDE = "com.pixelpals.app.ACTION_HIDE"
        const val ACTION_SHOW = "com.pixelpals.app.ACTION_SHOW"
        const val ACTION_STOP = "com.pixelpals.app.ACTION_STOP"
        const val ACTION_CONSUME_TREASURE = "com.pixelpals.app.ACTION_CONSUME_TREASURE"
        private const val PET_SIZE_DP = 80
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

    private fun debugLog(runId: String, hypothesisId: String, location: String, message: String, data: JSONObject) {
        // #region agent log
        try {
            val payload = JSONObject().apply {
                put("sessionId", "a40953")
                put("runId", runId)
                put("hypothesisId", hypothesisId)
                put("location", location)
                put("message", message)
                put("data", data)
                put("timestamp", System.currentTimeMillis())
            }
            Log.i("AGENT_DEBUG", payload.toString())
        } catch (_: Exception) {}
        // #endregion
    }

    override fun onCreate() {
        super.onCreate()
        selectedPetStore = SelectedPetStore(this)
        currentPetType = selectedPetStore.load()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != ACTION_STOP) {
            ensureForeground()
        }

        when (intent?.action) {
            ACTION_HIDE -> { hidePet(); return START_STICKY }
            ACTION_SHOW -> { showPet(); return START_STICKY }
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_CONSUME_TREASURE -> {
                ensureOverlayReady()
                val emoji = intent.getStringExtra("TREASURE_EMOJI") ?: "✨"
                petView?.consumeTreasure(emoji)
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

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(metrics)
        val petSize = (PET_SIZE_DP * resources.displayMetrics.density).toInt()
        val viewSize = (petSize * 1.4f).toInt()

        petView = PetView(this, metrics.widthPixels, metrics.heightPixels, petSize, currentPetType)

        // Evita robar foco al sistema (mejora back/gestos), manteniendo el overlay touchable.
        val params = WindowManager.LayoutParams(
            viewSize,
            viewSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels / 2 - viewSize / 2
            y = metrics.heightPixels / 3
        }
        debugLog(
            runId = "post-fix",
            hypothesisId = "H1",
            location = "PetService.kt:createPetOverlay",
            message = "Overlay params creados",
            data = JSONObject().apply {
                put("flags", params.flags)
                put("width", params.width)
                put("height", params.height)
                put("screenWidth", metrics.widthPixels)
                put("screenHeight", metrics.heightPixels)
                put("x", params.x)
                put("y", params.y)
            }
        )

        try {
            windowManager?.addView(petView, params)
            isViewAttached = true
            petView?.resumeAnimation()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo adjuntar el overlay", e)
            stopSelf()
        }
    }

    private fun removePetOverlay() {
        petView?.let {
            it.pauseAnimation()
            if (isViewAttached) {
                try { windowManager?.removeView(it) } catch (e: Exception) { Log.w(TAG, "Fail remove", e) }
                isViewAttached = false
            }
        }
        petView = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "PixelPals", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(isHidden: Boolean): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PixelPals Activo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(isHidden: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(isHidden))
    }

    private fun hidePet() { petView?.visibility = View.GONE }
    private fun showPet() { petView?.visibility = View.VISIBLE }

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
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    private fun registerAirplaneReceiver() {
        if (airplaneReceiver != null) return
        airplaneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val isAirplane = intent.getBooleanExtra("state", false)
                petView?.onAirplaneModeChanged(isAirplane)
            }
        }
        ContextCompat.registerReceiver(
            this,
            airplaneReceiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = ScreenStateReceiver { on -> if (on) petView?.resumeAnimation() else petView?.pauseAnimation() }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
            registerScreenReceiver()
            registerBatteryReceiver()
            registerAirplaneReceiver()
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
        super.onDestroy()
    }
}
