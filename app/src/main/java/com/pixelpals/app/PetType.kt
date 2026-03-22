package com.pixelpals.app

import com.pixelpals.app.R

/**
 * PetType — Catálogo maestro de mascotas virtuales.
 *
 * Cada tipo define: nombre, sprite, estilo visual, y parámetros de física/comportamiento.
 */
enum class PetType(
    val displayName: String,
    val description: String,
    val spriteResId: Int,
    val gravity: Float,
    val terminalVelocity: Float,
    val bounceDamping: Float,
    val movementStyle: MovementStyle,
    val idleStyle: IdleStyle,
    val interactionStyle: InteractionStyle
) {
    BLOOP(
        displayName = "Bloop",
        description = "El Fantasmita Tímido",
        spriteResId = R.drawable.pet_bloop,
        gravity = 0.0f,           // Floats! No gravity
        terminalVelocity = 0f,
        bounceDamping = 0f,
        movementStyle = MovementStyle.DRIFT_SLOW,
        idleStyle = IdleStyle.SINE_FLOAT,
        interactionStyle = InteractionStyle.FADE_SHRINK
    ),

    NUBE_MICHI(
        displayName = "Nube-Michi",
        description = "El Gatito de Nube",
        spriteResId = R.drawable.pet_nube_michi,
        gravity = 0.3f,           // Falls like a feather
        terminalVelocity = 5f,
        bounceDamping = 0.1f,
        movementStyle = MovementStyle.STATIC_PERCH,
        idleStyle = IdleStyle.BREATHING,
        interactionStyle = InteractionStyle.FEATHER_FALL
    ),

    JELLY(
        displayName = "Jelly",
        description = "El Slime de Gelatina",
        spriteResId = R.drawable.pet_jelly,
        gravity = 2.5f,           // Normal gravity
        terminalVelocity = 30f,
        bounceDamping = 0.6f,     // Very bouncy!
        movementStyle = MovementStyle.PARABOLIC_JUMP,
        idleStyle = IdleStyle.JELLY_WOBBLE,
        interactionStyle = InteractionStyle.SQUISH_BOUNCE
    ),

    CORGI(
        displayName = "Corgi",
        description = "El Perrito Explorador",
        spriteResId = R.drawable.pet_corgi,
        gravity = 1.8f,           // Normal gravity
        terminalVelocity = 25f,
        bounceDamping = 0.3f,
        movementStyle = MovementStyle.WALK_RUN,
        idleStyle = IdleStyle.SIT_BARK,
        interactionStyle = InteractionStyle.BELLY_RUB
    );
}

/** How the pet moves autonomously */
enum class MovementStyle {
    DRIFT_SLOW,       // Bloop: slow erratic drift
    STATIC_PERCH,     // Nube-Michi: sits on edges, rarely moves
    PARABOLIC_JUMP,   // Jelly: constant bouncing jumps along bottom
    WALK_RUN          // Corgi: walks left/right, climbs edges
}

/** How the pet animates when idle */
enum class IdleStyle {
    SINE_FLOAT,       // Bloop: smooth sine wave floating
    BREATHING,        // Nube-Michi: expand/contract breathing
    JELLY_WOBBLE,     // Jelly: wobbly jelly physics
    SIT_BARK          // Corgi: sits, occasionally shows bark bubble
}

/** How the pet responds to touch interaction */
enum class InteractionStyle {
    FADE_SHRINK,      // Bloop: becomes semi-transparent and shrinks
    FEATHER_FALL,     // Nube-Michi: falls very slowly like a feather
    SQUISH_BOUNCE,    // Jelly: squash animation then elastic bounce
    BELLY_RUB         // Corgi: rolls onto back
}
