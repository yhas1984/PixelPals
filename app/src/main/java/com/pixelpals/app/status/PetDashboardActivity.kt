package com.pixelpals.app.status

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.PetService
import com.pixelpals.app.R
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetPersonality
import com.pixelpals.app.feature.store.StoreActivity
import kotlinx.coroutines.launch

class PetDashboardActivity : AppCompatActivity() {
    private lateinit var selectedPetStore: SelectedPetStore
    private val repository: PixelPalsRepository by lazy { AppServices.repository(this) }
    private val analytics by lazy { AppServices.analytics(this) }

    private lateinit var selectedPet: PetType

    private lateinit var txtDashboardSubtitle: TextView
    private lateinit var txtCompanionLine: TextView
    private lateinit var txtMoodSummary: TextView
    private lateinit var txtBondJourney: TextView
    private lateinit var txtSuggestion: TextView
    private lateinit var txtProgressHighlights: TextView
    private lateinit var txtHealth: TextView
    private lateinit var txtEnergy: TextView
    private lateinit var txtHunger: TextView
    private lateinit var txtHygiene: TextView
    private lateinit var txtBond: TextView
    private lateinit var progressHealth: ProgressBar
    private lateinit var progressEnergy: ProgressBar
    private lateinit var progressHunger: ProgressBar
    private lateinit var progressHygiene: ProgressBar
    private lateinit var progressBond: ProgressBar
    private lateinit var tasksContainer: LinearLayout
    private lateinit var memoriesContainer: LinearLayout
    private lateinit var cardDashboardState: LinearLayout
    private lateinit var txtDashboardState: TextView
    private lateinit var progressDashboardLoading: ProgressBar
    private lateinit var btnDashboardRetry: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.dashboard_title)
        edgeToEdge()
        setContentView(R.layout.activity_pet_dashboard)
        selectedPetStore = SelectedPetStore(this)
        selectedPet = selectedPetStore.load()
        bindViews()
        applySystemBarsInsets()
        setupActions()
        lifecycleScope.launch { refreshDashboard(applyCheckIn = true) }
    }

    private fun bindViews() {
        txtDashboardSubtitle = findViewById(R.id.txtDashboardSubtitle)
        txtCompanionLine = findViewById(R.id.txtCompanionLine)
        txtMoodSummary = findViewById(R.id.txtMoodSummary)
        txtBondJourney = findViewById(R.id.txtBondJourney)
        txtSuggestion = findViewById(R.id.txtSuggestion)
        txtProgressHighlights = findViewById(R.id.txtProgressHighlights)
        txtHealth = findViewById(R.id.txtHealth)
        txtEnergy = findViewById(R.id.txtEnergy)
        txtHunger = findViewById(R.id.txtHunger)
        txtHygiene = findViewById(R.id.txtHygiene)
        txtBond = findViewById(R.id.txtBond)
        progressHealth = findViewById(R.id.progressHealth)
        progressEnergy = findViewById(R.id.progressEnergy)
        progressHunger = findViewById(R.id.progressHunger)
        progressHygiene = findViewById(R.id.progressHygiene)
        progressBond = findViewById(R.id.progressBond)
        tasksContainer = findViewById(R.id.tasksContainer)
        memoriesContainer = findViewById(R.id.memoriesContainer)
        cardDashboardState = findViewById(R.id.cardDashboardState)
        txtDashboardState = findViewById(R.id.txtDashboardState)
        progressDashboardLoading = findViewById(R.id.progressDashboardLoading)
        btnDashboardRetry = findViewById(R.id.btnDashboardRetry)
    }

    private fun setupActions() {
        findViewById<Button>(R.id.btnFeed).setOnClickListener { performCare(CareAction.FEED) }
        findViewById<Button>(R.id.btnClean).setOnClickListener { performCare(CareAction.CLEAN) }
        findViewById<Button>(R.id.btnPlay).setOnClickListener { performCare(CareAction.PLAY) }
        findViewById<Button>(R.id.btnRest).setOnClickListener { performCare(CareAction.REST) }
        findViewById<Button>(R.id.btnDashboardStore).setOnClickListener {
            startActivity(Intent(this, StoreActivity::class.java))
        }
        btnDashboardRetry.setOnClickListener { lifecycleScope.launch { refreshDashboard(applyCheckIn = false) } }
    }

    private fun performCare(action: CareAction) {
        lifecycleScope.launch {
            showLoadingState(getString(R.string.dashboard_loading))
            runCatching {
                val before = repository.getStatusSnapshot(selectedPet)
                val memoriesBefore = repository.getMemories(selectedPet).map { it.id }.toSet()
                val after = repository.applyCareAction(selectedPet, action)
                val newMemory = repository.getMemories(selectedPet).firstOrNull { it.id !in memoriesBefore }
                analytics.track(
                    "dashboard_action",
                    mapOf("pet_id" to selectedPet.name.lowercase(), "action" to action.name.lowercase())
                )
                renderDashboard()
                val bondGain = (after.bond - before.bond).coerceAtLeast(0)
                val coinGain = (after.softCurrency - before.softCurrency).coerceAtLeast(0)
                val message = when {
                    newMemory != null -> getString(R.string.dashboard_memory_unlocked, newMemory.title)
                    bondGain > 0 || coinGain > 0 -> getString(R.string.dashboard_care_reward, bondGain, coinGain)
                    else -> getString(
                        R.string.dashboard_care_received,
                        getString(selectedPet.displayNameResId),
                    )
                }
                showSuccessState(message)
                PetService.requestPetRefresh(
                    this@PetDashboardActivity,
                    careBubble(action),
                    celebrate = bondGain > 0 || newMemory != null,
                )
            }.onFailure {
                showErrorState(getString(R.string.dashboard_error))
            }
        }
    }

    private suspend fun refreshDashboard(applyCheckIn: Boolean) {
        showLoadingState(getString(R.string.dashboard_loading))
        runCatching {
            if (applyCheckIn) {
                val before = repository.getStatusSnapshot(selectedPet)
                val after = repository.applyCareAction(selectedPet, CareAction.CHECK_IN)
                if (after.bond > before.bond) {
                    PetService.requestPetRefresh(
                        this@PetDashboardActivity,
                        careBubble(CareAction.CHECK_IN),
                        celebrate = true,
                    )
                }
            }
            renderDashboard()
        }.onFailure {
            showErrorState(getString(R.string.dashboard_error))
        }
    }

    private suspend fun renderDashboard() {
        val snapshot = repository.getStatusSnapshot(selectedPet)
        val tasks = repository.getDailyTasks(selectedPet)
        val memories = repository.getMemories(selectedPet)

        txtDashboardSubtitle.text = getString(
            R.string.dashboard_subtitle_format,
            getString(selectedPet.displayNameResId),
            snapshot.careStreakDays,
            snapshot.softCurrency
        )
        txtCompanionLine.text = getString(
            R.string.dashboard_companion_line_format,
            getString(selectedPet.displayNameResId),
            moodLabel(snapshot.mood),
            personalityLabel(repository.getPersonality(selectedPet))
        )
        txtMoodSummary.text = getString(
            R.string.dashboard_bond_stage_format,
            getString(bondStageResource(snapshot.bond)),
            snapshot.bond,
        )
        txtBondJourney.text = bondJourney(snapshot.bond)
        txtSuggestion.text = getString(
            R.string.dashboard_suggestion_format,
            careActionLabel(snapshot.dominantSuggestion)
        )
        txtProgressHighlights.text = getString(
            R.string.dashboard_highlights_format,
            tasks.count { it.completed },
            tasks.size,
            memories.size
        )
        txtHealth.text = getString(R.string.dashboard_health_format, snapshot.health)
        txtEnergy.text = getString(R.string.dashboard_energy_format, snapshot.energy)
        txtHunger.text = getString(R.string.dashboard_hunger_format, snapshot.hunger)
        txtHygiene.text = getString(R.string.dashboard_hygiene_format, snapshot.hygiene)
        txtBond.text = getString(R.string.dashboard_bond_format, snapshot.bond)
        progressHealth.progress = snapshot.health
        progressEnergy.progress = snapshot.energy
        progressHunger.progress = snapshot.hunger
        progressHygiene.progress = snapshot.hygiene
        progressBond.progress = snapshot.bond

        tasksContainer.removeAllViews()
        tasks.forEach { task ->
            val view = TextView(this).apply {
                text = if (task.completed) {
                    getString(
                        R.string.dashboard_task_completed_format,
                        task.title,
                        task.description,
                        getString(R.string.task_done),
                        task.rewardCoins
                    )
                } else {
                    getString(
                        R.string.dashboard_task_pending_format,
                        task.title,
                        task.description,
                        task.rewardCoins
                    )
                }
                textSize = 13f
                setTextColor(if (task.completed) ContextCompat.getColor(this@PetDashboardActivity, R.color.text_primary) else ContextCompat.getColor(this@PetDashboardActivity, R.color.text_secondary))
                setPadding(0, 6, 0, 6)
            }
            tasksContainer.addView(view)
        }

        memoriesContainer.removeAllViews()
        memories.forEach { memory ->
            val view = TextView(this).apply {
                text = getString(R.string.dashboard_memory_format, memory.title, memory.subtitle)
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@PetDashboardActivity, R.color.text_secondary))
                setPadding(0, 6, 0, 10)
            }
            memoriesContainer.addView(view)
        }

        analytics.track(
            "dashboard_opened",
            mapOf(
                "pet_id" to snapshot.petId,
                "mood" to snapshot.mood.name,
                "bond" to snapshot.bond.toString()
            )
        )
        txtDashboardState.text = getString(R.string.dashboard_state_ready)
        txtDashboardState.setTextColor(ContextCompat.getColor(this, R.color.status_success_fg))
        cardDashboardState.setBackgroundResource(R.drawable.bg_status_success)
        progressDashboardLoading.visibility = View.GONE
        btnDashboardRetry.visibility = View.GONE
        updateActionButtonEmphasis(snapshot.dominantSuggestion)
    }

    private fun updateActionButtonEmphasis(recommended: CareAction) {
        val buttons = mapOf(
            CareAction.FEED to findViewById<Button>(R.id.btnFeed),
            CareAction.CLEAN to findViewById<Button>(R.id.btnClean),
            CareAction.PLAY to findViewById<Button>(R.id.btnPlay),
            CareAction.REST to findViewById<Button>(R.id.btnRest)
        )
        buttons.forEach { (action, button) ->
            button.alpha = if (action == recommended) 1f else 0.78f
            button.scaleX = if (action == recommended) 1.02f else 1f
            button.scaleY = if (action == recommended) 1.02f else 1f
        }
    }

    private fun showLoadingState(message: String) {
        txtDashboardState.text = message
        txtDashboardState.setTextColor(ContextCompat.getColor(this, R.color.status_info_fg))
        cardDashboardState.setBackgroundResource(R.drawable.bg_status_info)
        progressDashboardLoading.visibility = View.VISIBLE
        btnDashboardRetry.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        txtDashboardState.text = message
        txtDashboardState.setTextColor(ContextCompat.getColor(this, R.color.red_error))
        cardDashboardState.setBackgroundResource(R.drawable.bg_status_error)
        progressDashboardLoading.visibility = View.GONE
        btnDashboardRetry.visibility = View.VISIBLE
    }

    private fun showSuccessState(message: String) {
        txtDashboardState.text = message
        txtDashboardState.setTextColor(ContextCompat.getColor(this, R.color.status_success_fg))
        cardDashboardState.setBackgroundResource(R.drawable.bg_status_success)
        progressDashboardLoading.visibility = View.GONE
        btnDashboardRetry.visibility = View.GONE
    }

    private fun bondStageResource(bond: Int): Int {
        return when {
            bond >= 70 -> R.string.bond_stage_soulmates
            bond >= 35 -> R.string.bond_stage_inseparable
            bond >= 12 -> R.string.bond_stage_close
            else -> R.string.bond_stage_new
        }
    }

    private fun bondJourney(bond: Int): String {
        val goal = when {
            bond < 12 -> R.string.bond_goal_accessory to 12
            bond < 35 -> R.string.bond_goal_routine to 35
            bond < 70 -> R.string.bond_goal_strong to 70
            else -> return getString(R.string.dashboard_bond_complete)
        }
        return getString(
            R.string.dashboard_next_goal_format,
            getString(goal.first),
            goal.second - bond,
        )
    }

    private fun careBubble(action: CareAction): String {
        return getString(
            when (action) {
                CareAction.FEED -> R.string.care_bubble_feed
                CareAction.CLEAN -> R.string.care_bubble_clean
                CareAction.PLAY -> R.string.care_bubble_play
                CareAction.REST -> R.string.care_bubble_rest
                CareAction.CHECK_IN -> R.string.care_bubble_check_in
            }
        )
    }

    private fun edgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.surface_base)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.surface_base)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun applySystemBarsInsets() {
        val view = findViewById<ScrollView>(R.id.dashboardScroll)
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun moodLabel(mood: PetMood): String {
        return getString(
            when (mood) {
                PetMood.HAPPY -> R.string.mood_happy
                PetMood.SLEEPY -> R.string.mood_sleepy
                PetMood.HUNGRY -> R.string.mood_hungry
                PetMood.DIRTY -> R.string.mood_dirty
                PetMood.BORED -> R.string.mood_bored
                PetMood.EXCITED -> R.string.mood_excited
            }
        )
    }

    private fun careActionLabel(action: CareAction): String {
        return getString(
            when (action) {
                CareAction.FEED -> R.string.action_feed
                CareAction.CLEAN -> R.string.action_clean
                CareAction.PLAY -> R.string.action_play
                CareAction.REST -> R.string.action_rest
                CareAction.CHECK_IN -> R.string.action_check_in
            }
        )
    }

    private fun personalityLabel(personality: PetPersonality): String {
        return getString(
            when (personality) {
                PetPersonality.SWEET -> R.string.personality_sweet
                PetPersonality.DREAMY -> R.string.personality_dreamy
                PetPersonality.BOUNCY -> R.string.personality_bouncy
                PetPersonality.LOYAL -> R.string.personality_loyal
                PetPersonality.ELEGANT -> R.string.personality_elegant
                PetPersonality.ANGELIC -> R.string.personality_angelic
                PetPersonality.CURIOUS -> R.string.personality_curious
                PetPersonality.CHAOTIC -> R.string.personality_chaotic
            }
        )
    }
}
