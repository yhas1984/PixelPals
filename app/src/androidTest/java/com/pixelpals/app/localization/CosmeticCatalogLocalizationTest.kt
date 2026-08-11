package com.pixelpals.app.localization

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.data.catalog.CosmeticCatalog
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CosmeticCatalogLocalizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun sameCosmeticIdsHaveLocalizedEnglishAndSpanishCopy() {
        val english = CosmeticCatalog.all(contextFor("en"))
        val spanish = CosmeticCatalog.all(contextFor("es"))
        val englishItem = english.first { it.id == "tint_golden" }
        val spanishItem = spanish.first { it.id == "tint_golden" }

        assertEquals(english.map { it.id }, spanish.map { it.id })
        assertNotEquals(englishItem.displayName, spanishItem.displayName)
        assertNotEquals(englishItem.description, spanishItem.description)
        assertEquals(englishItem.coinPrice, spanishItem.coinPrice)
    }

    private fun contextFor(language: String): Context {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return context.createConfigurationContext(configuration)
    }
}
