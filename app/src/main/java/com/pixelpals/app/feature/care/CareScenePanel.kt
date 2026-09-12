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
import android.view.ViewGroup
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
    private val actionGrid: CareButtonGrid = CareButtonGrid(context)
    private val buttons: MutableMap<CareSceneAction, Button> = mutableMapOf()
    private var model: CareSceneViewModel? = null
    private var scope: CoroutineScope? = null
    private var activeRequest: String? = null
    private var displayedResult: String? = null
    private var isAnimationFinished: Boolean = false
    private var pendingPointer: Triple<Float, Float, Boolean>? = null
    private var loadJob: Job? = null
    private var pendingAction: Pair<CareSceneAction, CareSceneMode>? = null
    var onResult: ((CareSceneResult) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    fun setHomeEnvironment(environment: com.pixelpals.app.feature.home.HomeEnvironment): Unit { stage.environment = environment; stage.invalidate() }

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
        for (action: CareSceneAction in CareSceneAction.entries) {
            val button: Button = Button(context).apply {
                text = context.getString(label(action)); isAllCaps = false; textSize = 12f
                minWidth = 0; minimumWidth = 0; minimumHeight = dp(62); setPadding(dp(2), 0, dp(2), 0)
                background = toolBackground()
                setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(ContextCompat.getColor(context, R.color.text_secondary), ContextCompat.getColor(context, R.color.text_primary))))
                setCompoundDrawables(null, CareToolDrawable(action, dp(22)), null, null)
                compoundDrawablePadding = dp(2)
                elevation = 0f
                contentDescription = context.getString(R.string.care_scene_tool_description, text)
                setOnClickListener { start(action, CareSceneMode.AUTOMATIC) }
            }
            wireDrag(button, action)
            buttons[action] = button
            actionGrid.addView(button, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addView(actionGrid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val footer: LinearLayout = com.pixelpals.app.feature.home.HomeUi.row(context, cancelButton, retryButton).apply {
            isBaselineAligned = false
        }
        cancelButton.apply {
            text = context.getString(R.string.care_scene_close); isAllCaps = false
            background = toolBackground()
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 12f
            minimumHeight = dp(48)
            setPadding(dp(2), 0, dp(2), 0)
            elevation = 0f
            setOnClickListener { cancel(); onClose?.invoke() }
        }
        retryButton.apply {
            text = context.getString(R.string.dashboard_retry); isAllCaps = false; visibility = GONE
            background = toolBackground()
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            minimumHeight = dp(48)
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { cancel(); model?.refresh(); loadPack() }
        }
        addView(footer)
    }

    fun bind(viewModel: CareSceneViewModel): Unit {
        if (model?.pet != viewModel.pet) {
            pausePresentation()
            stage.toyDecorationId = null
            stage.bedDecorationId = null
        }
        model = viewModel
        refreshToolIcons(viewModel.pet)
        stage.contentDescription = context.getString(R.string.care_scene_pet_description, context.getString(viewModel.pet.displayNameResId))
        if (isAttachedToWindow) connect()
    }

    private fun refreshToolIcons(pet: com.pixelpals.app.core.domain.PetType) {
        buttons.forEach { (action, button) ->
            button.setCompoundDrawables(null, CareToolDrawable(action, dp(22), pet,
                bedDecorationId = stage.bedDecorationId, toyDecorationId = stage.toyDecorationId), null, null)
        }
    }

    fun start(action: CareSceneAction, mode: CareSceneMode = CareSceneMode.AUTOMATIC): Unit {
        if (stage.pack == null) { pendingAction = action to mode; return }
        model?.start(action, mode)
    }

    fun cancel(): Unit {
        pendingAction = null
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
                val decorationDao = com.pixelpals.app.core.services.AppServices.companions(context).dao
                val placements = decorationDao.getPlacements(current.pet.name.lowercase())
                stage.toyDecorationId = com.pixelpals.app.feature.home.CareDecorationSelection.careToy(
                    current.pet, placements, decorationDao.getHome(current.pet.name.lowercase())?.favoriteObject)
                stage.bedDecorationId = com.pixelpals.app.feature.home.CareDecorationSelection.bed(
                    placements)
                refreshToolIcons(current.pet)
                stage.pack = CarePoseLoader.load(context.assets, current.pet)
                render(current.state.value)
                pendingAction?.let { (action, mode) -> pendingAction = null; current.start(action, mode) }
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
            is CareSceneResult.Completed -> CareResultFormatter.describe(context, result, session.request.action)
            CareSceneResult.Cancelled -> context.getString(R.string.care_scene_cancelled)
            CareSceneResult.Unavailable -> context.getString(R.string.care_scene_medicine_unavailable)
            CareSceneResult.Error -> context.getString(R.string.dashboard_error)
        }
        retryButton.visibility = if (result == CareSceneResult.Error) VISIBLE else GONE
        if (result is CareSceneResult.Completed) stage.celebrate()
        onResult?.invoke(result)
        // Jelly's REST and MEDICINE actions finish on authored poses. Keep the
        // final frame while releasing ownership; resetting here would switch to
        // the idle PET pose at a different room baseline.
        if (result is CareSceneResult.Completed &&
            session.request.pet == com.pixelpals.app.core.domain.PetType.JELLY &&
            session.request.action in setOf(CareSceneAction.REST, CareSceneAction.MEDICINE) && isAnimationFinished) {
            activeRequest = null
        }
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
        fun shape(color: Int): GradientDrawable = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, color)); cornerRadius = dp(14).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(R.color.surface_subtle))
            addState(intArrayOf(android.R.attr.state_pressed), shape(R.color.surface_tinted))
            addState(intArrayOf(), shape(R.color.surface_warm))
        }
    }

    override fun onAttachedToWindow(): Unit { super.onAttachedToWindow(); if (model != null) connect() }
    override fun onDetachedFromWindow(): Unit { pausePresentation(); super.onDetachedFromWindow() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class CareButtonGrid(context: Context) : ViewGroup(context) {
        private val horizontalMargin: Int = dp(3)
        private val minimumCellWidth: Int = (dp(108) * resources.configuration.fontScale).toInt()
        private var columnCount: Int = 1
        private val rowHeights: MutableList<Int> = mutableListOf()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Unit {
            val width: Int = MeasureSpec.getSize(widthMeasureSpec)
            columnCount = (width / minimumCellWidth).coerceIn(1, 3)
            rowHeights.clear()
            val cellWidth: Int = width / columnCount
            for (index: Int in 0 until childCount) {
                val child: View = getChildAt(index)
                val childWidth: Int = (cellWidth - horizontalMargin * 2).coerceAtLeast(0)
                child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                val row: Int = index / columnCount
                while (rowHeights.size <= row) rowHeights.add(0)
                rowHeights[row] = maxOf(rowHeights[row], child.measuredHeight)
            }
            for (index: Int in 0 until childCount) {
                val child: View = getChildAt(index)
                child.measure(MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(rowHeights[index / columnCount], MeasureSpec.EXACTLY))
            }
            val height: Int = rowHeights.sum() + rowHeights.size * horizontalMargin * 2
            setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit {
            val cellWidth: Int = width / columnCount
            var rowTop: Int = horizontalMargin
            for (row: Int in rowHeights.indices) {
                for (column: Int in 0 until columnCount) {
                    val index: Int = row * columnCount + column
                    if (index >= childCount) break
                    val child: View = getChildAt(index)
                    val childLeft: Int = column * cellWidth + horizontalMargin
                    child.layout(childLeft, rowTop, childLeft + child.measuredWidth, rowTop + child.measuredHeight)
                }
                rowTop += rowHeights[row] + horizontalMargin * 2
            }
        }

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    }

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
