package com.pixelpals.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.prefs.SelectedPetStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectedPetStoreTest {
    private lateinit var context: Context
    private lateinit var store: SelectedPetStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("pixelpals_selection", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = SelectedPetStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("pixelpals_selection", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun petEnabledStatePersistsIndependentlyFromSelectedPet() {
        store.save(PetType.CORGI)
        assertTrue(store.isPetEnabled())

        store.setPetEnabled(false)

        assertFalse(store.isPetEnabled())
        assertTrue(store.load() == PetType.CORGI)
    }

    @Test
    fun newSelectionStoreStartsDisabled() {
        assertFalse(store.isPetEnabled())
    }
}
