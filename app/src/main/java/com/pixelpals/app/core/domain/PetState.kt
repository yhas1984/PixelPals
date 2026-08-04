package com.pixelpals.app.core.domain

/**
 * PetState — Estados reales del ciclo de vida de la mascota.
 *
 * (Versión limpia tras Fase 1.7: WALKING, LANDING, SECRET_IDLE,
 *  SYSTEM_REACTION estaban definidos pero no usados por [PetView.update].)
 */
enum class PetState {
    IDLE,
    DRAGGING,
    FALLING,
    JUMPING,
    INTERACTING,
}
