package com.pixelpals.app

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window

/** Window callback that closes an App Open launch window on first real input. */
class InteractionWindowCallback(
    private val delegate: Window.Callback,
    private val onUserInteraction: () -> Unit,
) : Window.Callback by delegate {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onUserInteraction()
        return delegate.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) onUserInteraction()
        return delegate.dispatchKeyEvent(event)
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) onUserInteraction()
        return delegate.dispatchKeyShortcutEvent(event)
    }
}
