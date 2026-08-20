package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetAlphaHitMaskTest {
    @Test
    fun transparentPixelsDoNotCaptureAndFramesRemainIndependent() {
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(0, 0, Color.WHITE)
            setPixel(3, 1, Color.WHITE)
        }
        val spec = PetAtlasSpec(
            version = 2,
            petId = "fixture",
            atlasPath = "fixture.png",
            previewPath = null,
            frameWidth = 2,
            frameHeight = 2,
            columns = 2,
            rows = 1,
            frameCount = 2,
            pivot = PetAtlasPivot(1, 1),
            renderHints = PetAtlasRenderHints(),
            clips = listOf(PetClipSpec("idle", listOf(0, 1), true, 100)),
            frames = listOf(
                PetFrameSpec(0, "first", null),
                PetFrameSpec(1, "second", null),
            ),
        )

        val mask = PetAlphaHitMask.fromBitmap(bitmap, spec)

        assertTrue(mask.isOpaque(frame = 0, x = 0, y = 0))
        assertFalse(mask.isOpaque(frame = 0, x = 1, y = 1))
        assertFalse(mask.isOpaque(frame = 1, x = 0, y = 0))
        assertTrue(mask.isOpaque(frame = 1, x = 1, y = 1))
        assertFalse(mask.isOpaque(frame = 2, x = 0, y = 0))
        bitmap.recycle()
    }
}
