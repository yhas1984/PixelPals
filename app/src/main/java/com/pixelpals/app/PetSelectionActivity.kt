package com.pixelpals.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * PetSelectionActivity — Pantalla de selección de mascota.
 *
 * Presenta las mascotas en una grilla con sus sprites.
 * Al tocar una, se selecciona y se lanza el servicio con esa mascota.
 */
class PetSelectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PET_TYPE = "pet_type"
    }

    private lateinit var cardBloop: LinearLayout
    private lateinit var cardNubeMichi: LinearLayout
    private lateinit var cardJelly: LinearLayout
    private lateinit var cardCorgi: LinearLayout
    private lateinit var cardGinger: LinearLayout
    private lateinit var cardPatito: LinearLayout
    private lateinit var cardDiablillo: LinearLayout

    private var selectedType: PetType? = null
    private val allCards = mutableListOf<LinearLayout>()
    private var isLaunching = false
    private lateinit var selectedPetStore: SelectedPetStore

    private fun debugLog(runId: String, hypothesisId: String, location: String, message: String, data: JSONObject) {
        // #region agent log
        val payload = JSONObject().apply {
            put("sessionId", "a40953")
            put("runId", runId)
            put("hypothesisId", hypothesisId)
            put("location", location)
            put("message", message)
            put("data", data)
            put("timestamp", System.currentTimeMillis())
        }
        Log.i("AGENT_DEBUG", payload.toString())
        // #endregion
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pet_selection)
        selectedPetStore = SelectedPetStore(this)

        bindViews()
        setupCards()
        animateEntrance()
    }

    private fun bindViews() {
        cardBloop = findViewById(R.id.cardBloop)
        cardNubeMichi = findViewById(R.id.cardNubeMichi)
        cardJelly = findViewById(R.id.cardJelly)
        cardCorgi = findViewById(R.id.cardCorgi)
        cardGinger = findViewById(R.id.cardGinger)
        cardPatito = findViewById(R.id.cardPatito)
        cardDiablillo = findViewById(R.id.cardDiablillo)
        allCards.addAll(listOf(cardBloop, cardNubeMichi, cardJelly, cardCorgi, cardGinger, cardPatito, cardDiablillo))
    }

    private fun setupCards() {
        val petTypes = listOf(
            cardBloop to PetType.BLOOP,
            cardNubeMichi to PetType.NUBE_MICHI,
            cardJelly to PetType.JELLY,
            cardCorgi to PetType.CORGI,
            cardGinger to PetType.GINGER,
            cardPatito to PetType.PATITO,
            cardDiablillo to PetType.DIABLILLO
        )

        petTypes.forEach { (card, type) ->
            card.setOnClickListener {
                selectPet(card, type)
            }
        }
    }

    private fun selectPet(selectedCard: LinearLayout, type: PetType) {
        if (isLaunching) return
        isLaunching = true
        selectedType = type
        selectedPetStore.save(type)
        debugLog(
            runId = "post-fix",
            hypothesisId = "H3",
            location = "PetSelectionActivity.kt:selectPet",
            message = "Selección de mascota y launch bloqueado a un solo intento",
            data = JSONObject().apply {
                put("selectedType", type.name)
            }
        )

        // ── Visual selection feedback ──
        allCards.forEach { card ->
            card.isEnabled = false
            if (card == selectedCard) {
                card.setBackgroundResource(R.drawable.bg_card_pet_selected)
                card.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            } else {
                card.setBackgroundResource(R.drawable.bg_card_pet)
                card.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(200)
                    .start()
            }
        }

        // ── Launch pet after a brief delay ──
        selectedCard.postDelayed({
            launchPet(type)
        }, 400)
    }

    private fun launchPet(type: PetType) {
        debugLog(
            runId = "post-fix",
            hypothesisId = "H3",
            location = "PetSelectionActivity.kt:launchPet",
            message = "Lanzando servicio de mascota",
            data = JSONObject().apply {
                put("type", type.name)
                put("launchLocked", isLaunching)
            }
        )
        val serviceIntent = Intent(this, PetService::class.java).apply {
            putExtra(EXTRA_PET_TYPE, type.name)
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        Toast.makeText(
            this,
            "¡${type.displayName} está explorando! 🐾",
            Toast.LENGTH_SHORT
        ).show()

        finish()
    }

    private fun animateEntrance() {
        val title = findViewById<TextView>(R.id.titleSelect)
        val views = listOf(title) + allCards

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay((index * 100).toLong())
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }
}
