package com.pixelpals.app.motion

import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetAnimationPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetAnimationPlayerTest {
    @Test
    fun loopingClipWrapsAtItsDuration() {
        val player = PetAnimationPlayer(listOf(clip("idle", listOf(4, 5), loop = true, duration = 0.1f)))
        assertTrue(player.setClip("idle"))
        assertEquals(4, player.currentFrame())
        player.update(0.1f)
        assertEquals(5, player.currentFrame())
        player.update(0.1f)
        assertEquals(4, player.currentFrame())
        assertFalse(player.isFinished)
    }

    @Test
    fun oneShotClampsToLastFrameAndFinishes() {
        val player = PetAnimationPlayer(listOf(clip("touch", listOf(7, 8, 9), loop = false, duration = 0.2f)))
        player.setClip("touch")
        player.update(0.7f)
        assertEquals(9, player.currentFrame())
        assertTrue(player.isFinished)
    }

    @Test
    fun missingClipDoesNotReplaceCurrentClip() {
        val player = PetAnimationPlayer(listOf(clip("idle", listOf(1), loop = true, duration = 0.1f)))
        player.setClip("idle")
        assertFalse(player.setClip("missing"))
        assertEquals("idle", player.clipId)
    }

    private fun clip(id: String, frames: List<Int>, loop: Boolean, duration: Float): PetAnimationClip =
        PetAnimationClip(id, frames, loop, duration)
}
