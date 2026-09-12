package com.pixelpals.app

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractionWindowCallbackTest {
    @Test fun interactionIsReportedBeforeTouchAndKeyAreDelegated(): Unit {
        val events: MutableList<String> = mutableListOf()
        val delegate: Window.Callback = Proxy.newProxyInstance(
            Window.Callback::class.java.classLoader,
            arrayOf(Window.Callback::class.java),
        ) { _, method, _ ->
            if (method.name == "dispatchTouchEvent") events += "touch"
            if (method.name == "dispatchKeyEvent") events += "key"
            if (method.name == "dispatchKeyShortcutEvent") events += "shortcut"
            method.name != "dispatchTouchEvent"
        } as Window.Callback
        val callback: InteractionWindowCallback = InteractionWindowCallback(delegate) {
            events += "interaction"
        }
        val touch: MotionEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 2f, 3f, 0)
        try {
            assertFalse(callback.dispatchTouchEvent(touch))
        } finally {
            touch.recycle()
        }
        assertTrue(callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)))
        assertTrue(callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)))
        assertTrue(callback.dispatchKeyShortcutEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)))
        assertEquals(listOf("interaction", "touch", "interaction", "key", "key", "interaction", "shortcut"), events)
    }

    @Test fun secondaryActivitiesReceiveOneWrapperAcrossRepeatedResumeCallbacks(): Unit {
        ActivityScenario.launch(CompanionSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val callback = activity.window.callback
                assertTrue(callback is InteractionWindowCallback)
                (activity.application as PixelPalsApplication).onActivityResumed(activity)
                assertSame(callback, activity.window.callback)
            }
        }
    }
}
