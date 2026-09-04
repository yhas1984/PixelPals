package com.pixelpals.app.feature.care

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pixelpals.app.R
import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.care.scene.*
import kotlinx.coroutines.*
import kotlin.math.hypot

/** The same compact, accessible care controls are used in the room and overlay. */
class CareScenePanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val stage: CareStageView = CareStageView(context)
    private val message: TextView = TextView(context)
    private val cancelButton: Button = Button(context)
    private val retryButton: Button = Button(context)
    private val buttons: MutableMap<CareSceneAction, Button> = mutableMapOf()
    private var model: CareSceneViewModel? = null
    private var scope: CoroutineScope? = null
    private var activeRequest: String? = null
    private var displayedResult: String? = null
    private var isAnimationFinished: Boolean = false
    private var pendingPointer: Triple<Float, Float, Boolean>? = null
    private var loadJob: Job? = null
    var onResult: ((CareSceneResult) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(8))
        setBackgroundResource(R.drawable.bg_card)
        message.apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            typeface = Typeface.create("sans-serif-rounded", Typeface.NORMAL)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = context.getString(R.string.care_scene_loading)
        }
        addView(message, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(stage, LayoutParams(LayoutParams.MATCH_PARENT, dp(220)))
        for (row: List<CareSceneAction> in CareSceneAction.entries.chunked(3)) {
            val line: LinearLayout = LinearLayout(context).apply { orientation = HORIZONTAL }
            row.forEach { action ->
                val button: Button = Button(context).apply {
                    text = context.getString(label(action)); isAllCaps = false; textSize = 12f
                    minWidth = 0; minimumWidth = 0; setPadding(dp(2), 0, dp(2), 0)
                    background = toolBackground()
                    setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                        intArrayOf(Color.parseColor("#91869D"), ContextCompat.getColor(context, R.color.text_primary))))
                    setCompoundDrawables(null, CareToolDrawable(action, dp(22)), null, null)
                    compoundDrawablePadding = dp(2)
                    elevation = 0f
                    contentDescription = context.getString(R.string.care_scene_tool_description, text)
                    setOnClickListener { start(action, CareSceneMode.AUTOMATIC) }
                }
                wireDrag(button, action)
                buttons[action] = button
                line.addView(button, LayoutParams(0, dp(62), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        val footer: LinearLayout = LinearLayout(context).apply { orientation = HORIZONTAL }
        cancelButton.apply {
            text = context.getString(R.string.care_scene_close); isAllCaps = false
            background = toolBackground()
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 12f
            elevation = 0f
            setOnClickListener { cancel(); onClose?.invoke() }
        }
        retryButton.apply {
            text = context.getString(R.string.dashboard_retry); isAllCaps = false; visibility = GONE
            setOnClickListener { cancel(); model?.refresh(); loadPack() }
        }
        footer.addView(cancelButton, LayoutParams(0, dp(48), 1f))
        footer.addView(retryButton, LayoutParams(0, dp(48), 1f))
        addView(footer)
    }

    fun bind(viewModel: CareSceneViewModel): Unit {
        model = viewModel
        buttons.forEach { (action, button) ->
            button.setCompoundDrawables(null, CareToolDrawable(action, dp(22), viewModel.pet), null, null)
        }
        stage.contentDescription = context.getString(R.string.care_scene_pet_description, context.getString(viewModel.pet.displayNameResId))
        if (isAttachedToWindow) connect()
    }

    fun start(action: CareSceneAction, mode: CareSceneMode = CareSceneMode.AUTOMATIC): Unit {
        if (stage.pack == null) return
        model?.start(action, mode)
    }

    fun cancel(): Unit {
        pendingPointer = null
        stage.stop()
        model?.cancel()
        activeRequest = null
        displayedResult = null
        message.setText(R.string.care_scene_hint)
    }

    fun pausePresentation(): Unit {
        cancel()
        scope?.cancel()
        scope = null
        stage.pack = null
    }

    fun resumePresentation(): Unit {
        if (isAttachedToWindow && scope == null) connect()
        model?.refresh()
    }

    private fun connect(): Unit {
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        loadPack()
        scope?.launch { model?.state?.collect { render(it) } }
    }

    private fun loadPack(): Unit {
        if (stage.pack != null || loadJob?.isActive == true) return
        val current: CareSceneViewModel = model ?: return
        message.setText(R.string.care_scene_loading)
        retryButton.visibility = GONE
        loadJob = scope?.launch {
            try {
                stage.pack = CarePoseLoader.load(context.assets, current.pet)
                render(current.state.value)
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) {
                message.setText(R.string.care_scene_assets_error)
                retryButton.visibility = VISIBLE
            }
        }
    }

    private fun render(state: CareSceneUiState): Unit {
        val ready: Boolean = stage.pack != null && !state.isLoading && !state.hasError
        buttons.forEach { (action, button) ->
            val isManualSource: Boolean = state.session?.request?.let {
                it.mode == CareSceneMode.MANUAL && it.action == action
            } == true
            button.isEnabled = ready && (!state.isBusy || isManualSource) && (action != CareSceneAction.MEDICINE ||
                state.snapshot?.let { isMedicineAvailable(it, System.currentTimeMillis()) } == true)
            if (action == CareSceneAction.MEDICINE) button.contentDescription = context.getString(
                if (button.isEnabled) R.string.care_scene_medicine_ready else R.string.care_scene_medicine_unavailable)
        }
        stage.isGentle = state.snapshot?.condition in setOf(PetCondition.SICK, PetCondition.RECOVERING)
        val session: CareSceneSession? = state.session
        if (state.hasError) { message.setText(R.string.dashboard_error); retryButton.visibility = VISIBLE }
        if (session == null) {
            if (activeRequest != null) { stage.stop(); activeRequest = null }
            if (ready && displayedResult == null) message.setText(if (state.isBusy) R.string.care_scene_busy else R.string.care_scene_hint)
            return
        }
        if (activeRequest != session.request.id && session.phase == CareScenePhase.READY) beginScene(session)
        if (session.phase == CareScenePhase.COMMITTING) message.setText(R.string.care_scene_saving)
        if (session.phase == CareScenePhase.FINISHED) finishScene(session)
    }

    private fun beginScene(session: CareSceneSession): Unit {
        val pack: CarePosePack = stage.pack ?: return
        activeRequest = session.request.id
        displayedResult = null
        isAnimationFinished = false
        val scene: CareSceneController = CareSceneController(session.request.action, session.request.mode,
            pack.spec.timings.getValue(session.request.action),
            if (session.request.action == CareSceneAction.PLAY) CarePlayVariations.shared.nextFor(session.request.pet)
            else CarePlayVariation.DIRECT)
        val hint: Int = if (session.request.pet == com.pixelpals.app.core.domain.PetType.DIABLILLO &&
            session.request.action == CareSceneAction.REST) R.string.care_scene_manual_imp_rest else manualHint(session.request.action)
        message.text = if (session.request.mode == CareSceneMode.MANUAL) context.getString(hint)
            else context.getString(R.string.care_scene_action_in_progress, context.getString(label(session.request.action)))
        stage.onCompletion = { model?.complete(session.request.id) }
        stage.onTimeout = { cancel(); message.setText(R.string.care_scene_cancelled) }
        stage.onFinished = { isAnimationFinished = true; model?.state?.value?.session?.let(::finishScene) }
        stage.start(scene)
        if (scene.mode == CareSceneMode.MANUAL) pendingPointer?.let { stage.sendPointer(it.first, it.second, it.third) }
        pendingPointer = null
    }

    private fun finishScene(session: CareSceneSession): Unit {
        val result: CareSceneResult = session.result ?: return
        if (result is CareSceneResult.Completed && !isAnimationFinished) return
        if (displayedResult == session.request.id) return
        displayedResult = session.request.id
        message.text = when (result) {
            is CareSceneResult.Completed -> CareResultFormatter.describe(context, result)
            CareSceneResult.Cancelled -> context.getString(R.string.care_scene_cancelled)
            CareSceneResult.Unavailable -> context.getString(R.string.care_scene_medicine_unavailable)
            CareSceneResult.Error -> context.getString(R.string.dashboard_error)
        }
        retryButton.visibility = if (result == CareSceneResult.Error) VISIBLE else GONE
        if (result is CareSceneResult.Completed) stage.celebrate()
        onResult?.invoke(result)
        model?.cancel()
        model?.refresh()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireDrag(button: Button, action: CareSceneAction): Unit {
        var downX: Float = 0f
        var downY: Float = 0f
        var dragging: Boolean = false
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; dragging = false
                    button.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(event.rawX - downX, event.rawY - downY) > ViewConfiguration.get(context).scaledTouchSlop) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        button.isPressed = false
                        start(action, CareSceneMode.MANUAL)
                    }
                    if (dragging) sendPointer(event.rawX, event.rawY, true)
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) sendPointer(event.rawX, event.rawY, false)
                    button.parent?.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL -> {
                    button.parent?.requestDisallowInterceptTouchEvent(false)
                    if (dragging) cancel()
                }
            }
            dragging
        }
    }

    private fun sendPointer(x: Float, y: Float, isDown: Boolean): Unit {
        if (activeRequest == null) pendingPointer = Triple(x, y, isDown)
        else stage.sendPointer(x, y, isDown)
    }

    private fun toolBackground(): StateListDrawable {
        fun shape(color: String): GradientDrawable = GradientDrawable().apply {
            setColor(Color.parseColor(color)); cornerRadius = dp(14).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape("#F0ECF4"))
            addState(intArrayOf(android.R.attr.state_pressed), shape("#DDD1F5"))
            addState(intArrayOf(), shape("#F3ECFC"))
        }
    }

    override fun onAttachedToWindow(): Unit { super.onAttachedToWindow(); if (model != null) connect() }
    override fun onDetachedFromWindow(): Unit { pausePresentation(); super.onDetachedFromWindow() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun label(action: CareSceneAction): Int = when (action) {
            CareSceneAction.FEED -> R.string.action_feed
            CareSceneAction.PLAY -> R.string.action_play
            CareSceneAction.PET -> R.string.care_scene_pet
            CareSceneAction.CLEAN -> R.string.action_clean
            CareSceneAction.REST -> R.string.action_rest
            CareSceneAction.MEDICINE -> R.string.action_medicine
        }
        private fun manualHint(action: CareSceneAction): Int = when (action) {
            CareSceneAction.FEED -> R.string.care_scene_manual_feed
            CareSceneAction.PLAY -> R.string.care_scene_manual_play
            CareSceneAction.PET -> R.string.care_scene_manual_pet
            CareSceneAction.CLEAN -> R.string.care_scene_manual_clean
            CareSceneAction.REST -> R.string.care_scene_manual_rest
            CareSceneAction.MEDICINE -> R.string.care_scene_manual_medicine
        }
    }
}
