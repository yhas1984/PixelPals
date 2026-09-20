package com.pixelpals.app.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelpals.app.PetService
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.database.*
import com.pixelpals.app.status.MemoryMoment
import com.pixelpals.app.status.PetStatusSnapshot
import com.pixelpals.app.core.review.ReviewMoment
import com.pixelpals.app.core.review.ReviewPromptInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CompanionWorld(
    val home: CompanionHomeEntity? = null,
    val placements: List<HomeDecorationEntity> = emptyList(),
    val inventory: List<DecorationInventoryEntity> = emptyList(),
    val expedition: CompanionExpeditionEntity? = null,
)

class CompanionViewModel(application: Application) : AndroidViewModel(application) {
    val repository: CompanionRepository = AppServices.companions(application)
    private val economy = AppServices.repository(application)
    private val selection = SelectedPetStore(application)
    private val selected: MutableStateFlow<PetType> = MutableStateFlow(selection.load())
    val pet: StateFlow<PetType> = selected.asStateFlow()
    private val mutableStatus: MutableStateFlow<PetStatusSnapshot?> = MutableStateFlow(null)
    val status: StateFlow<PetStatusSnapshot?> = mutableStatus.asStateFlow()
    private val mutableMemories: MutableStateFlow<List<MemoryMoment>> = MutableStateFlow(emptyList())
    val memories: StateFlow<List<MemoryMoment>> = mutableMemories.asStateFlow()
    val error: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val busy: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val mutableReviewMoments = MutableSharedFlow<ReviewPromptInput>(extraBufferCapacity = 1)
    val reviewMoments: SharedFlow<ReviewPromptInput> = mutableReviewMoments.asSharedFlow()

    private val observationRevision = MutableStateFlow(0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val world: StateFlow<CompanionWorld> = restartableObservation(combine(selected, observationRevision) { pet, _ -> pet }, { pet ->
        combine(repository.dao.observeHome(pet.name.lowercase()), repository.dao.observePlacements(pet.name.lowercase()),
            repository.dao.observeInventory(), repository.dao.observeExpedition()) { home, placements, inventory, expedition ->
            CompanionWorld(home, placements, inventory, expedition)
        }
    }, { error.value = true }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompanionWorld())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val journal: StateFlow<List<CompanionJournalEntity>> = restartableObservation(combine(selected, observationRevision) { pet, _ -> pet }, {
        repository.dao.observeJournal(it.name.lowercase())
    }, { error.value = true }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh() }

    fun refresh(): Unit {
        selected.value = selection.load()
        observationRevision.value += 1
        viewModelScope.launch {
            try {
                val current: PetType = pet.value
                repository.ensureHome(current)
                repository.refreshExpedition()
                val snapshot: PetStatusSnapshot = economy.getStatusSnapshot(current)
                if (pet.value == current) {
                    mutableStatus.value = snapshot
                    mutableMemories.value = economy.getMemories(current)
                }
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) { error.value = true }
        }
    }

    fun perform(action: suspend () -> Unit): Unit {
        if (busy.value) return
        busy.value = true
        error.value = false
        viewModelScope.launch {
            try { action(); refresh()
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) { error.value = true
            } finally { busy.value = false }
        }
    }

    fun depart(destination: ExpeditionDestination): Unit = perform {
        check(AppServices.careScenes(getApplication()).session.value == null)
        check(repository.startExpedition(pet.value, destination))
        PetService.stopPet(getApplication())
    }
    fun returnHome(expedition: CompanionExpeditionEntity, cancel: Boolean = false): Unit = perform {
        check(repository.finishExpedition(expedition.requestId, cancel))
        if (!cancel) {
            val returnedPet: PetType = PetType.entries.first { it.name.lowercase() == expedition.petId }
            val home: CompanionHomeEntity = repository.dao.getHome(expedition.petId) ?: repository.ensureHome(returnedPet)
            val snapshot: PetStatusSnapshot = economy.getStatusSnapshot(returnedPet)
            mutableReviewMoments.emit(
                ReviewPromptInput(
                    moment = ReviewMoment.EXPEDITION_REWARD,
                    adoptedAt = home.adoptedAt,
                    bond = snapshot.bond,
                    careStreakDays = snapshot.careStreakDays,
                    eventAt = System.currentTimeMillis(),
                ),
            )
        }
        if (expedition.resumeDesktop && selection.load().name.lowercase() == expedition.petId &&
            android.provider.Settings.canDrawOverlays(getApplication())) PetService.requestPetChange(getApplication(), selection.load())
    }
}
