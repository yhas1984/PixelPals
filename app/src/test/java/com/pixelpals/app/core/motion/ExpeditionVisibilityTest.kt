package com.pixelpals.app.core.motion

import org.junit.Assert.*
import org.junit.Test

class ExpeditionVisibilityTest {
    @Test fun processRestartWaitsForTravelStateBeforeShowingAnyPet() {
        val visibility = ExpeditionVisibility()
        assertTrue(visibility.hides("corgi"))
        visibility.update("corgi")
        assertTrue(visibility.hides("corgi"))
        assertFalse(visibility.hides("yuki"))
    }

    @Test fun selectionChangesDoNotRequireAnotherDatabaseEmission() {
        val visibility = ExpeditionVisibility()
        visibility.update("yuki")
        assertTrue(visibility.hides("yuki"))
        assertFalse(visibility.hides("corgi"))
        assertTrue(visibility.hides("yuki"))
        visibility.update(null)
        assertFalse(visibility.hides("yuki"))
        assertFalse(visibility.hides("corgi"))
    }
}
