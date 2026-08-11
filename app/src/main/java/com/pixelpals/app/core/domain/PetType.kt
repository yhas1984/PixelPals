package com.pixelpals.app.core.domain

import androidx.annotation.StringRes
import com.pixelpals.app.R

/**
 * PetType — Catálogo maestro de mascotas virtuales.
 *
 * Cada tipo define: nombre, sprite, estilo visual, y parámetros de física/comportamiento.
 */
enum class PetType(
    @param:StringRes val displayNameResId: Int,
    @param:StringRes val descriptionResId: Int,
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
        displayNameResId = R.string.pet_name_bloop,
        descriptionResId = R.string.bloop_desc,
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
        displayNameResId = R.string.pet_name_nube_michi,
        descriptionResId = R.string.nube_michi_desc,
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
        displayNameResId = R.string.pet_name_jelly,
        descriptionResId = R.string.jelly_desc,
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
        displayNameResId = R.string.pet_name_corgi,
        descriptionResId = R.string.corgi_desc,
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
        displayNameResId = R.string.pet_name_ginger,
        descriptionResId = R.string.ginger_desc,
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
        displayNameResId = R.string.pet_name_angel,
        descriptionResId = R.string.angel_desc,
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
        displayNameResId = R.string.pet_name_patito,
        descriptionResId = R.string.patito_desc,
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
        displayNameResId = R.string.pet_name_diablillo,
        descriptionResId = R.string.diablillo_desc,
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
        displayNameResId = R.string.pet_name_moki,
        descriptionResId = R.string.moki_desc,
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
    ),

    YUKI(
        displayNameResId = R.string.pet_name_yuki,
        descriptionResId = R.string.yuki_desc,
        spriteResId = R.drawable.pet_yuki,
        gravity = 1.4f,           // Pesado pero resbaladizo
        terminalVelocity = 14f,
        bounceDamping = 0.25f,    // Aterrizajes blandos
        agility = 0.8f,           // Pausado, se derrite con el calor
        boredomRate = 0.7f,
        exploreInterval = 4200L,
        movementStyle = MovementStyle.WALK_RUN,
        idleStyle = IdleStyle.BREATHING,
        interactionStyle = InteractionStyle.SQUISH_BOUNCE
    ),

    PIRU(
        displayNameResId = R.string.pet_name_piru,
        descriptionResId = R.string.piru_desc,
        spriteResId = R.drawable.pet_piru,
        gravity = 1.1f,
        terminalVelocity = 15f,
        bounceDamping = 0.4f,     // Rebote gomoso
        agility = 1.3f,           // Alegre y vivaz
        boredomRate = 1.1f,
        exploreInterval = 3000L,
        movementStyle = MovementStyle.WADDLE_EXPLORE,
        idleStyle = IdleStyle.DUCK_IDLE,
        interactionStyle = InteractionStyle.SQUISH_BOUNCE
    ),

    TARO(
        displayNameResId = R.string.pet_name_taro,
        descriptionResId = R.string.taro_desc,
        spriteResId = R.drawable.pet_taro,
        gravity = 1.6f,           // Muy pesada y lenta
        terminalVelocity = 12f,
        bounceDamping = 0.0f,     // No rebota
        agility = 0.35f,          // La más lenta: paciencia
        boredomRate = 0.25f,      // Contenta estando quieta
        exploreInterval = 7000L,  // Explora muy de vez en cuando
        movementStyle = MovementStyle.ELEGANT_STRETCH,
        idleStyle = IdleStyle.BREATHING,
        interactionStyle = InteractionStyle.BELLY_RUB
    ),

    MENTA(
        displayNameResId = R.string.pet_name_menta,
        descriptionResId = R.string.menta_desc,
        spriteResId = R.drawable.pet_menta,
        gravity = 0.9f,
        terminalVelocity = 13f,
        bounceDamping = 0.35f,
        agility = 1.0f,           // Se desliza con gracia
        boredomRate = 0.8f,
        exploreInterval = 3600L,
        movementStyle = MovementStyle.DRIFT_SLOW,
        idleStyle = IdleStyle.SINE_FLOAT,
        interactionStyle = InteractionStyle.FEATHER_FALL
    ),

    TELA(
        displayNameResId = R.string.pet_name_tela,
        descriptionResId = R.string.tela_desc,
        spriteResId = R.drawable.pet_tela,
        gravity = 0.2f,           // Casi sin gravedad: trepa y cuelga
        terminalVelocity = 6f,
        bounceDamping = 0.1f,
        agility = 1.6f,           // Ágil: trepa paredes y recorre el techo
        boredomRate = 1.0f,
        exploreInterval = 2200L,  // Siempre explorando
        movementStyle = MovementStyle.CHAOTIC_ZOOM,
        idleStyle = IdleStyle.LURK_IDLE,
        interactionStyle = InteractionStyle.SQUISH_BOUNCE
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
