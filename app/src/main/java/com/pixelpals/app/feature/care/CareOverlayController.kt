package com.pixelpals.app.feature.care

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.pixelpals.app.R
import com.pixelpals.app.core.care.scene.CareSceneOrigin
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.domain.PetType

/** A bounded tool window, never a full-screen input-capturing overlay. */
class CareOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onVisibilityChanged: () -> Unit,
    private val onResult: (CareSceneResult) -> Unit,
) {
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val themed: Context = ContextThemeWrapper(context, R.style.Theme_PixelPals)
    private var hint: View? = null
    private var window: View? = null
    private var panel: CareScenePanel? = null
    private var store: ViewModelStore? = null
    val isShowing: Boolean get() = window != null
    val hasPresentation: Boolean get() = window != null || hint != null
    private val hideHint: Runnable = Runnable { removeHint() }

    fun showHint(pet: PetType, anchorX: Int, anchorY: Int): Unit {
        if (isShowing) return
        removeHint()
        val button: Button = Button(themed).apply {
            text = context.getString(R.string.care_scene_open)
            isAllCaps = false
            setOnClickListener { open(pet, anchorX, anchorY) }
        }
        try {
            windowManager.addView(button, params(dp(104), dp(48), anchorX, anchorY - dp(48)))
            hint = button
            handler.postDelayed(hideHint, 5_000L)
        } catch (_: WindowManager.BadTokenException) { removeHint()
        } catch (_: SecurityException) { removeHint() }
    }

    private fun open(pet: PetType, x: Int, y: Int): Unit {
        removeHint()
        if (isShowing) return
        val viewModels: ViewModelStore = ViewModelStore()
        store = viewModels
        val model: CareSceneViewModel = ViewModelProvider(viewModels, CareSceneViewModel.Factory(
            context.applicationContext as Application, pet, CareSceneOrigin.OVERLAY))[CareSceneViewModel::class.java]
        val content: CareScenePanel = CareScenePanel(themed).apply {
            bind(model)
            onClose = { close() }
            onResult = this@CareOverlayController.onResult
        }
        val scroll: ScrollView = ScrollView(themed).apply { isFillViewport = false; addView(content) }
        scroll.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) { close(); true } else false
        }
        panel = content
        try {
            windowManager.addView(scroll, params(dp(320), dp(450), x - dp(80), y - dp(160)))
            window = scroll
            onVisibilityChanged()
        } catch (_: WindowManager.BadTokenException) { close()
        } catch (_: SecurityException) { close() }
    }

    fun close(): Unit {
        removeHint()
        panel?.cancel()
        window?.let { if (it.isAttachedToWindow) windowManager.removeViewImmediate(it) }
        window = null
        panel = null
        store?.clear()
        store = null
        onVisibilityChanged()
    }

    private fun removeHint(): Unit {
        handler.removeCallbacks(hideHint)
        hint?.let { if (it.isAttachedToWindow) windowManager.removeViewImmediate(it) }
        hint = null
    }

    private fun params(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams {
        val bounds: Rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics: android.view.WindowMetrics = windowManager.currentWindowMetrics
            val insets: android.graphics.Insets = metrics.windowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            Rect(insets.left, insets.top, metrics.bounds.width() - insets.right, metrics.bounds.height() - insets.bottom)
        } else Rect(0, dp(24), context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels - dp(48))
        val safeWidth: Int = minOf(width, bounds.width())
        val safeHeight: Int = minOf(height, bounds.height())
        return WindowManager.LayoutParams(safeWidth, safeHeight, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x.coerceIn(bounds.left, bounds.right - safeWidth)
            this.y = y.coerceIn(bounds.top, bounds.bottom - safeHeight)
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
