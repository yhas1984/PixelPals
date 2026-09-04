package com.pixelpals.app.feature.care

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.Log
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** View-owned playback, application-owned commit. Hiding the pet cancels uncommitted care. */
class CorgiDesktopCare(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onFetchFrame: (CorgiFetchFrame?) -> Unit,
    private val coordinator: CareSceneCoordinator = AppServices.careScenes(context),
    private val onFinished: (CareSceneAction, CareSceneResult?) -> Unit,
) : DesktopCarePlayback {
    private val renderer: CorgiDesktopCareRenderer = CorgiDesktopCareRenderer()
    private var action: CareSceneAction = CareSceneAction.FEED
    private var owner: String? = null
    private var requestId: String? = null
    private var job: Job? = null
    private var pack: CarePosePack? = null
    private var scene: CareSceneController? = null
    private var result: CareSceneResult? = null
    private var facingLeft: Boolean = false
    private var elapsedRemainderMs: Float = 0f
    private var fetchPlan: CorgiFetchPlan? = null
    private var fetchPose: CorgiFetchPose? = null
    override val isActive: Boolean get() = owner != null
    override val isMovingPet: Boolean get() = isActive && action == CareSceneAction.PLAY

    override fun start(action: CareSceneAction, facingLeft: Boolean, fetchPlan: CorgiFetchPlan?): Unit {
        if (isActive) return
        require(action in ACTIONS)
        require(action != CareSceneAction.PLAY || fetchPlan != null)
        this.fetchPlan = fetchPlan
        this.action = action
        val sessionOwner: String = UUID.randomUUID().toString()
        owner = sessionOwner
        this.facingLeft = fetchPlan?.let { it.direction < 0f } ?: facingLeft
        job = scope.launch {
            try {
                val request: CareSceneRequest = CareSceneRequest(UUID.randomUUID().toString(), sessionOwner,
                    PetType.CORGI, action, CareSceneOrigin.OVERLAY, CareSceneMode.AUTOMATIC)
                if (!coordinator.start(request)) {
                    finish(CareSceneResult.Unavailable)
                    return@launch
                }
                requestId = request.id
                val initial: CareSceneSession? = coordinator.session.value
                if (initial?.request?.id != request.id) {
                    finish(null)
                    return@launch
                }
                if (initial.phase == CareScenePhase.FINISHED) {
                    finish(initial.result)
                    return@launch
                }
                val loaded: CarePosePack = CarePoseLoader.load(context.assets, PetType.CORGI)
                pack = loaded
                scene = CareSceneController(action, CareSceneMode.AUTOMATIC,
                    when (action) {
                        CareSceneAction.FEED -> CorgiFeedingMotion.timing
                        CareSceneAction.PLAY -> requireNotNull(fetchPlan).timing
                        else -> if (action in CorgiAdditionalCareMotion.actions) CorgiAdditionalCareMotion.getTiming(action)
                            else loaded.spec.timings.getValue(action)
                    })
                emitFetchFrame()
                coordinator.session.collect { current ->
                    if (current?.request?.owner != sessionOwner) {
                        finish(null)
                    } else if (current.phase == CareScenePhase.FINISHED) {
                        result = current.result
                        finishIfReady()
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w("CorgiDesktopCare", "Desktop care unavailable", exception)
                finish(CareSceneResult.Error)
            }
        }
    }

    override fun advance(deltaSeconds: Float): Unit {
        val playback: CareSceneController = scene ?: return
        elapsedRemainderMs += deltaSeconds * 1_000f
        val wholeMs: Long = elapsedRemainderMs.toLong()
        elapsedRemainderMs -= wholeMs
        val completed: Boolean = playback.advance(wholeMs)
        emitFetchFrame()
        if (completed) requestId?.let(coordinator::complete)
        finishIfReady()
    }

    /** True means locomotion must not draw an additional sprite this frame. */
    override fun draw(canvas: Canvas, spriteSize: Int): Boolean {
        val loaded: CarePosePack = pack ?: return false
        val playback: CareSceneController = scene ?: return false
        if (fetchPose?.regularFrame != null) return false
        renderer.draw(canvas, loaded, spriteSize, playback.animationMs, facingLeft,
            reducedMotion = !ValueAnimator.areAnimatorsEnabled(), action = action,
            fetchFrame = fetchPose?.careFrame ?: 2)
        return true
    }

    override fun cancel(): Unit {
        if (isActive) finish(null)
    }

    private fun finishIfReady(): Unit {
        if (scene?.isComplete == true && result != null) finish(result)
    }

    private fun emitFetchFrame(): Unit {
        val plan: CorgiFetchPlan = fetchPlan ?: return
        val loaded: CarePosePack = pack ?: return
        val elapsed: Long = scene?.animationMs ?: return
        val pose: CorgiFetchPose = CorgiFetchMotion.getPose(plan, elapsed)
        fetchPose = pose
        val anchors: CarePoseAnchors = loaded.spec.anchors[pose.careFrame]
        onFetchFrame(CorgiFetchFrame.fromPose(plan, pose, anchors))
    }

    private fun finish(outcome: CareSceneResult?): Unit {
        val previousOwner: String = owner ?: return
        owner = null
        scene?.cancel()
        scene = null
        fetchPlan = null
        fetchPose = null
        onFetchFrame(null)
        pack?.bitmap?.recycle()
        pack = null
        requestId = null
        result = null
        elapsedRemainderMs = 0f
        job?.cancel()
        job = null
        AppServices.applicationScope.launch { coordinator.cancel(previousOwner) }
        onFinished(action, outcome)
    }

    companion object {
        val ACTIONS: Set<CareSceneAction> = CareSceneAction.entries.toSet()
    }
}
