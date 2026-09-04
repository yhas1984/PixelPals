package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetStatusSnapshot

enum class CareSceneAction(val careAction: CareAction?) {
    FEED(CareAction.FEED), PLAY(CareAction.PLAY), PET(null),
    CLEAN(CareAction.CLEAN), REST(CareAction.REST), MEDICINE(CareAction.MEDICINE);
}

enum class CareSceneOrigin { ROOM, OVERLAY }
enum class CareSceneMode { AUTOMATIC, MANUAL }
enum class CareScenePhase { READY, COMMITTING, FINISHED }

data class CareSceneRequest(
    val id: String,
    val owner: String,
    val pet: PetType,
    val action: CareSceneAction,
    val origin: CareSceneOrigin,
    val mode: CareSceneMode,
)

sealed interface CareSceneResult {
    data class Completed(val before: PetStatusSnapshot, val after: PetStatusSnapshot) : CareSceneResult {
        val bondGain: Int get() = (after.bond - before.bond).coerceAtLeast(0)
        val coinGain: Int get() = (after.softCurrency - before.softCurrency).coerceAtLeast(0)
        val didWake: Boolean get() = before.condition == PetCondition.HIBERNATING &&
            after.condition != PetCondition.HIBERNATING
    }
    data object Cancelled : CareSceneResult
    data object Unavailable : CareSceneResult
    data object Error : CareSceneResult
}

data class CareSceneSession(
    val request: CareSceneRequest,
    val snapshot: PetStatusSnapshot,
    val phase: CareScenePhase = CareScenePhase.READY,
    val result: CareSceneResult? = null,
    val isDetached: Boolean = false,
)

fun isMedicineAvailable(snapshot: PetStatusSnapshot, now: Long): Boolean =
    snapshot.condition in setOf(PetCondition.SICK, PetCondition.RECOVERING) &&
        snapshot.medicineAvailableAt <= now

/** Unknown health must never expose a dose; the coordinator rechecks before playback. */
fun getAvailableDesktopCareActions(snapshot: PetStatusSnapshot?, now: Long): List<CareSceneAction> =
    CareSceneAction.entries.filter { action ->
        action != CareSceneAction.MEDICINE || (snapshot != null && isMedicineAvailable(snapshot, now))
    }
