package com.pixelpals.app.feature.care

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.care.scene.CareSceneResult
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

    private fun context(language: String): Context {
        val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val config: Configuration = Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return target.createConfigurationContext(config)
    }
}
