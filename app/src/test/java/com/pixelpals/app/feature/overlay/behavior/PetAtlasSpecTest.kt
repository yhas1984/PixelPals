package com.pixelpals.app.feature.overlay.behavior

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetAtlasSpecTest {
    @Test
    fun `legacy manifests keep frame occupancy normalization by default`() {
        val spec = PetAtlasSpec.fromJson(JSONObject(validManifest(renderHints = "")))

        assertTrue(spec.renderHints.useFrameOccupancyNormalization)
    }

    @Test
    fun `normalized V2 manifests can disable manual occupancy tables`() {
        val spec = PetAtlasSpec.fromJson(
            JSONObject(validManifest(renderHints = ", \"useFrameOccupancyNormalization\": false"))
        )

        assertFalse(spec.renderHints.useFrameOccupancyNormalization)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pivot outside the frame is rejected`() {
        PetAtlasSpec.fromJson(JSONObject(validManifest(pivotX = 65)))
    }

    private fun validManifest(renderHints: String = "", pivotX: Int = 32): String = """
        {
          "version": 2,
          "petId": "fixture",
          "atlasPath": "fixture.png",
          "frameWidth": 64,
          "frameHeight": 64,
          "columns": 1,
          "rows": 1,
          "frameCount": 1,
          "pivot": {"x": $pivotX, "y": 60},
          "renderHints": {"drawScale": 1.0 $renderHints},
          "clips": [{"id": "idle", "frames": [0], "loop": true, "frameDurationMs": 100}],
          "frames": [{"index": 0, "name": "idle_00"}]
        }
    """.trimIndent()
}
