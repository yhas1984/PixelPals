package com.pixelpals.app.feature.care

import android.graphics.Canvas
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CorgiFetchPlan
import com.pixelpals.app.core.domain.PetType

/** One care scene rendered inside the existing floating-pet window. */
interface DesktopCarePlayback {
    val isActive: Boolean
    val isMovingPet: Boolean

    fun start(action: CareSceneAction, facingLeft: Boolean, fetchPlan: CorgiFetchPlan? = null): Unit
    fun advance(deltaSeconds: Float): Unit
    fun draw(canvas: Canvas, spriteSize: Int): Boolean
    fun cancel(): Unit

    companion object {
        val ACTIONS: Set<CareSceneAction> = CareSceneAction.entries.toSet()
        val SUPPORTED_PETS: Set<PetType> = PetType.entries.toSet()
    }
}
