package com.pixelpals.app.feature.home

import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.database.CompanionDao
import com.pixelpals.app.database.CompanionHomeEntity
import com.pixelpals.app.database.CompanionJournalEntity

/** Called inside the same Room transaction that applies the care reward. */
object CompanionLearning {
    suspend fun record(dao: CompanionDao, petId: String, action: CareSceneAction, now: Long): Unit {
        val home: CompanionHomeEntity = dao.getHome(petId) ?: return
        if (now - home.lastLearnedAt < 30_000L) return
        dao.saveHome(home.copy(
            feedCount = home.feedCount + if (action == CareSceneAction.FEED) 1 else 0,
            playCount = home.playCount + if (action == CareSceneAction.PLAY) 1 else 0,
            touchCount = home.touchCount + if (action == CareSceneAction.PET) 1 else 0,
            lastLearnedAt = now,
        ))
        val day: Long = now / 86_400_000L
        dao.remember(CompanionJournalEntity("care:$petId:$action:$day", petId, "care", action.name, now))
    }
}
