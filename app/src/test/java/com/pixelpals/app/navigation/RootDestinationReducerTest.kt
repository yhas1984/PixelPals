package com.pixelpals.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootDestinationReducerTest {
    @Test
    fun savedDestinationWinsOverIntentAndInvalidValuesFallBackHome() {
        assertEquals(
            PixelPalsDestination.PETS,
            RootDestinationReducer.restore("PETS", "STORE"),
        )
        assertEquals(
            PixelPalsDestination.STORE,
            RootDestinationReducer.restore(null, "STORE"),
        )
        assertEquals(
            PixelPalsDestination.HOME,
            RootDestinationReducer.restore("unknown", "invalid"),
        )
    }

    @Test
    fun backReturnsHomeOnlyFromSecondaryDestinations() {
        assertEquals(
            PixelPalsDestination.HOME,
            RootDestinationReducer.backTarget(PixelPalsDestination.PETS),
        )
        assertEquals(
            PixelPalsDestination.HOME,
            RootDestinationReducer.backTarget(PixelPalsDestination.STORE),
        )
        assertNull(RootDestinationReducer.backTarget(PixelPalsDestination.HOME))
    }
}
