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
    @Test fun scheduledSleepWaitsForGroundActionsAndThermalRecovery(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val safeModes: Map<PetType, Set<String>> = mapOf(
                PetType.CORGI to setOf("REST", "ALERT"),
                PetType.GINGER to setOf("SIT", "SLEEP", "STANDING"),
                PetType.PIRU to setOf("SLEEP"),
                PetType.MENTA to setOf("COIL", "SLEEP"),
                PetType.JELLY to setOf("IDLE"),
                PetType.PATITO to setOf("QUACK"),
            )
            for ((pet, safe) in safeModes) {
                val bridge = TestPetBridge(instrumentation.targetContext, pet)
                if (pet == PetType.JELLY) {
                    bridge.getWindowParams().y = bridge.groundY
                    bridge.updateWindowLayout(bridge.getWindowParams())
                }
                val behavior: PetBehavior = PetBehaviorFactory.create(pet, bridge, SeededPetRandom(12))
                try {
                    val mode = behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }
                    for (value in requireNotNull(mode.type.enumConstants)) {
                        mode.set(behavior, value)
                        val name: String = (value as Enum<*>).name
                        settlePiruYawn(pet, behavior, name)
                        assertEquals("$pet $name handoff", name in safe, behavior.canStartScheduledSleep(false))
                        val staticTravel: Boolean = name in setOf("WALK", "WADDLE", "SLITHER") ||
                            (pet == PetType.GINGER && name == "STALK")
                        assertEquals("$pet $name reduced handoff", name in safe || staticTravel,
                            behavior.canStartScheduledSleep(true))
                    }
                } finally { behavior.destroy() }
            }
            val runtime = YukiRuntimeBehavior(TestPetBridge(instrumentation.targetContext, PetType.YUKI), SeededPetRandom(12))
            try {
                val intent = RuntimePetBehavior::class.java.getDeclaredField("currentIntent").apply { isAccessible = true }
                assertFalse("Unloaded runtime must not hand off", runtime.canStartScheduledSleep(false))
                for (value in com.pixelpals.app.core.runtime.PetIntent.entries) {
                    intent.set(runtime, value)
                    val safe: Boolean = value.name in setOf("IDLE", "SLEEP")
                    assertEquals(value.name, safe, runtime.canStartScheduledSleep(false))
                    assertEquals(value.name, safe || value.name == "WALK", runtime.canStartScheduledSleep(true))
                }
            } finally { runtime.destroy() }
        }
    }

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
                        settlePiruYawn(pet, behavior, name)
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

    private fun settlePiruYawn(pet: PetType, behavior: PetBehavior, mode: String): Unit {
        if (pet != PetType.PIRU || mode != "SLEEP") return
        val timer = behavior.javaClass.getDeclaredField("modeTimer").apply { isAccessible = true }
        timer.setFloat(behavior, 0f)
        assertFalse("Piru is still yawning", behavior.isSleeping)
        assertFalse("Piru must finish its yawn before handoff", behavior.canStartScheduledSleep(false))
        timer.setFloat(behavior, .35f)
    }
}
