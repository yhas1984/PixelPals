package com.pixelpals.app.feature.home

import android.content.Context
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType

/** Starter care objects adapt to the selected species; purchased variants keep their names. */
object DecorationPresentation {
    fun title(context: Context, item: Decoration, pet: PetType): String = when {
        pet == PetType.CORGI -> context.getString(item.title)
        item.kind == DecorationKind.BOWL -> context.getString(when (item.id) {
            "wood_bowl" -> R.string.decor_species_food_wood
            "flower_bowl" -> R.string.decor_species_food_flower
            "moon_bowl" -> R.string.decor_species_food_moon
            else -> R.string.decor_species_food
        }, context.getString(pet.displayNameResId))
        item.id == "ball" -> context.getString(R.string.decor_species_toy, context.getString(pet.displayNameResId))
        item.id == "linen_bed" -> context.getString(R.string.decor_species_rest, context.getString(pet.displayNameResId))
        else -> context.getString(item.title)
    }
}
