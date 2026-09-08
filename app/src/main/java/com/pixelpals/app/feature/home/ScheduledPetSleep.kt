package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.rest.DeviceRestState
import com.pixelpals.app.feature.care.PetDreamPainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Display-only sleep: never starts a care session or grants currency/care rewards. */
class ScheduledPetSleep(private val context: Context, private val pet: PetType, private val scope: CoroutineScope) {
    private val preferences = CompanionPreferences(context)
    private val device = DeviceRestState(context)
    private val motion = CompanionMotion()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val target = RectF()
    private val dreams = PetDreamPainter()
    private var art: HomeLocomotion? = null
    private var loading: Job? = null
    private var awakeUntil: Long = 0L
    private var retryAfter: Long = 0L
    var active: Boolean = false
        private set

    fun wakeForInteraction() {
        awakeUntil = SystemClock.elapsedRealtime() + 60_000L
        active = false
    }

    fun update(delta: Float, eligible: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        val rest = eligible && now >= awakeUntil && device.shouldRest(preferences.restSchedule)
        if (!rest) {
            motion.advanceScheduledRest(false, delta)
            // Direct manipulation/care takes priority; automatic waking holds the
            // actor in place until its wake clip completes.
            active = eligible && art != null && motion.advanceScheduledWake(delta)
            return active
        }
        if (art == null && loading?.isActive != true && now >= retryAfter) {
            loading = scope.launch {
                try {
                    art = HomeLocomotion.load(context, pet)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    retryAfter = SystemClock.elapsedRealtime() + 60_000L
                    android.util.Log.w("ScheduledPetSleep", "Sleep art unavailable for $pet", failure)
                }
            }
        }
        active = art != null
        if (active) motion.advanceScheduledRest(true, delta)
        return active
    }

    fun draw(canvas: Canvas, size: Float, baseline: Float, facesLeft: Boolean, tint: ColorFilter?) {
        val source = art ?: return
        val ground = canvas.height / 2f + baseline
        target.set(canvas.width / 2f - size / 2f, ground - size, canvas.width / 2f + size / 2f, ground)
        paint.colorFilter = tint
        canvas.save()
        if (facesLeft) canvas.scale(-1f, 1f, canvas.width / 2f, ground)
        source.draw(canvas, paint, target, motion, preferences.reducedMotion)
        canvas.restore()
        if (motion.activity == CompanionActivity.REST) {
            dreams.drawDesktop(canvas, canvas.width / 2f, ground, size, motion.elapsed, preferences.reducedMotion)
        }
    }
}
