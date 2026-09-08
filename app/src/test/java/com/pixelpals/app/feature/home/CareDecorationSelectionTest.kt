package com.pixelpals.app.feature.home

import com.pixelpals.app.database.HomeDecorationEntity
import org.junit.Assert.*
import org.junit.Test

class CareDecorationSelectionTest {
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
