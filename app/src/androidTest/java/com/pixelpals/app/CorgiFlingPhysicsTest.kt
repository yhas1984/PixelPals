package com.pixelpals.app

import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises Corgi's real touch dispatch at the boundary with shared physics. */
@RunWith(AndroidJUnit4::class)
class CorgiFlingPhysicsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before fun requireDisposableEmulator() {
        assumeTrue("PetView construction starts repository status refreshes",
            android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.HARDWARE == "ranchu")
    }

    @Test fun horizontalFlingFromAboveFloorUsesPhysicsWithoutReleaseSnap() {
        for (direction in listOf(-1f, 1f)) {
            instrumentation.runOnMainSync {
                    val view = newView()
                try {
                    view.windowY = view.bounds.floor - 300
                    syncLayoutParams(view)
                    val release = fling(view, direction, vertical = false)
                    assertEquals("air fling must enter shared physics", PetState.FALLING, view.state)
                    assertEquals("release must not snap X", release.first, view.windowX)
                    assertEquals("release must not snap Y", release.second, view.windowY)
                    val xAtRelease = view.windowX
                    val yAtRelease = view.windowY
                    advanceFalling(view, .1f)
                    assertTrue("physics must advance after release",
                        view.windowX != xAtRelease || view.windowY != yAtRelease)
                    advanceFalling(view, 12f)
                    assertEquals(PetState.IDLE, view.state)
                    assertEquals(view.bounds.floor, view.windowY)
                } finally { detach(view) }
            }
        }
    }

    @Test fun verticalDominantFlingFromFloorDelegatesToSharedPhysics() {
        instrumentation.runOnMainSync {
            for (verticalDirection in listOf(-1f, 1f)) {
                val view = newView()
                try {
                    view.windowY = view.bounds.floor
                    syncLayoutParams(view)
                    val release = fling(view, direction = verticalDirection, vertical = true)
                    assertEquals(PetState.FALLING, view.state)
                    assertEquals(release.first, view.windowX)
                    assertEquals(release.second, view.windowY)
                    repeat(30) {
                        if (view.state == PetState.FALLING) {
                            advanceFalling(view, 1f / 60f)
                            assertEquals("shared fall keeps Corgi rigid", 1f, view.animScaleY, .0001f)
                        }
                    }
                    advanceFalling(view, 12f)
                    assertEquals(PetState.IDLE, view.state)
                    assertEquals(view.bounds.floor, view.windowY)
                } finally { detach(view) }
            }
        }
    }

    @Test fun horizontalFlingOnFloorRemainsInteractionForCorgiBurst() {
        instrumentation.runOnMainSync {
            val view = newView()
            try {
                view.windowY = view.bounds.floor
                syncLayoutParams(view)
                fling(view, direction = 1f, vertical = false)
                assertEquals("ground horizontal fling belongs to Corgi burst", PetState.INTERACTING, view.state)
                assertEquals(view.bounds.floor, view.windowY)
            } finally { detach(view) }
        }
    }

    private fun newView(): PetView = PetView(
        context, screenWidth = 1_080, screenHeight = 2_400,
        petSpriteSize = 80, petType = PetType.CORGI
    ).also { view ->
        view.layoutParams = WindowManager.LayoutParams(
            160, 160, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { x = 500; y = 500 }
        val spec = View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY)
        view.measure(spec, spec)
        view.layout(0, 0, 160, 160)
        view.windowX = 500
        view.windowY = 500
        syncLayoutParams(view)
    }

    private fun fling(view: PetView, direction: Float, vertical: Boolean): Pair<Int, Int> {
        syncLayoutParams(view)
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, at: Long, x: Float, y: Float) {
            val event = MotionEvent.obtain(downTime, at, action, x, y, 0)
            try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
            // PetView's logical coordinates are copied into a fresh LayoutParams;
            // an unattached test view cannot persist that copy through WindowManager.
            if (action == MotionEvent.ACTION_MOVE) syncLayoutParams(view)
        }
        send(MotionEvent.ACTION_DOWN, downTime, 80f, 80f)
        if (vertical) {
            send(MotionEvent.ACTION_MOVE, downTime + 20L, 80f, 80f - direction * 60f)
            send(MotionEvent.ACTION_MOVE, downTime + 40L, 80f, 80f - direction * 180f)
        } else {
            send(MotionEvent.ACTION_MOVE, downTime + 20L, 80f + direction * 60f, 80f)
            send(MotionEvent.ACTION_MOVE, downTime + 40L, 80f + direction * 180f, 80f)
        }
        val releaseX = view.windowX
        val releaseY = view.windowY
        send(MotionEvent.ACTION_UP, downTime + 50L,
            if (vertical) 80f else 80f + direction * 220f,
            if (vertical) 80f - direction * 240f else 80f)
        return releaseX to releaseY
    }

    private fun syncLayoutParams(view: PetView) {
        val real = view.layoutParams as WindowManager.LayoutParams
        val inset = ((view.width - view.petSpriteSize) / 2).coerceAtLeast(0)
        real.x = view.windowX - inset
        real.y = view.windowY - inset
        view.layoutParams = real
    }

    private fun advanceFalling(view: PetView, seconds: Float) {
        val update = PetView::class.java.getDeclaredMethod(
            "updatePhysicsFalling", Float::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val step = 1f / 60f
        repeat((seconds / step).toInt()) {
            if (view.state == PetState.FALLING) {
                update.invoke(view, step)
                assertEquals("Corgi must stay rigid throughout the fall", 1f, view.animScaleY, .0001f)
                syncLayoutParams(view)
            }
        }
    }

    private fun detach(view: PetView) {
        PetView::class.java.getDeclaredMethod("onDetachedFromWindow").apply {
            isAccessible = true
        }.invoke(view)
    }
}
