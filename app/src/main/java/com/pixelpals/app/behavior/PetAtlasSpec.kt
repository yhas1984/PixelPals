package com.pixelpals.app.behavior

import org.json.JSONObject

data class PetAtlasSpec(
    val version: Int,
    val petId: String,
    val atlasPath: String,
    val previewPath: String?,
    val frameWidth: Int,
    val frameHeight: Int,
    val columns: Int,
    val rows: Int,
    val frameCount: Int,
    val pivot: PetAtlasPivot?,
    val renderHints: PetAtlasRenderHints,
    val clips: List<PetClipSpec>,
    val frames: List<PetFrameSpec>
) {
    fun clip(id: String): PetClipSpec? = clips.firstOrNull { it.id == id }

    companion object {
        fun fromJson(json: JSONObject): PetAtlasSpec {
            val clipsJson = json.optJSONArray("clips")
            val framesJson = json.optJSONArray("frames")

            val clips = buildList {
                for (i in 0 until (clipsJson?.length() ?: 0)) {
                    val clipJson = clipsJson?.optJSONObject(i) ?: continue
                    add(
                        PetClipSpec(
                            id = clipJson.getString("id"),
                            frames = buildList {
                                val clipFrames = clipJson.getJSONArray("frames")
                                for (frameIndex in 0 until clipFrames.length()) {
                                    add(clipFrames.getInt(frameIndex))
                                }
                            },
                            loop = clipJson.optBoolean("loop", true),
                            frameDurationMs = clipJson.optInt("frameDurationMs", 120)
                        )
                    )
                }
            }

            val frames = buildList {
                for (i in 0 until (framesJson?.length() ?: 0)) {
                    val frameJson = framesJson?.optJSONObject(i) ?: continue
                    add(
                        PetFrameSpec(
                            index = frameJson.getInt("index"),
                            name = frameJson.getString("name"),
                            sourceHint = frameJson.optString("sourceHint").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }

            val pivotJson = json.optJSONObject("pivot")
            val renderHintsJson = json.optJSONObject("renderHints")

            return PetAtlasSpec(
                version = json.optInt("version", 1),
                petId = json.getString("petId"),
                atlasPath = json.getString("atlasPath"),
                previewPath = json.optString("previewPath").takeIf { it.isNotBlank() },
                frameWidth = json.getInt("frameWidth"),
                frameHeight = json.getInt("frameHeight"),
                columns = json.getInt("columns"),
                rows = json.optInt("rows", 1),
                frameCount = json.getInt("frameCount"),
                pivot = pivotJson?.let {
                    PetAtlasPivot(
                        x = it.getInt("x"),
                        y = it.getInt("y")
                    )
                },
                renderHints = PetAtlasRenderHints(
                    innerTransparentPaddingPx = renderHintsJson?.optInt("innerTransparentPaddingPx", 0) ?: 0,
                    recommendedBleedInsetPx = renderHintsJson?.optInt("recommendedBleedInsetPx", 0) ?: 0,
                    filterBitmap = renderHintsJson?.optBoolean("filterBitmap", false) ?: false
                ),
                clips = clips,
                frames = frames
            ).also { spec ->
                require(spec.frameWidth > 0 && spec.frameHeight > 0) { "Atlas frame dimensions must be positive" }
                require(spec.columns > 0 && spec.rows > 0) { "Atlas grid dimensions must be positive" }
                require(spec.frameCount in 1..(spec.columns * spec.rows)) { "Atlas frameCount exceeds its grid" }
                require(spec.clips.all { clip ->
                    clip.frameDurationMs > 0 && clip.frames.isNotEmpty() &&
                        clip.frames.all { frame -> frame in 0 until spec.frameCount }
                }) { "Atlas clips contain invalid frames or durations" }
            }
        }
    }
}

data class PetAtlasPivot(
    val x: Int,
    val y: Int
)

data class PetAtlasRenderHints(
    val innerTransparentPaddingPx: Int = 0,
    val recommendedBleedInsetPx: Int = 0,
    val filterBitmap: Boolean = false
)

data class PetClipSpec(
    val id: String,
    val frames: List<Int>,
    val loop: Boolean,
    val frameDurationMs: Int
)

data class PetFrameSpec(
    val index: Int,
    val name: String,
    val sourceHint: String?
)
