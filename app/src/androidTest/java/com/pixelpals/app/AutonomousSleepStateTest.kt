package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutonomousSleepStateTest {
    @Test fun onlyActualSleepingModesExposeDreams(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (pet: PetType in listOf(PetType.GINGER, PetType.NUBE_MICHI, PetType.PIRU, PetType.MENTA, PetType.TELA)) {
                val behavior: PetBehavior = PetBehaviorFactory.create(pet,
                    TestPetBridge(instrumentation.targetContext, pet), SeededPetRandom(12))
                try {
                    val mode = behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }
                    for (value in requireNotNull(mode.type.enumConstants)) {
                        mode.set(behavior, value)
                        val name: String = (value as Enum<*>).name
                        assertEquals("$pet $name must report its actual sleep state",
                            name == "SLEEP" || name == "SLEEP_FLOAT", behavior.isSleeping)
                    }
                } finally { behavior.destroy() }
            }
            val corgi: PetBehavior = PetBehaviorFactory.create(PetType.CORGI,
                TestPetBridge(instrumentation.targetContext, PetType.CORGI), SeededPetRandom(12))
            try {
                val mode = corgi.javaClass.getDeclaredField("mode").apply { isAccessible = true }
                for (value in requireNotNull(mode.type.enumConstants)) {
                    mode.set(corgi, value)
                    assertFalse("Corgi's resting blink is not sleep", corgi.isSleeping)
                }
            } finally { corgi.destroy() }
        }
    }
}
