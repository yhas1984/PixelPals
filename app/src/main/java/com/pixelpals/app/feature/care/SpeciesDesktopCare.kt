package com.pixelpals.app.feature.care

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.Log
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneController
import com.pixelpals.app.core.care.scene.CareSceneCoordinator
import com.pixelpals.app.core.care.scene.CareSceneMode
import com.pixelpals.app.core.care.scene.CareSceneOrigin
import com.pixelpals.app.core.care.scene.CareScenePhase
import com.pixelpals.app.core.care.scene.CareSceneRequest
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.care.scene.CareSceneSession
import com.pixelpals.app.core.care.scene.CorgiFetchPlan
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Species-aware desktop playback. Corgi keeps its separately tuned fetch controller. */
class SpeciesDesktopCare(
    private val context: Context,
    private val scope: CoroutineScope,
    private val pet: PetType,
    private val coordinator: CareSceneCoordinator = AppServices.careScenes(context),
    private val onFinished: (CareSceneAction, CareSceneResult?) -> Unit,
) : DesktopCarePlayback {
    private val renderer: SpeciesCareRenderer = SpeciesCareRenderer()
    private var action: CareSceneAction = CareSceneAction.FEED
    private var owner: String? = null
    private var requestId: String? = null
    private var job: Job? = null
    private var pack: CarePosePack? = null
    private var scene: CareSceneController? = null
    private var result: CareSceneResult? = null
    private var facingLeft: Boolean = false
    private var elapsedRemainderMs: Float = 0f

    override val isActive: Boolean get() = owner != null
    override val isMovingPet: Boolean get() = false

    init {
        require(pet in DesktopCarePlayback.SUPPORTED_PETS && pet != PetType.CORGI)
    }

    override fun start(action: CareSceneAction, facingLeft: Boolean, fetchPlan: CorgiFetchPlan?): Unit {
        if (isActive) return
        require(action in DesktopCarePlayback.ACTIONS)
        require(fetchPlan == null)
        this.action = action
        this.facingLeft = facingLeft
        val sessionOwner: String = UUID.randomUUID().toString()
        owner = sessionOwner
        job = scope.launch {
            try {
                val request: CareSceneRequest = CareSceneRequest(
                    UUID.randomUUID().toString(), sessionOwner, pet, action,
                    CareSceneOrigin.OVERLAY, CareSceneMode.AUTOMATIC,
                )
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
                val loaded: CarePosePack = CarePoseLoader.load(context.assets, pet)
                pack = loaded
                scene = CareSceneController(action, CareSceneMode.AUTOMATIC, loaded.spec.timings.getValue(action))
                coordinator.session.collect { current: CareSceneSession? ->
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
                Log.w(TAG, "Desktop care unavailable for ${pet.name}", exception)
                finish(CareSceneResult.Error)
            }
        }
    }

    override fun advance(deltaSeconds: Float): Unit {
        val playback: CareSceneController = scene ?: return
        elapsedRemainderMs += deltaSeconds * 1_000f
        val wholeMs: Long = elapsedRemainderMs.toLong()
        elapsedRemainderMs -= wholeMs
        if (playback.advance(wholeMs)) requestId?.let(coordinator::complete)
        finishIfReady()
    }

    override fun draw(canvas: Canvas, spriteSize: Int): Boolean {
        val loaded: CarePosePack = pack ?: return false
        val playback: CareSceneController = scene ?: return false
        canvas.save()
        if (facingLeft) canvas.scale(-1f, 1f, canvas.width / 2f, canvas.height / 2f)
        renderer.draw(
            canvas, loaded, playback,
            reduced = !ValueAnimator.areAnimatorsEnabled(),
            gentle = false,
            desktopSize = spriteSize,
        )
        canvas.restore()
        return true
    }

    override fun cancel(): Unit {
        if (isActive) finish(null)
    }

    private fun finishIfReady(): Unit {
        if (scene?.isComplete == true && result != null) finish(result)
    }

    private fun finish(outcome: CareSceneResult?): Unit {
        val previousOwner: String = owner ?: return
        owner = null
        scene?.cancel()
        scene = null
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
        private const val TAG: String = "SpeciesDesktopCare"
    }
}
