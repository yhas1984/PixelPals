package com.pixelpals.app.feature.overlay.behavior

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.SeededPetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelaInteractionPoseTest {
    @Test
    fun interactionClearsInheritedPoseAndStaysNeutral() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (facingSign in listOf(-1f, 1f)) {
            val bridge = TestPetBridge(context, PetType.TELA, 160, PetArtworkScale.forPet(PetType.TELA))
            val behavior = TelaBehavior(bridge, SeededPetRandom(11))
            try {
                awaitLoaded(behavior)
                bridge.animScaleX = facingSign * 2.5f
                bridge.animScaleY = -1.75f
                bridge.animOffsetX = 4f
                bridge.animOffsetY = 8f
                bridge.animRotation = -6f
                behavior.onInteract()
                assertNeutralPose(bridge, facingSign)
                behavior.updateInteracting(.1f)
                assertNeutralPose(bridge, facingSign)
            } finally {
                behavior.destroy()
            }
        }
    }

    @Test
    fun dragClearsVerticalDisplacementAndPreservesFacingSign() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (facingSign in listOf(-1f, 1f)) {
            val bridge = TestPetBridge(context, PetType.TELA, 160, PetArtworkScale.forPet(PetType.TELA))
            val behavior = TelaBehavior(bridge, SeededPetRandom(12))
            try {
                awaitLoaded(behavior)
                bridge.animScaleX = facingSign * 2.5f
                bridge.animScaleY = 1.25f
                bridge.animOffsetY = 9f
                behavior.updateDrag(.1f)
                assertEquals("drag must clear inherited vertical offset", 0f, bridge.animOffsetY, 0f)
                assertEquals("drag must preserve facing sign", facingSign, bridge.animScaleX, 0f)
                assertEquals("drag must normalize vertical scale", 1f, bridge.animScaleY, 0f)
            } finally {
                behavior.destroy()
            }
        }
    }

    private fun assertNeutralPose(bridge: TestPetBridge, facingSign: Float) {
        assertEquals("interaction must clear rotation", 0f, bridge.animRotation, 0f)
        assertEquals("interaction must clear horizontal offset", 0f, bridge.animOffsetX, 0f)
        assertEquals("interaction must clear vertical offset", 0f, bridge.animOffsetY, 0f)
        assertEquals("interaction must preserve facing sign", facingSign, bridge.animScaleX, 0f)
        assertEquals("interaction must normalize vertical scale", 1f, bridge.animScaleY, 0f)
    }

    private fun awaitLoaded(behavior: TelaBehavior) {
        val field = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (field.getBoolean(behavior) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50L)
        assertTrue("Tela atlas did not load", !field.getBoolean(behavior))
    }
}
