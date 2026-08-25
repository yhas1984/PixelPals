package com.pixelpals.app.feature.treasure

import androidx.annotation.StringRes
import com.pixelpals.app.R
import com.pixelpals.app.status.PetPersonality

data class TreasureDefinition(
    val id: String,
    val emoji: String,
    @param:StringRes val nameResourceId: Int,
    @param:StringRes val storyResourceId: Int,
    @param:StringRes val hintResourceId: Int,
)

object TreasureCatalog {
    val all: List<TreasureDefinition> = listOf(
        TreasureDefinition("travelling_coin", "🪙", R.string.treasure_name_travelling_coin, R.string.treasure_story_travelling_coin, R.string.treasure_hint_travelling_coin),
        TreasureDefinition("walking_flower", "🌸", R.string.treasure_name_walking_flower, R.string.treasure_story_walking_flower, R.string.treasure_hint_walking_flower),
        TreasureDefinition("lucky_bone", "🦴", R.string.treasure_name_lucky_bone, R.string.treasure_story_lucky_bone, R.string.treasure_hint_lucky_bone),
        TreasureDefinition("pocket_star", "⭐", R.string.treasure_name_pocket_star, R.string.treasure_story_pocket_star, R.string.treasure_hint_pocket_star),
        TreasureDefinition("bright_gem", "💎", R.string.treasure_name_bright_gem, R.string.treasure_story_bright_gem, R.string.treasure_hint_bright_gem),
        TreasureDefinition("companion_clover", "🍀", R.string.treasure_name_companion_clover, R.string.treasure_story_companion_clover, R.string.treasure_hint_companion_clover),
        TreasureDefinition("whispering_shell", "🐚", R.string.treasure_name_whispering_shell, R.string.treasure_story_whispering_shell, R.string.treasure_hint_whispering_shell),
        TreasureDefinition("caring_bow", "🎀", R.string.treasure_name_caring_bow, R.string.treasure_story_caring_bow, R.string.treasure_hint_caring_bow),
        TreasureDefinition("curious_mushroom", "🍄", R.string.treasure_name_curious_mushroom, R.string.treasure_story_curious_mushroom, R.string.treasure_hint_curious_mushroom),
        TreasureDefinition("mysterious_key", "🔑", R.string.treasure_name_mysterious_key, R.string.treasure_story_mysterious_key, R.string.treasure_hint_mysterious_key),
        TreasureDefinition("missing_piece", "🧩", R.string.treasure_name_missing_piece, R.string.treasure_story_missing_piece, R.string.treasure_hint_missing_piece),
        TreasureDefinition("playful_note", "🎵", R.string.treasure_name_playful_note, R.string.treasure_story_playful_note, R.string.treasure_hint_playful_note),
        TreasureDefinition("light_feather", "🪶", R.string.treasure_name_light_feather, R.string.treasure_story_light_feather, R.string.treasure_hint_light_feather),
        TreasureDefinition("shared_sweet", "🍬", R.string.treasure_name_shared_sweet, R.string.treasure_story_shared_sweet, R.string.treasure_hint_shared_sweet),
        TreasureDefinition("nap_moon", "🌙", R.string.treasure_name_nap_moon, R.string.treasure_story_nap_moon, R.string.treasure_hint_nap_moon),
        TreasureDefinition("friendship_ring", "💍", R.string.treasure_name_friendship_ring, R.string.treasure_story_friendship_ring, R.string.treasure_hint_friendship_ring),
        TreasureDefinition("tiny_crown", "👑", R.string.treasure_name_tiny_crown, R.string.treasure_story_tiny_crown, R.string.treasure_hint_tiny_crown),
        TreasureDefinition("dream_orb", "🔮", R.string.treasure_name_dream_orb, R.string.treasure_story_dream_orb, R.string.treasure_hint_dream_orb),
        TreasureDefinition("secret_slice", "🍕", R.string.treasure_name_secret_slice, R.string.treasure_story_secret_slice, R.string.treasure_hint_secret_slice),
    )

    fun getById(id: String): TreasureDefinition? = all.firstOrNull { treasure -> treasure.id == id }

    fun getByEmoji(emoji: String): TreasureDefinition? = all.firstOrNull { treasure -> treasure.emoji == emoji }

    fun getFavorites(personality: PetPersonality): Set<String> = when (personality) {
        PetPersonality.DREAMY -> setOf("🌙", "⭐", "🔮")
        PetPersonality.SWEET -> setOf("🎀", "🍬", "🌸")
        PetPersonality.BOUNCY -> setOf("🧩", "🎵", "🍕")
        PetPersonality.LOYAL -> setOf("🦴", "🍀", "🔑")
        PetPersonality.ELEGANT -> setOf("💎", "💍", "👑")
        PetPersonality.ANGELIC -> setOf("⭐", "🪶", "🌸")
        PetPersonality.CURIOUS -> setOf("🐚", "🍄", "🪙")
        PetPersonality.CHAOTIC -> setOf("🔑", "🍕", "🔮")
    }
}
