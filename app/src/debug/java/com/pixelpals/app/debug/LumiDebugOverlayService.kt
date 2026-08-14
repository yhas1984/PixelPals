package com.pixelpals.app.debug

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
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pixelpals.app.R
import kotlin.math.roundToInt

class LumiDebugOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: LumiDebugOverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var isForeground: Boolean = false
    private var screenReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null

    override fun onCreate(): Unit {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureForeground()
        ensureOverlay()
        when (intent?.action) {
            ACTION_REVIEW_CLIP -> reviewClip(intent)
            ACTION_AUTONOMOUS -> {
                setReviewInputEnabled(false)
                overlayView?.stopManualReview()
            }
            ACTION_SET_REVIEW_INPUT -> setReviewInputEnabled(intent.getBooleanExtra(EXTRA_INPUT_ENABLED, true))
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy(): Unit {
        overlayView?.stopAnimation()
        overlayView?.let { view ->
            runCatching { windowManager?.removeViewImmediate(view) }
        }
        overlayView = null
        overlayParams = null
        unregisterReceivers()
        if (isForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        super.onDestroy()
    }

    private fun reviewClip(intent: Intent): Unit {
        val clipName = intent.getStringExtra(EXTRA_CLIP) ?: return
        val clip = runCatching { LumiReviewClip.valueOf(clipName) }.getOrNull() ?: return
        val direction = intent.getFloatExtra(EXTRA_DIRECTION, 1f)
        val speed = intent.getFloatExtra(EXTRA_SPEED, 1f)
        setReviewInputEnabled(true)
        overlayView?.startManualReview(clip, direction, speed)
    }

    private fun setReviewInputEnabled(enabled: Boolean): Unit {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val flags = if (enabled) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        if (flags == params.flags) return
        params.flags = flags
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun ensureForeground(): Unit {
        if (isForeground) return
        startForeground(NOTIFICATION_ID, buildNotification())
        isForeground = true
    }

    private fun ensureOverlay(): Unit {
        if (overlayView != null) return
        val metrics = getDisplayMetrics()
        val spriteSize = (SPRITE_SIZE_DP * resources.displayMetrics.density).roundToInt()
        val viewSize = spriteSize
        val initialX = metrics.widthPixels / 2
        val initialY = (metrics.heightPixels * 0.62f).roundToInt()
        val params = WindowManager.LayoutParams(
            viewSize,
            viewSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (initialX - viewSize / 2).coerceIn(0, (metrics.widthPixels - viewSize).coerceAtLeast(0))
            y = (initialY - viewSize / 2).coerceIn(0, (metrics.heightPixels - viewSize).coerceAtLeast(0))
        }
        val view = LumiDebugOverlayView(
            context = this,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            spriteSize = spriteSize,
            onMove = ::moveOverlay,
        )
        windowManager = getSystemService(WindowManager::class.java)
        windowManager?.addView(view, params)
        overlayParams = params
        overlayView = view
        registerReceivers()
        view.startAnimation()
    }

    private fun registerReceivers(): Unit {
        if (screenReceiver == null) {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent): Unit {
                    overlayView?.onScreenChanged(intent.action == Intent.ACTION_SCREEN_ON)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        if (batteryReceiver == null) {
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent): Unit {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    overlayView?.onBatteryChanged((level * 100 / scale).coerceIn(0, 100), charging)
                }
            }
            ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    private fun unregisterReceivers(): Unit {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        batteryReceiver = null
    }

    private fun moveOverlay(centerX: Float, centerY: Float): Unit {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val maxX = (view.screenWidth - view.viewSize).coerceAtLeast(0)
        val maxY = (view.screenHeight - view.viewSize).coerceAtLeast(0)
        params.x = (centerX - view.viewSize / 2f).roundToInt().coerceIn(0, maxX)
        params.y = (centerY - view.viewSize / 2f).roundToInt().coerceIn(0, maxY)
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun getDisplayMetrics(): android.util.DisplayMetrics {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds
            android.util.DisplayMetrics().apply {
                widthPixels = bounds?.width()?.coerceAtLeast(1) ?: resources.displayMetrics.widthPixels
                heightPixels = bounds?.height()?.coerceAtLeast(1) ?: resources.displayMetrics.heightPixels
                density = resources.displayMetrics.density
                densityDpi = resources.displayMetrics.densityDpi
            }
        } else {
            resources.displayMetrics
        }
    }

    private fun createNotificationChannel(): Unit {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.debug_lumi_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, LumiDebugOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, LumiDebugActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(R.string.debug_lumi_title))
            .setContentText(getString(R.string.debug_lumi_notification))
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.debug_lumi_stop), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_STOP: String = "com.pixelpals.app.debug.ACTION_STOP_LUMI"
        private const val ACTION_REVIEW_CLIP: String = "com.pixelpals.app.debug.ACTION_REVIEW_LUMI_CLIP"
        private const val ACTION_AUTONOMOUS: String = "com.pixelpals.app.debug.ACTION_AUTONOMOUS_LUMI"
        private const val ACTION_SET_REVIEW_INPUT: String = "com.pixelpals.app.debug.ACTION_SET_REVIEW_INPUT"
        private const val EXTRA_CLIP: String = "extra_clip"
        private const val EXTRA_DIRECTION: String = "extra_direction"
        private const val EXTRA_SPEED: String = "extra_speed"
        private const val EXTRA_INPUT_ENABLED: String = "extra_input_enabled"
        private const val CHANNEL_ID: String = "pixelpals_debug_lumi"
        private const val NOTIFICATION_ID: Int = 2101
        private const val REQUEST_STOP: Int = 2102
        private const val REQUEST_OPEN: Int = 2103
        private const val SPRITE_SIZE_DP: Float = 104f

        fun start(context: android.content.Context): Unit {
            val intent = Intent(context, LumiDebugOverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        internal fun setReviewInputEnabled(context: android.content.Context, enabled: Boolean): Unit {
            val intent = Intent(context, LumiDebugOverlayService::class.java).apply {
                action = ACTION_SET_REVIEW_INPUT
                putExtra(EXTRA_INPUT_ENABLED, enabled)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context): Unit {
            context.stopService(Intent(context, LumiDebugOverlayService::class.java))
        }

        internal fun startManualReview(context: android.content.Context, clip: LumiReviewClip, direction: Float, speed: Float): Unit {
            val intent = Intent(context, LumiDebugOverlayService::class.java).apply {
                action = ACTION_REVIEW_CLIP
                putExtra(EXTRA_CLIP, clip.name)
                putExtra(EXTRA_DIRECTION, direction)
                putExtra(EXTRA_SPEED, speed)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        internal fun resumeAutonomous(context: android.content.Context): Unit {
            val intent = Intent(context, LumiDebugOverlayService::class.java).setAction(ACTION_AUTONOMOUS)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
