package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.rest.DeviceRestState
import com.pixelpals.app.feature.care.PetDreamPainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch

/** Display-only sleep: never starts a care session or grants currency/care rewards. */
class ScheduledPetSleep(private val context: Context, private val pet: PetType, private val scope: CoroutineScope) {
    private val preferences = CompanionPreferences(context)
    private val device = DeviceRestState(context)
    private val motion = CompanionMotion()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val target = RectF()
    private val dreams = PetDreamPainter()
    private val bedPainter = com.pixelpals.app.feature.care.CarePropPainter()
    private var placedBed: String? = null
    private var bedLoaded: Boolean = false
    init {
        scope.launch {
            com.pixelpals.app.core.services.AppServices.companions(context).dao
                .observePlacements(pet.name.lowercase())
                .catch { failure ->
                    android.util.Log.w("ScheduledPetSleep", "Bed selection unavailable", failure)
                    emit(emptyList())
                }.collect { placements ->
                    placedBed = CareDecorationSelection.bed(placements)
                    bedLoaded = true
                }
        }
    }
    private var art: HomeLocomotion? = null
    private var loading: Job? = null
    private var awakeUntil: Long = 0L
    private var retryAfter: Long = 0L
    private var restStartsSeated: Boolean = false
    var active: Boolean = false
        private set

    fun wakeForInteraction(): Int? {
        val nativeFrame: Int? = if (active) art?.restFrameForHandoff(motion, preferences.reducedMotion, restStartsSeated) else null
        awakeUntil = SystemClock.elapsedRealtime() + 60_000L
        active = false
        restStartsSeated = false
        // Discard the interrupted rest clock so the next entry does not skip
        // its transition or inherit a partially completed wake.
        motion.advanceScheduledRest(false, 0f)
        motion.settleWithoutMovement(false)
        return nativeFrame
    }

    fun wantsRest(): Boolean = SystemClock.elapsedRealtime() >= awakeUntil &&
        device.shouldRest(preferences.restSchedule)

    fun update(delta: Float, eligible: Boolean, startsSeated: Boolean = false, entryFrame: Int? = null): Boolean {
        val now = SystemClock.elapsedRealtime()
        val rest = eligible && wantsRest()
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
        // Ambient sleep can last all night; follow home edits without restarting its pose.
        bedPainter.bedDecorationId = placedBed
        val entering = !active
        active = art != null && bedLoaded
        if (active && entering && motion.activity != CompanionActivity.WAKE) restStartsSeated = startsSeated
        if (active) {
            val wasWaking = motion.activity == CompanionActivity.WAKE
            motion.advanceScheduledRest(true, delta)
            if (entering && entryFrame != null)
                art?.restElapsedForFrame(entryFrame, restStartsSeated)?.let(motion::retainScheduledRestPose)
            // A re-enabled schedule finishes waking before returning to bed.
            // Its actor is now upright, even if the previous entry was seated.
            if (wasWaking && motion.activity == CompanionActivity.REST) restStartsSeated = false
        }
        return active
    }

    fun draw(canvas: Canvas, size: Float, baseline: Float, facesLeft: Boolean, tint: ColorFilter?) {
        val source = art ?: return
        val scaledSize: Float = size * PetArtworkScale.speciesSize(pet)
        val bodySize: Float = scaledSize * source.desktopSizeAdjustment
        val ground = canvas.height / 2f + baseline
        target.set(canvas.width / 2f - bodySize / 2f, ground - bodySize, canvas.width / 2f + bodySize / 2f, ground)
        // Home artwork reserves 4% below its feet; the desktop baseline is already the feet.
        target.offset(0f, bodySize * .04f)
        paint.colorFilter = tint
        canvas.save()
        if (facesLeft) canvas.scale(-1f, 1f, canvas.width / 2f, ground)
        // Bed and body share the same ground anchor; furniture is always behind.
        if (com.pixelpals.app.core.care.scene.PetCareProfile.forPet(pet).bed != com.pixelpals.app.core.care.scene.CareBed.WING_WRAP) {
            bedPainter.draw(canvas, com.pixelpals.app.core.care.scene.CareSceneAction.REST,
                canvas.width / 2f, ground, scaledSize * 1.08f, pet = pet)
        }
        source.draw(canvas, paint, target, motion, preferences.reducedMotion, restStartsSeated = restStartsSeated)
        canvas.restore()
        if (motion.activity == CompanionActivity.REST) {
            dreams.drawDesktop(canvas, canvas.width / 2f, ground, scaledSize, motion.elapsed, preferences.reducedMotion)
        }
    }
}
