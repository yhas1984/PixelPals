package com.pixelpals.app.feature.home

import com.pixelpals.app.database.HomeDecorationEntity
import org.junit.Assert.*
import org.junit.Test

class CareDecorationSelectionTest {
    @Test fun careUsesCompatiblePlacedToysAndPreservesSpeciesActions() {
        val pinwheel = HomeDecorationEntity("corgi", "pinwheel", 0, 0)
        val yarn = HomeDecorationEntity("corgi", "yarn", 1, 0)
        val ball = HomeDecorationEntity("corgi", "ball", 2, 0)
        for (pet in listOf(com.pixelpals.app.core.domain.PetType.CORGI, com.pixelpals.app.core.domain.PetType.GINGER)) {
            assertEquals("yarn", CareDecorationSelection.careToy(pet, listOf(pinwheel, yarn), "pinwheel"))
            assertEquals("yarn", CareDecorationSelection.careToy(pet, listOf(ball, yarn), "yarn"))
            assertNull(CareDecorationSelection.careToy(pet, listOf(pinwheel), "yarn"))
        }
        assertEquals("ball", CareDecorationSelection.careToy(com.pixelpals.app.core.domain.PetType.YUKI, listOf(yarn, ball), "yarn"))
        assertNull(CareDecorationSelection.careToy(com.pixelpals.app.core.domain.PetType.DIABLILLO, listOf(yarn), "yarn"))
    }

    @Test fun toyUsesPlacedFavoriteAndIgnoresStoredOrNonToyFavorites() {
        val ball = HomeDecorationEntity("corgi", "ball", 0, 0)
        val yarn = HomeDecorationEntity("corgi", "yarn", 1, 0)
        val bed = HomeDecorationEntity("corgi", "moss_bed", 2, 0)
        assertEquals("yarn", CareDecorationSelection.toy(listOf(ball, yarn, bed), "yarn"))
        assertEquals("ball", CareDecorationSelection.toy(listOf(ball, bed), "yarn"))
        assertEquals("ball", CareDecorationSelection.toy(listOf(ball, yarn, bed), "moss_bed"))
        assertNull(CareDecorationSelection.toy(listOf(bed), "yarn"))
    }

    @Test fun matchesFirstPlacedBedAndFallsBackWhenStored() {
        val toy = HomeDecorationEntity("corgi", "yarn", 0, 0)
        val bed = HomeDecorationEntity("corgi", "moss_bed", 1, 0)
        val other = HomeDecorationEntity("corgi", "moon_bed", 2, 0)
        assertNull(CareDecorationSelection.bed(emptyList()))
        assertNull(CareDecorationSelection.bed(listOf(toy)))
        assertEquals("moss_bed", CareDecorationSelection.bed(listOf(toy, bed, other)))
        assertEquals("moon_bed", CareDecorationSelection.bed(listOf(toy, other)))
    }
}
