package com.pixelpals.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pixelpals.app.PetService
import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.PetStatusEntity
import com.pixelpals.app.notifications.PetCareNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CareStateDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_STATE) return
        val requested: PetCondition = intent.getStringExtra(EXTRA_CONDITION)
            ?.uppercase()
            ?.let { value -> PetCondition.entries.firstOrNull { it.name == value } }
            ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                setCondition(context, requested)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun setCondition(context: Context, condition: PetCondition) {
        val petType = SelectedPetStore(context).load()
        val petId: String = petType.name.lowercase()
        val dao = AppDatabase.getDatabase(context).petStatusDao()
        val current: PetStatusEntity = dao.getByPetId(petId) ?: PetStatusEntity(petId)
        val now: Long = System.currentTimeMillis()
        val updated: PetStatusEntity = when (condition) {
            PetCondition.HEALTHY -> current.copy(
                energy = 85,
                satiety = 85,
                hygiene = 85,
                condition = condition.name,
                conditionStartedAt = now,
                criticalNeedsStartedAt = 0L,
                recoveryProgress = 0,
                lastUpdatedAt = now,
                lastInteractionAt = now,
            )
            PetCondition.AT_RISK -> current.copy(
                energy = 12,
                satiety = 12,
                condition = condition.name,
                conditionStartedAt = now,
                criticalNeedsStartedAt = now - 13L * 60L * 60L * 1_000L,
                recoveryProgress = 0,
                lastUpdatedAt = now,
                lastInteractionAt = now - 13L * 60L * 60L * 1_000L,
            )
            PetCondition.SICK -> current.copy(
                energy = 15,
                satiety = 15,
                condition = condition.name,
                conditionStartedAt = now,
                criticalNeedsStartedAt = now - 25L * 60L * 60L * 1_000L,
                recoveryProgress = 0,
                lastUpdatedAt = now,
                lastInteractionAt = now - 25L * 60L * 60L * 1_000L,
                lastMedicineAt = 0L,
            )
            PetCondition.RECOVERING -> current.copy(
                energy = 50,
                satiety = 50,
                hygiene = 50,
                condition = condition.name,
                conditionStartedAt = now,
                criticalNeedsStartedAt = 0L,
                recoveryProgress = 70,
                lastUpdatedAt = now,
            )
            PetCondition.HIBERNATING -> current.copy(
                energy = 25,
                satiety = 25,
                hygiene = 25,
                condition = condition.name,
                conditionStartedAt = now,
                criticalNeedsStartedAt = 0L,
                recoveryProgress = 0,
                lastUpdatedAt = now,
            )
        }
        dao.upsert(updated)
        PetService.requestPetRefresh(context, conditionEmoji(condition), celebrate = condition == PetCondition.HEALTHY)
        PetCareNotificationScheduler.schedule(context, 0L)
    }

    private fun conditionEmoji(condition: PetCondition): String = when (condition) {
        PetCondition.HEALTHY -> "💛✨"
        PetCondition.AT_RISK -> "🥺"
        PetCondition.SICK -> "🤒"
        PetCondition.RECOVERING -> "💛"
        PetCondition.HIBERNATING -> "💤"
    }

    companion object {
        const val ACTION_SET_STATE: String = "com.pixelpals.app.debug.SET_CARE_STATE"
        const val EXTRA_CONDITION: String = "condition"
    }
}
