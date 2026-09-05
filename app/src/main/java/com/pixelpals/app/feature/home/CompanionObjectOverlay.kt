package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.pixelpals.app.R

/** A single bounded, non-focusable toy. Only its small visible area receives touches. */
class CompanionObjectOverlay(private val context: Context, private val manager: WindowManager, private val onUse: (Decoration) -> Unit) {
    private var view: DecorationPreview? = null
    private var item: Decoration? = null
    private var isVisible: Boolean = false
    private var isAttached: Boolean = false
    private val size: Int = HomeUi.dp(context, 48)
    private val params: WindowManager.LayoutParams = WindowManager.LayoutParams(size, size,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }

    fun setObject(decoration: Decoration): Unit {
        if (item == decoration) return
        close()
        item = decoration
        view = DecorationPreview(context, decoration).apply {
            minimumHeight = size
            contentDescription = context.getString(R.string.home_object, context.getString(decoration.title))
            setOnClickListener { onUse(decoration) }
        }
        setVisible(isVisible)
    }
    fun follow(x: Int, y: Int): Unit {
        val metrics = context.resources.displayMetrics
        params.x = (x + size).coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
        params.y = (y + size).coerceIn(HomeUi.dp(context, 28), (metrics.heightPixels - size - HomeUi.dp(context, 32)).coerceAtLeast(HomeUi.dp(context, 28)))
        if (isAttached) runCatching { manager.updateViewLayout(view, params) }.onFailure { close() }
    }
    fun setVisible(visible: Boolean): Unit {
        isVisible = visible
        val current: View = view ?: return
        if (!visible) { close(); return }
        if (!isAttached) try { manager.addView(current, params); isAttached = true
        } catch (_: RuntimeException) { isAttached = false }
    }
    fun close(): Unit {
        if (isAttached) runCatching { manager.removeViewImmediate(view) }
        isAttached = false
    }
}
