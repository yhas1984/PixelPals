package com.pixelpals.app.debug

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.care.*
import kotlinx.coroutines.*

/** Debug-only visual/soak lab. Does not access the repository or award real care. */
class CareScenePreviewActivity : Activity() {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var stage: CareStageView
    private lateinit var status: TextView
    private var scene: CareSceneController? = null
    private var round: Int = 0
    private var completions: Int = 0
    private var startedAt: Long = 0L
    private var soakJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val layout: LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 48, 24, 16) }
        status = TextView(this).apply { textSize = 18f }
        stage = CareStageView(this)
        layout.addView(status)
        layout.addView(stage, LinearLayout.LayoutParams(-1, 650))
        CareSceneAction.entries.forEach { action ->
            layout.addView(Button(this).apply {
                text = action.name
                setOnClickListener { scope.launch { preview(selectedPet(), action, intent.getBooleanExtra("manual", false)) } }
            })
        }
        setContentView(layout)
        if (intent.getBooleanExtra("soak", false)) startSoak()
        else scope.launch { preview(selectedPet(), CareSceneAction.valueOf(intent.getStringExtra("action") ?: "FEED"), intent.getBooleanExtra("manual", false)) }
    }

    private fun selectedPet(): PetType = runCatching { PetType.valueOf(intent.getStringExtra("pet") ?: "CORGI") }.getOrDefault(PetType.CORGI)

    private suspend fun preview(pet: PetType, action: CareSceneAction, manual: Boolean): Unit {
        stage.stop()
        stage.pack = null
        val pack: CarePosePack = CarePoseLoader.load(assets, pet)
        stage.pack = pack
        val controller: CareSceneController = CareSceneController(action, if (manual) CareSceneMode.MANUAL else CareSceneMode.AUTOMATIC, pack.spec.timings.getValue(action),
            if (action == CareSceneAction.PLAY) CarePlayVariations.shared.nextFor(pet) else CarePlayVariation.DIRECT)
        scene = controller
        status.text = "$pet · $action · ${controller.mode} · debug preview"
        stage.onCompletion = { completions++; Log.i("CARE_SCENE_LAB", "commit=$completions round=$round pet=$pet action=$action") }
        stage.onFinished = { status.append(" · done") }
        stage.start(controller)
    }

    private fun startSoak(): Unit {
        startedAt = SystemClock.elapsedRealtime()
        soakJob = scope.launch {
            val duration: Long = intent.getLongExtra("durationMs", 1_800_000L)
            while (SystemClock.elapsedRealtime() - startedAt < duration) {
                val pet: PetType = PetType.entries[(round / 6) % PetType.entries.size]
                val action: CareSceneAction = CareSceneAction.entries[round % 6]
                preview(pet, action, (round / 90) % 2 == 1)
                var steps: Int = 0
                while (scene?.let { !it.isComplete && !it.isCancelled } == true) {
                    val controller: CareSceneController = requireNotNull(scene)
                    val pack: CarePosePack = requireNotNull(stage.pack)
                    if (controller.mode == CareSceneMode.MANUAL && !controller.hasContact) {
                        val target: CarePoint = CareSceneRenderer().getTarget(pack, controller, stage.width.toFloat(), stage.height.toFloat())
                        controller.movePointer(target.copy(x = target.x + if (steps % 2 == 0) .03f else -.03f), target, action != CareSceneAction.PLAY)
                    }
                    steps++
                    delay(40L)
                }
                round++
                val used: Long = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                Log.i("CARE_SCENE_LAB", "round=$round commits=$completions elapsed=${SystemClock.elapsedRealtime() - startedAt} heap=$used bitmap=${stage.pack?.bitmap?.allocationByteCount}")
            }
            Log.i("CARE_SCENE_LAB", "SOAK_COMPLETE rounds=$round commits=$completions elapsed=${SystemClock.elapsedRealtime() - startedAt}")
            status.text = "SOAK COMPLETE · rounds=$round · commits=$completions"
        }
    }

    override fun onPause(): Unit { soakJob?.cancel(); stage.stop(); super.onPause() }
    override fun onDestroy(): Unit { scope.cancel(); stage.pack = null; super.onDestroy() }
}
