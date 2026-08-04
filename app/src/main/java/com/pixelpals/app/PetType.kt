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
    val jumpInterval: Long = 0L,
    val agility: Float = 1.0f,        // How quickly they react and move (1.0 = normal)
    val boredomRate: Float = 1.0f,    // How fast they get bored (1.0 = normal)
    val exploreInterval: Long = 5000L, // Time between autonomous explorations (ms)
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
        agility = 0.6f,           // Slow, dreamy reactions
        boredomRate = 0.3f,       // Content floating for a long time
        movementStyle = MovementStyle.DRIFT_SLOW,
        idleStyle = IdleStyle.SINE_FLOAT,
        interactionStyle = InteractionStyle.FADE_SHRINK
    ),

    NUBE_MICHI(
        displayName = "Nube-Michi",
        description = "El Gatito de Nube",
        spriteResId = R.drawable.pet_nube_michi,
        gravity = 0.2f,           // Falls like a feather
        terminalVelocity = 4f,
        bounceDamping = 0.1f,
        agility = 0.8f,           // Gentle, slow reactions
        boredomRate = 0.6f,       // Occasionally wants attention
        movementStyle = MovementStyle.STATIC_PERCH,
        idleStyle = IdleStyle.BREATHING,
        interactionStyle = InteractionStyle.FEATHER_FALL
    ),

    JELLY(
        displayName = "Jelly",
        description = "El Slime de Gelatina",
        spriteResId = R.drawable.pet_jelly,
        gravity = 2.2f,           // High dynamic gravity for quick snappy falls
        terminalVelocity = 30f,
        bounceDamping = 0.5f,     // Half elastic recovery
        jumpInterval = 4000L,     // Base charge interval
        agility = 1.2f,           // Bouncy, quick reactions
        boredomRate = 1.3f,       // Always wants to bounce
        movementStyle = MovementStyle.PARABOLIC_JUMP,
        idleStyle = IdleStyle.JELLY_WOBBLE,
        interactionStyle = InteractionStyle.SQUISH_BOUNCE
    ),

    CORGI(
        displayName = "Corgi",
        description = "El Perrito Explorador",
        spriteResId = R.drawable.pet_corgi,
        gravity = 1.2f,           // heavy
        terminalVelocity = 15f,
        bounceDamping = 0.3f,     // dry bounce
        agility = 1.5f,           // Very active and eager
        boredomRate = 1.5f,       // Needs constant attention
        movementStyle = MovementStyle.WALK_RUN,
        idleStyle = IdleStyle.SIT_BARK,
        interactionStyle = InteractionStyle.BELLY_RUB
    ),

    GINGER(
        displayName = "Ginger",
        description = "La Gata Elegante",
        spriteResId = R.drawable.pet_ginger,
        gravity = 1.4f,           // Heavy like a real cat
        terminalVelocity = 18f,
        bounceDamping = 0.0f,     // Cats always land on their feet - no bounce
        agility = 1.8f,           // Quick, graceful reactions
        boredomRate = 0.5f,       // Content grooming, but unpredictable
        movementStyle = MovementStyle.ELEGANT_STRETCH,
        idleStyle = IdleStyle.GROOMING,
        interactionStyle = InteractionStyle.BELLY_RUB
    ),

    ANGEL(
        displayName = "Querubin",
        description = "El Querubin Luminoso",
        spriteResId = R.drawable.pet_angel,
        gravity = 0.1f,
        terminalVelocity = 3f,
        bounceDamping = 0.0f,
        agility = 0.95f,
        boredomRate = 0.45f,
        movementStyle = MovementStyle.DRIFT_SLOW,
        idleStyle = IdleStyle.SINE_FLOAT,
        interactionStyle = InteractionStyle.FEATHER_FALL
    ),

    PATITO(
        displayName = "Patito",
        description = "El Patito Curioso",
        spriteResId = R.drawable.pet_patito,
        gravity = 1.2f,           // Rubber duck physics
        terminalVelocity = 16f,
        bounceDamping = 0.6f,     // Elastic bouncy landing
        agility = 1.0f,           // Normal reaction speed
        boredomRate = 1.2f,       // Gets curious quickly
        exploreInterval = 4000L,  // Explores every 4 seconds
        movementStyle = MovementStyle.WADDLE_EXPLORE,
        idleStyle = IdleStyle.DUCK_IDLE,
        interactionStyle = InteractionStyle.QUACK_REACTION
    ),

    DIABLILLO(
        displayName = "Diablillo",
        description = "El Diablillo Travieso",
        spriteResId = R.drawable.pet_diablillo,
        gravity = 0.8f,           // Light gravity - can "fly" a bit
        terminalVelocity = 12f,
        bounceDamping = 0.7f,     // Bouncy
        agility = 2.5f,           // FASTEST - snappy reactions
        boredomRate = 2.0f,       // Never stays still
        exploreInterval = 2000L,  // Decides every 2 seconds
        movementStyle = MovementStyle.CHAOTIC_ZOOM,
        idleStyle = IdleStyle.LURK_IDLE,
        interactionStyle = InteractionStyle.CHAOTIC_JUMP
    ),

    MOKI(
        displayName = "Moki",
        description = "El Camaleón Adhesivo",
        spriteResId = R.drawable.pet_moki,
        gravity = 0.6f,
        terminalVelocity = 12f,
        bounceDamping = 0f,
        agility = 1.1f,
        boredomRate = 0.9f,
        exploreInterval = 3200L,
        movementStyle = MovementStyle.STATIC_PERCH,
        idleStyle = IdleStyle.BREATHING,
        interactionStyle = InteractionStyle.FEATHER_FALL
    );
}

/** How the pet moves autonomously */
enum class MovementStyle {
    DRIFT_SLOW,       // Bloop: slow erratic drift
    STATIC_PERCH,     // Nube-Michi: sits on edges, rarely moves
    PARABOLIC_JUMP,   // Jelly: constant bouncing jumps along bottom
    WALK_RUN,         // Corgi: walks left/right, climbs edges
    ELEGANT_STRETCH,  // Ginger: graceful stretches and elegant movements
    WADDLE_EXPLORE,   // Patito: curious waddle exploration
    CHAOTIC_ZOOM      // Diablillo: unpredictable teleports and sprints
}

/** How the pet animates when idle */
enum class IdleStyle {
    SINE_FLOAT,       // Bloop: smooth sine wave floating
    BREATHING,        // Nube-Michi: expand/contract breathing
    JELLY_WOBBLE,     // Jelly: wobbly jelly physics
    SIT_BARK,         // Corgi: sits, occasionally shows bark bubble
    GROOMING,         // Ginger: grooming sequence - clean face, lick paw, wink
    DUCK_IDLE,        // Patito: peek and look around
    LURK_IDLE         // Diablillo: lurking and watching
}

/** How the pet responds to touch interaction */
enum class InteractionStyle {
    FADE_SHRINK,      // Bloop: becomes semi-transparent and shrinks
    FEATHER_FALL,     // Nube-Michi: falls very slowly like a feather
    SQUISH_BOUNCE,    // Jelly: squash animation then elastic bounce
    BELLY_RUB,        // Corgi/Ginger: rolls onto back
    QUACK_REACTION,   // Patito: quack supremo with jump
    CHAOTIC_JUMP      // Diablillo: fire jump with staccato haptic
}
