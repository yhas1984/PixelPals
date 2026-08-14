package com.pixelpals.app.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class TaroDebugOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: TaroDebugOverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        ensureOverlay()
        when (intent?.action) {
            ACTION_REVIEW_CLIP -> {
                val clip = intent.getStringExtra(EXTRA_CLIP)?.let { name ->
                    TaroReviewClip.entries.firstOrNull { it.name == name }
                }
                if (clip != null) {
                    overlayView?.startManualReview(
                        clip,
                        intent.getFloatExtra(EXTRA_DIRECTION, 1f),
                        intent.getFloatExtra(EXTRA_SPEED, 1f),
                    )
                }
            }
            ACTION_AUTONOMOUS -> overlayView?.startAutonomous()
            ACTION_SET_SPEED -> overlayView?.setPlaybackSpeed(intent.getFloatExtra(EXTRA_SPEED, 1f))
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlayView?.stopAnimation()
        overlayView?.let { view -> runCatching { windowManager?.removeViewImmediate(view) } }
        overlayView = null
        overlayParams = null
        super.onDestroy()
    }

    private fun ensureOverlay() {
        if (overlayView != null) return
        val metrics = resources.displayMetrics
        val spriteSize = (SPRITE_SIZE_DP * metrics.density).roundToInt()
        val params = WindowManager.LayoutParams(
            spriteSize,
            spriteSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels / 2 - spriteSize / 2).coerceAtLeast(0)
            y = (metrics.heightPixels * 0.62f).roundToInt().coerceIn(0, (metrics.heightPixels - spriteSize).coerceAtLeast(0))
        }
        windowManager = getSystemService(WindowManager::class.java)
        val view = TaroDebugOverlayView(
            context = this,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            spriteSize = spriteSize,
            onMove = ::moveOverlay,
        )
        windowManager?.addView(view, params)
        overlayParams = params
        overlayView = view
        view.startAnimation()
    }

    private fun moveOverlay(centerX: Float, centerY: Float) {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        params.x = (centerX - view.viewSize / 2f).roundToInt()
            .coerceIn(0, (view.screenWidth - view.viewSize).coerceAtLeast(0))
        params.y = (centerY - view.viewSize / 2f).roundToInt()
            .coerceIn(0, (view.screenHeight - view.viewSize).coerceAtLeast(0))
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(com.pixelpals.app.R.string.debug_taro_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, TaroDebugOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, TaroDebugActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(com.pixelpals.app.R.string.debug_taro_title))
            .setContentText(getString(com.pixelpals.app.R.string.debug_taro_notification))
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(com.pixelpals.app.R.string.debug_taro_stop), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val ACTION_REVIEW_CLIP = "com.pixelpals.app.debug.ACTION_REVIEW_TARO_CLIP"
        private const val ACTION_AUTONOMOUS = "com.pixelpals.app.debug.ACTION_AUTONOMOUS_TARO"
        private const val ACTION_SET_SPEED = "com.pixelpals.app.debug.ACTION_SET_SPEED_TARO"
        private const val ACTION_STOP = "com.pixelpals.app.debug.ACTION_STOP_TARO"
        private const val EXTRA_CLIP = "extra_clip"
        private const val EXTRA_DIRECTION = "extra_direction"
        private const val EXTRA_SPEED = "extra_speed"
        private const val CHANNEL_ID = "pixelpals_debug_taro"
        private const val NOTIFICATION_ID = 2201
        private const val REQUEST_STOP = 2202
        private const val REQUEST_OPEN = 2203
        private const val SPRITE_SIZE_DP = 104f

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(context, Intent(context, TaroDebugOverlayService::class.java))
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, TaroDebugOverlayService::class.java))
        }

        internal fun startManualReview(context: android.content.Context, clip: TaroReviewClip, direction: Float, speed: Float) {
            val intent = Intent(context, TaroDebugOverlayService::class.java).apply {
                action = ACTION_REVIEW_CLIP
                putExtra(EXTRA_CLIP, clip.name)
                putExtra(EXTRA_DIRECTION, direction)
                putExtra(EXTRA_SPEED, speed)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun resumeAutonomous(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TaroDebugOverlayService::class.java).setAction(ACTION_AUTONOMOUS),
            )
        }

        internal fun setSpeed(context: android.content.Context, speed: Float) {
            val intent = Intent(context, TaroDebugOverlayService::class.java).apply {
                action = ACTION_SET_SPEED
                putExtra(EXTRA_SPEED, speed)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
