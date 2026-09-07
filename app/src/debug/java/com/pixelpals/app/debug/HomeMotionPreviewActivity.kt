package com.pixelpals.app.debug

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.database.HomeDecorationEntity
import com.pixelpals.app.feature.home.*
import kotlinx.coroutines.*

/** Reproducible art review. Does not select, adopt, feed or mutate the user's pets. */
class HomeMotionPreviewActivity : Activity() {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var scene: HomeSceneView
    private lateinit var heading: TextView
    private var loadJob: Job? = null
    private var selected: PetType = PetType.CORGI
    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val column: LinearLayout = HomeUi.column(this)
        column.setPadding(24, 80, 24, 32)
        heading = TextView(this).apply { textSize = 26f; setTextColor(HomeUi.ink) }
        column.addView(heading)
        scene = HomeSceneView(this)
        scene.bond = 75
        column.addView(scene)
        column.addView(TextView(this).apply {
            text = "Preparación → pasos → frenada → juego → giro → descanso.\nLa prueba no modifica tus mascotas ni tu progreso."
            textSize = 17f
            setTextColor(HomeUi.ink)
        })
        column.addView(HomeUi.row(this,
            HomeUi.button(this, "Corgi") { showPet(PetType.CORGI) },
            HomeUi.button(this, "Taro") { showPet(PetType.TARO) },
            HomeUi.button(this, "Siguiente") { showPet(PetType.entries[(selected.ordinal + 1) % PetType.entries.size]) }))
        column.addView(HomeUi.row(this,
            HomeUi.button(this, "Repetir") { showPet(selected) },
            HomeUi.button(this, "Normal / lento") { scene.previewTimeScale = if (scene.previewTimeScale == 1f) .35f else 1f },
            HomeUi.button(this, "Movimiento reducido") { scene.previewReducedMotion = !scene.previewReducedMotion }))
        column.addView(HomeUi.button(this, "Volver a PixelPals") { finish() })
        setContentView(ScrollView(this).apply { setBackgroundColor(HomeUi.cream); addView(column) })
        val requested: PetType = PetType.entries.firstOrNull { it.name == intent.getStringExtra("pet") } ?: PetType.CORGI
        showPet(requested)
    }
    private fun showPet(pet: PetType): Unit {
        selected = pet
        heading.text = getString(pet.displayNameResId)
        scene.placements = listOf(HomeDecorationEntity(pet.name.lowercase(), "linen_bed", 1, 1), HomeDecorationEntity(pet.name.lowercase(), "ball", 4, 2))
        loadJob?.cancel()
        loadJob = scope.launch { scene.loadPet(pet) }
    }
    override fun onResume(): Unit { super.onResume(); scene.resume() }
    override fun onPause(): Unit { scene.pause(); super.onPause() }
    override fun onDestroy(): Unit { scope.cancel(); super.onDestroy() }
}
