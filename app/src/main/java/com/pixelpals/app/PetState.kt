package com.pixelpals.app

/**
 * PetState — Estados posibles de la mascota.
 */
enum class PetState {
    IDLE,               // Breathing/wobble/float + blink
    DRAGGING,           // Held by user — "pataleo" animation
    FALLING,            // Dropped — gravity pulling down
    LANDING,            // Hit ground — squash sequence
    WALKING,            // Autonomous movement
    JUMPING,            // In air from autonomous jump
    SECRET_IDLE,        // Doing a rare hidden activity
    SYSTEM_REACTION,    // Reacting to battery/charging/etc
    INTERACTING         // Triggered by tap (e.g. Belly rub)
}
