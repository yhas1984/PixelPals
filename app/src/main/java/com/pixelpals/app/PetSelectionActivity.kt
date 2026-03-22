package com.pixelpals.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * PetSelectionActivity — Pantalla de selección de mascota.
 *
 * Presenta las 5 mascotas en una grilla con sus sprites.
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

    private var selectedType: PetType? = null
    private val allCards = mutableListOf<LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pet_selection)

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
        allCards.addAll(listOf(cardBloop, cardNubeMichi, cardJelly, cardCorgi, cardGinger))
    }

    private fun setupCards() {
        val petTypes = listOf(
            cardBloop to PetType.BLOOP,
            cardNubeMichi to PetType.NUBE_MICHI,
            cardJelly to PetType.JELLY,
            cardCorgi to PetType.CORGI,
            cardGinger to PetType.GINGER
        )

        petTypes.forEach { (card, type) ->
            card.setOnClickListener {
                selectPet(card, type)
            }
        }
    }

    private fun selectPet(selectedCard: LinearLayout, type: PetType) {
        selectedType = type

        // ── Visual selection feedback ──
        allCards.forEach { card ->
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
