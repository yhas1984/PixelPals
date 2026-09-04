package com.pixelpals.app.feature.care

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.getAvailableDesktopCareActions
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.status.PetStatusSnapshot

/** A small thought cloud that follows the existing desktop pet, without replacing it. */
class CorgiCareCloud(
    private val context: Context,
    private val windowManager: WindowManager,
    private val petSize: Int,
    private val pet: PetType,
    private val readStatus: () -> PetStatusSnapshot?,
    private val onAction: (CareSceneAction) -> Unit,
) {
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var cloud: CorgiCareCloudView? = null
    private var cachedBounds: Rect? = null
    private val dismiss: Runnable = Runnable { close() }
    private val refresh: Runnable = object : Runnable {
        override fun run(): Unit {
            if (cloud == null) return
            refreshActions()
            handler.postDelayed(this, 1_000L)
        }
    }

    fun show(anchorX: Int, anchorY: Int): Unit {
        close()
        val view: CorgiCareCloudView = CorgiCareCloudView(context, pet, getActions(),
            onAction = { action ->
                if (action in getActions()) { close(); onAction(action) } else refreshActions()
            }, onDismiss = ::close)
        try {
            val params: WindowManager.LayoutParams = params(anchorX, anchorY)
            windowManager.addView(view, params)
            cloud = view
            view.post { pointTail(view, params, anchorX, anchorY) }
            val accessibility: AccessibilityManager = context.getSystemService(AccessibilityManager::class.java)
            val timeout: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                accessibility.getRecommendedTimeoutMillis(12_000, AccessibilityManager.FLAG_CONTENT_CONTROLS) else 12_000
            handler.postDelayed(dismiss, timeout.toLong())
            handler.postDelayed(refresh, 1_000L)
        } catch (_: WindowManager.BadTokenException) { close()
        } catch (_: SecurityException) { close() }
    }

    fun follow(anchorX: Int, anchorY: Int): Unit {
        val view: CorgiCareCloudView = cloud ?: return
        if (!view.isAttachedToWindow) return
        val target: WindowManager.LayoutParams = params(anchorX, anchorY)
        val current: WindowManager.LayoutParams = view.layoutParams as WindowManager.LayoutParams
        if (current.x != target.x || current.y != target.y) windowManager.updateViewLayout(view, target)
        pointTail(view, target, anchorX, anchorY)
    }

    fun close(): Unit {
        handler.removeCallbacks(dismiss)
        handler.removeCallbacks(refresh)
        cloud?.let { if (it.isAttachedToWindow) windowManager.removeViewImmediate(it) }
        cloud = null
        cachedBounds = null
    }

    fun refreshActions(): Unit { cloud?.updateActions(getActions()) }

    private fun getActions(): List<CareSceneAction> =
        getAvailableDesktopCareActions(readStatus(), System.currentTimeMillis())

    private fun pointTail(view: CorgiCareCloudView, params: WindowManager.LayoutParams, x: Int, y: Int): Unit {
        view.pointTo((x - params.x).toFloat(), fromTop = params.y > y)
    }

    private fun params(x: Int, y: Int): WindowManager.LayoutParams {
        val bounds: Rect = cachedBounds ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics: android.view.WindowMetrics = windowManager.currentWindowMetrics
            val insets: android.graphics.Insets = metrics.windowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            Rect(insets.left, insets.top, metrics.bounds.width() - insets.right, metrics.bounds.height() - insets.bottom)
        } else Rect(0, dp(24), context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels - dp(48)))
            .also { cachedBounds = it }
        val width: Int = minOf(dp(152), bounds.width()).coerceAtLeast(1)
        val height: Int = minOf(dp(136), bounds.height()).coerceAtLeast(1)
        val top: Int = if (y - height >= bounds.top) y - height else y + petSize
        return WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (x - width / 2).coerceIn(bounds.left, maxOf(bounds.left, bounds.right - width))
            this.y = top.coerceIn(bounds.top, maxOf(bounds.top, bounds.bottom - height))
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
