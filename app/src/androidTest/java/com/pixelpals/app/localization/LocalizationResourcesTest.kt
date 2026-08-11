package com.pixelpals.app.localization

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.CoinProduct
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun englishAndSpanishExposeLocalizedPetAndStoreCopy() {
        val english = contextFor("en")
        val spanish = contextFor("es")

        assertEquals("Language", english.getString(R.string.language_title))
        assertEquals("Idioma", spanish.getString(R.string.language_title))
        assertEquals("Cherub", english.getString(PetType.ANGEL.displayNameResId))
        assertEquals("Querubín", spanish.getString(PetType.ANGEL.displayNameResId))
        assertNotEquals(
            english.getString(CoinProduct.CATALOG.first().subtitleResId),
            spanish.getString(CoinProduct.CATALOG.first().subtitleResId),
        )
    }

    private fun contextFor(language: String): Context {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return context.createConfigurationContext(configuration)
    }
}
