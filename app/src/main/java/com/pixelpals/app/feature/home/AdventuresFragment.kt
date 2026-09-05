package com.pixelpals.app.feature.home

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.pixelpals.app.R
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.feature.treasure.TreasureAlbumActivity
import com.pixelpals.app.database.CompanionJournalEntity
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AdventuresFragment : Fragment() {
    private lateinit var model: CompanionViewModel
    private var body: LinearLayout? = null
    private var travelCard: LinearLayout? = null
    private var journalCard: LinearLayout? = null
    private val scenes: MutableList<HomeSceneView> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?): Unit { super.onCreate(savedInstanceState); model = ViewModelProvider(this)[CompanionViewModel::class.java] }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val column = HomeUi.column(requireContext())
        body = column
        column.addView(HomeUi.text(requireContext(), getString(R.string.adventures_title), 30f))
        column.addView(HomeUi.text(requireContext(), getString(R.string.adventures_subtitle)))
        travelCard = HomeUi.card(requireContext()).also(column::addView)
        ExpeditionDestination.entries.forEach { destination ->
            val card = HomeUi.card(requireContext())
            val scene: HomeSceneView = HomeSceneView(requireContext()).apply {
                environment = destination.environment; showPet = false; isClickable = false
                contentDescription = getString(destination.title)
            }
            scenes.add(scene)
            card.addView(scene, LinearLayout.LayoutParams(-1, HomeUi.dp(requireContext(), 130)))
            card.addView(HomeUi.text(requireContext(), getString(destination.title), 22f))
            card.addView(HomeUi.text(requireContext(), getString(destination.story), 14f))
            card.addView(HomeUi.text(requireContext(), getString(R.string.adventure_reward), 13f))
            card.addView(HomeUi.button(requireContext(), getString(R.string.adventure_depart, destination.durationMs / 60_000), true) {
                if (model.world.value.expedition != null || AppServices.careScenes(requireContext()).session.value != null)
                    Toast.makeText(requireContext(), R.string.adventure_busy, Toast.LENGTH_LONG).show()
                else model.depart(destination)
            })
            column.addView(card)
        }
        column.addView(HomeUi.button(requireContext(), getString(R.string.journal_album)) { startActivity(Intent(requireContext(), TreasureAlbumActivity::class.java)) })
        column.addView(HomeUi.text(requireContext(), getString(R.string.journal_title), 26f))
        journalCard = HomeUi.card(requireContext()).also(column::addView)
        return ScrollView(requireContext()).apply { addView(column) }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { model.world.collect { renderTravel(it) } }
                launch { model.journal.collect { renderJournal(it) } }
                launch { model.memories.collect { renderJournal(model.journal.value) } }
                launch { model.error.collect { if (it) { Toast.makeText(requireContext(), R.string.home_error, Toast.LENGTH_LONG).show(); model.error.value = false } } }
                launch { while (isActive) { model.refresh(); delay(10_000) } }
            }
        }
    }
    private fun renderTravel(world: CompanionWorld): Unit {
        val card: LinearLayout = travelCard ?: return
        card.removeAllViews()
        val expedition = world.expedition
        card.visibility = if (expedition == null) View.GONE else View.VISIBLE
        if (expedition == null) return
        val destination: ExpeditionDestination = ExpeditionDestination.find(expedition.destination) ?: return
        val pet = com.pixelpals.app.core.domain.PetType.entries.firstOrNull { it.name.lowercase() == expedition.petId } ?: return
        card.addView(HomeUi.text(requireContext(), getString(R.string.adventure_travel, getString(pet.displayNameResId), getString(destination.title))))
        val remaining: Long = (destination.durationMs - expedition.elapsedMs).coerceAtLeast(0)
        card.addView(HomeUi.text(requireContext(), getString(R.string.adventure_remaining, (remaining + 59_999) / 60_000), 14f))
        if (remaining == 0L) card.addView(HomeUi.button(requireContext(), getString(R.string.adventure_return), true) {
            model.returnHome(expedition)
        })
        card.addView(HomeUi.button(requireContext(), getString(R.string.adventure_cancel)) {
            model.returnHome(expedition, cancel = true)
        })
    }
    private fun renderJournal(events: List<CompanionJournalEntity>): Unit {
        val card: LinearLayout = journalCard ?: return
        card.removeAllViews()
        if (events.isEmpty() && model.memories.value.isEmpty()) card.addView(HomeUi.text(requireContext(), getString(R.string.journal_empty)))
        events.forEach { event ->
            val title: String = when (event.kind) {
                "adopt" -> getString(R.string.journal_adopt)
                "expedition" -> getString(R.string.journal_expedition, getString(ExpeditionDestination.find(event.detail)?.title ?: R.string.adventures_title))
                "care" -> getString(R.string.journal_care, getString(com.pixelpals.app.feature.care.CareScenePanel.label(
                    com.pixelpals.app.core.care.scene.CareSceneAction.entries.firstOrNull { it.name == event.detail } ?: com.pixelpals.app.core.care.scene.CareSceneAction.PET)))
                else -> return@forEach
            }
            card.addView(HomeUi.text(requireContext(), title, 18f))
            card.addView(HomeUi.text(requireContext(), DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .format(Instant.ofEpochMilli(event.occurredAt).atZone(ZoneId.systemDefault())), 12f))
            if (event.kind == "expedition") ExpeditionDestination.find(event.detail)?.let { destination ->
                card.addView(HomeUi.text(requireContext(), getString(destination.story), 14f))
            }
        }
        model.memories.value.forEach { memory ->
            card.addView(HomeUi.text(requireContext(), memory.title, 18f))
            card.addView(HomeUi.text(requireContext(), memory.subtitle, 14f))
        }
    }
    override fun onResume(): Unit { super.onResume(); model.refresh(); scenes.forEach { it.resume() } }
    override fun onPause(): Unit { scenes.forEach { it.pause() }; super.onPause() }
    override fun onDestroyView(): Unit { scenes.forEach { it.pause() }; scenes.clear(); body = null; travelCard = null; journalCard = null; super.onDestroyView() }
}
