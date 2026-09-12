package com.pixelpals.app.feature.care

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetStatusSnapshot
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareResultFormatterTest {
    private val before: PetStatusSnapshot = PetStatusSnapshot("corgi", 40, 40, 40, 40, 20, PetMood.HAPPY, 0, 0, CareAction.FEED, 0)

    @Test fun reportsRealPositiveAndNegativeDeltasInEnglish(): Unit {
        val text: String = CareResultFormatter.describe(context("en"), CareSceneResult.Completed(before, before.copy(hunger = 70, energy = 35)))
        assertEquals("Fullness +30 · Energy -5", text)
    }

    @Test fun reportsRealDeltasInSpanish(): Unit {
        val text: String = CareResultFormatter.describe(context("es"), CareSceneResult.Completed(before, before.copy(hunger = 70, bond = 23)))
        assertEquals("Saciedad +30 · Vínculo +3", text)
    }

    @Test fun maximumsAndCooldownsDoNotInventRewards(): Unit {
        val text: String = CareResultFormatter.describe(context("en"), CareSceneResult.Completed(before, before))
        assertEquals("A little moment together. No extra changes this time.", text)
        assertFalse(text.contains("+"))
    }

    @Test fun medicineIncludesRecoveryDelta(): Unit {
        val text: String = CareResultFormatter.describe(context("es"), CareSceneResult.Completed(before, before.copy(recoveryProgress = 25)))
        assertEquals("Recuperación +25", text)
    }

    @Test fun wakingDoesNotPretendTheRequestedActionWasApplied(): Unit {
        val text: String = CareResultFormatter.describe(context("en"), CareSceneResult.Completed(before.copy(condition = PetCondition.HIBERNATING), before))
        assertTrue(text.contains("awake"))
        assertFalse(text.contains("Fullness"))
    }

    @Test fun recoveryResetOnHealingIsNotPresentedAsLostProgress(): Unit {
        val text: String = CareResultFormatter.describe(context("en"), CareSceneResult.Completed(
            before.copy(condition = PetCondition.RECOVERING, recoveryProgress = 95), before))
        assertTrue(text.contains("Feeling better"))
        assertFalse(text.contains("-95"))
    }

    @Test fun cappedPlayRecognizesTheGameAndKeepsRealCostsInBothLanguages(): Unit {
        val result = CareSceneResult.Completed(before, before.copy(hunger = 36, energy = 32, health = 36))
        val spanish = CareResultFormatter.describe(context("es"), result, CareSceneAction.PLAY)
        assertTrue(spanish.startsWith("Un juego más juntos.\n"))
        assertTrue(spanish.contains("Jugar gasta energía y abre el apetito."))
        assertTrue(spanish.contains("Saciedad -4 · Energía -8 · Salud -4"))
        assertFalse(spanish.contains("Vínculo") || spanish.contains("Monedas") || spanish.contains("+"))
        val english = CareResultFormatter.describe(context("en"), result, CareSceneAction.PLAY)
        assertTrue(english.startsWith("Another game together.\n"))
        assertTrue(english.contains("Playing uses energy and works up an appetite."))
        assertTrue(english.contains("Fullness -4 · Energy -8 · Health -4"))
        assertFalse(english.contains("Bond") || english.contains("Coins") || english.contains("+"))
    }

    @Test fun completedPlayWithoutNumericChangesDoesNotInventEffortOrRewards(): Unit {
        val result = CareSceneResult.Completed(before, before)
        assertEquals("Un juego más juntos.", CareResultFormatter.describe(context("es"), result, CareSceneAction.PLAY))
        assertEquals("Another game together.", CareResultFormatter.describe(context("en"), result, CareSceneAction.PLAY))
    }

    @Test fun playPreservesPositiveRewardsAndWakeTakesPrecedence(): Unit {
        val gained = CareSceneResult.Completed(before, before.copy(energy = 32, bond = 23, softCurrency = 5))
        val description = CareResultFormatter.describe(context("en"), gained, CareSceneAction.PLAY)
        assertTrue(description.contains("Energy -8") && description.contains("Bond +3") && description.contains("Coins +5"))
        val waking = CareSceneResult.Completed(before.copy(condition = PetCondition.HIBERNATING), before)
        val wakeDescription = CareResultFormatter.describe(context("en"), waking, CareSceneAction.PLAY)
        assertTrue(wakeDescription.contains("awake"))
        assertFalse(wakeDescription.contains("game") || wakeDescription.contains("Energy"))
    }

    private fun context(language: String): Context {
        val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val config: Configuration = Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return target.createConfigurationContext(config)
    }
}
