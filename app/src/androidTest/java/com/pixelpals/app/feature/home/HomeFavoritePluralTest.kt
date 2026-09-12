package com.pixelpals.app.feature.home

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeFavoritePluralTest {
    private val baseContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun englishUsesSingularOnlyForOne() {
        val context = localizedContext("en")

        assertEquals("Favourite: Ball · 0 shared games", context.favoriteText(0))
        assertEquals("Favourite: Ball · 1 shared game", context.favoriteText(1))
        assertEquals("Favourite: Ball · 2 shared games", context.favoriteText(2))
    }

    @Test
    fun spanishUsesSingularOnlyForOne() {
        val context = localizedContext("es")

        assertEquals("Favorito: Ball · 0 juegos compartidos", context.favoriteText(0))
        assertEquals("Favorito: Ball · 1 juego compartido", context.favoriteText(1))
        assertEquals("Favorito: Ball · 2 juegos compartidos", context.favoriteText(2))
    }

    private fun localizedContext(languageTag: String): Context {
        val configuration = Configuration(baseContext.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(languageTag))
        }
        return baseContext.createConfigurationContext(configuration)
    }

    private fun Context.favoriteText(playCount: Int): String = resources.getQuantityString(
        R.plurals.home_favorite,
        playCount,
        "Ball",
        playCount,
    )
}
