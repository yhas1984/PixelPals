package com.pixelpals.app.animation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.pixelpals.app.BuildConfig
import com.pixelpals.app.PetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * AnimationStudio — Motor de Generación On-Demand con Gemini
 *
 * Funciona como un "Estudio de Animación Infinito" que genera
 * frames faltantes usando Gemini 2.5 Flash Image Generation.
 */
class AnimationStudio(private val context: Context) {

    companion object {
        private const val TAG = "AnimationStudio"
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-image-preview:generateContent"
        private const val IMAGE_MODEL = "gemini-3.1-flash-image-preview"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey = BuildConfig.GEMINI_API_KEY

    /**
     * Audit assets and generate missing frames
     */
    suspend fun auditAndGenerate(petType: PetType, existingFrames: List<Int>): List<GeneratedFrame> {
        val missingFrames = findMissingFrames(petType, existingFrames)
        if (missingFrames.isEmpty()) {
            Log.d(TAG, "No missing frames for ${petType.displayName}")
            return emptyList()
        }

        Log.d(TAG, "Generating ${missingFrames.size} missing frames for ${petType.displayName}")
        val generated = mutableListOf<GeneratedFrame>()

        for (frameDef in missingFrames) {
            try {
                val bitmap = generateFrame(petType, frameDef)
                if (bitmap != null) {
                    val savedPath = saveFrame(petType, frameDef.index, bitmap)
                    generated.add(GeneratedFrame(frameDef.index, bitmap, savedPath))
                    Log.d(TAG, "Generated frame ${frameDef.index}: ${frameDef.description}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate frame ${frameDef.index}: ${e.message}")
            }
        }

        return generated
    }

    /**
     * Find which frames are missing based on pet personality
     */
    private fun findMissingFrames(petType: PetType, existingFrames: List<Int>): List<FrameDefinition> {
        val required = getRequiredFrames(petType)
        return required.filter { it.index !in existingFrames }
    }

    /**
     * Define required frames based on pet personality
     */
    private fun getRequiredFrames(petType: PetType): List<FrameDefinition> {
        return when (petType) {
            PetType.BLOOP -> listOf(
                FrameDefinition(0, "floating idle", "ghost floating peacefully"),
                FrameDefinition(1, "peek shy", "ghost peeking shyly"),
                FrameDefinition(2, "surprised", "ghost surprised with wide eyes"),
                FrameDefinition(3, "happy", "ghost happy with sparkles")
            )
            PetType.NUBE_MICHI -> listOf(
                FrameDefinition(0, "cloud idle", "fluffy cloud cat sleeping"),
                FrameDefinition(1, "stretch yawn", "cloud cat stretching and yawning"),
                FrameDefinition(2, "alert ears", "cloud cat alert with ears up"),
                FrameDefinition(3, "happy purr", "cloud cat purring happily")
            )
            PetType.JELLY -> listOf(
                FrameDefinition(0, "wobble idle", "jelly slime wobbling"),
                FrameDefinition(1, "bounce up", "jelly bouncing upward"),
                FrameDefinition(2, "squish flat", "jelly squished flat"),
                FrameDefinition(3, "happy glow", "jelly glowing happily")
            )
            PetType.CORGI -> listOf(
                FrameDefinition(0, "sit idle", "corgi sitting attentively, looking forward"),
                FrameDefinition(1, "stand alert", "corgi standing on all fours, alert pose"),
                FrameDefinition(2, "walk left", "corgi walking left with paws moving"),
                FrameDefinition(3, "walk right", "corgi walking right with paws moving"),
                FrameDefinition(4, "run", "corgi running fast"),
                FrameDefinition(5, "jump", "corgi jumping up excitedly"),
                FrameDefinition(6, "bark", "corgi barking with mouth open")
            )
            PetType.GINGER -> listOf(
                FrameDefinition(0, "stretch forward", "elegant cat stretching forward"),
                FrameDefinition(1, "stretch back", "elegant cat stretching backward"),
                FrameDefinition(2, "standing", "cat standing on all fours alert"),
                FrameDefinition(3, "wink", "cat winking one eye"),
                FrameDefinition(4, "sitting", "cat sitting elegantly"),
                FrameDefinition(5, "lick paw", "cat licking paw"),
                FrameDefinition(6, "clean face", "cat cleaning face with paw"),
                FrameDefinition(7, "pout", "cat pouting with displeased face"),
                FrameDefinition(8, "roll start", "cat starting to roll"),
                FrameDefinition(9, "rolling", "cat rolling on back"),
                FrameDefinition(10, "belly up", "cat lying belly up")
            )
            PetType.PATITO -> listOf(
                FrameDefinition(0, "idle side", "duck looking sideways"),
                FrameDefinition(1, "waddle left", "duck waddling left"),
                FrameDefinition(2, "waddle right", "duck waddling right"),
                FrameDefinition(3, "peek front", "duck peeking at camera"),
                FrameDefinition(4, "curious", "duck curious with head tilt"),
                FrameDefinition(5, "swim", "duck swimming in water")
            )
            PetType.DIABLILLO -> listOf(
                FrameDefinition(0, "lurk left", "imp lurking and watching left"),
                FrameDefinition(1, "lurk right", "imp lurking and watching right"),
                FrameDefinition(2, "run left", "imp running mischievously left"),
                FrameDefinition(3, "run right", "imp running mischievously right"),
                FrameDefinition(4, "surprised", "imp surprised with wide eyes"),
                FrameDefinition(5, "fire jump", "imp jumping with fire")
            )
        }
    }

    /**
     * Generate a single frame using Gemini
     */
    private suspend fun generateFrame(petType: PetType, frameDef: FrameDefinition): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(petType, frameDef)
                val baseFrame = loadBaseFrame(petType)

                val response = callGemini(prompt, baseFrame)
                extractImageFromResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Gemini generation failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Build the generation prompt - CRITICAL: must specify transparent background
     */
    private fun buildPrompt(petType: PetType, frameDef: FrameDefinition): String {
        val style = when (petType) {
            PetType.BLOOP -> "cute ghost character, translucent, ethereal, purple glow"
            PetType.NUBE_MICHI -> "fluffy cloud cat, soft, dreamy, white/cream colors"
            PetType.JELLY -> "bouncy slime creature, neon green, translucent, wobbly"
            PetType.CORGI -> "cute corgi puppy, orange/white fur, round face, short legs, brown patches on back"
            PetType.GINGER -> "elegant orange cat with pink bow, graceful, sophisticated"
            PetType.PATITO -> "cute yellow duck, rubber duck style, round, curious"
            PetType.DIABLILLO -> "mischievous imp, small horns, devil tail, red/purple, chaotic"
        }

        val contextHint = when (petType) {
            PetType.CORGI -> when (frameDef.index) {
                0 -> "sitting attentively, looking forward, tail down"
                1 -> "standing on all fours, alert pose, ready to move"
                2 -> "walking left, left paw forward, right paw back"
                3 -> "walking right, right paw forward, left paw back"
                4 -> "running fast, all four paws in motion, ears back"
                5 -> "jumping up with excitement, paws off ground"
                6 -> "barking with mouth open, playful expression"
                else -> frameDef.action
            }
            else -> frameDef.action
        }

        return """
            Generate a single sprite frame: $style
            
            ACTION: $contextHint
            
            STYLE: 128x128 pixel art, cute, chibi style, clear dark outlines, vibrant colors
            
            CRITICAL - NO BACKGROUND:
            - The image MUST have a completely transparent background
            - PNG format with alpha channel
            - Only the character should be visible
            - No background color, no floor, no shadows on ground
            - No white, gray, or colored background
            - Character should appear to float in transparent space
            
            Generate ONLY the character on transparent background.
        """.trimIndent()
    }

    /**
     * Load base frame from assets
     */
    private fun loadBaseFrame(petType: PetType): Bitmap? {
        return try {
            val resId = when (petType) {
                PetType.BLOOP -> context.resources.getIdentifier("fantasma_0", "drawable", context.packageName)
                PetType.NUBE_MICHI -> context.resources.getIdentifier("gato_0", "drawable", context.packageName)
                PetType.JELLY -> context.resources.getIdentifier("jelly_0", "drawable", context.packageName)
                PetType.CORGI -> context.resources.getIdentifier("perro_0", "drawable", context.packageName)
                PetType.GINGER -> context.resources.getIdentifier("ginger_0", "drawable", context.packageName)
                PetType.PATITO -> context.resources.getIdentifier("patito_0", "drawable", context.packageName)
                PetType.DIABLILLO -> context.resources.getIdentifier("diablillo_0", "drawable", context.packageName)
            }
            if (resId != 0) {
                BitmapFactory.decodeResource(context.resources, resId)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Call Gemini API with image generation
     */
    private fun callGemini(prompt: String, baseImage: Bitmap?): String {
        val contents = JSONArray()

        // Add text prompt
        val textPart = JSONObject().apply { put("text", prompt) }
        val parts = JSONArray().apply { put(textPart) }

        // Add base image if available
        if (baseImage != null) {
            val imageBase64 = bitmapToBase64(baseImage)
            val imagePart = JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "image/png")
                    put("data", imageBase64)
                })
            }
            parts.put(imagePart)
        }

        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", parts)
        })

        val requestBody = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.8)
                put("topK", 40)
                put("topP", 0.95)
                put("maxOutputTokens", 8192)
                put("responseModalities", JSONArray().apply {
                    put("TEXT")
                    put("IMAGE")
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$GEMINI_API_URL?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API error: ${response.code} - ${response.message}")
                Log.e(TAG, "Response body: $responseBody")
                throw Exception("Gemini API error: ${response.code} - ${response.message}")
            }
            return responseBody
        }
    }

    /**
     * Extract image from Gemini response and remove background
     */
    private fun extractImageFromResponse(response: String): Bitmap? {
        return try {
            val json = JSONObject(response)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) return null

            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("inlineData")) {
                    val inlineData = part.getJSONObject("inlineData")
                    val mimeType = inlineData.getString("mimeType")
                    if (mimeType.startsWith("image/")) {
                        val base64Data = inlineData.getString("data")
                        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        // Remove background from generated image
                        return removeBackground(bitmap)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract image: ${e.message}")
            null
        }
    }

    /**
     * Remove background from image and make it transparent
     * Aggressive removal of any solid color background
     */
    private fun removeBackground(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        Log.d(TAG, "Removing background from ${width}x${height} image")

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Sample background color from ALL edges (not just corners)
        val edgeColors = mutableListOf<Int>()

        // Top and bottom edges
        for (x in 0 until width step 2) {
            edgeColors.add(bitmap.getPixel(x, 0))
            edgeColors.add(bitmap.getPixel(x, height - 1))
        }
        // Left and right edges
        for (y in 0 until height step 2) {
            edgeColors.add(bitmap.getPixel(0, y))
            edgeColors.add(bitmap.getPixel(width - 1, y))
        }

        // Most common edge color is the background
        val bgColor = edgeColors.groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key ?: 0xFFFFFFFF.toInt()

        val bgR = (bgColor shr 16) and 0xFF
        val bgG = (bgColor shr 8) and 0xFF
        val bgB = bgColor and 0xFF

        Log.d(TAG, "Detected background color: R=$bgR G=$bgG B=$bgB from ${edgeColors.size} edge samples")

        // Threshold - more aggressive
        val threshold = 60

        // Process each pixel
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var transparentCount = 0
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Check if pixel is similar to background
            val rDiff = Math.abs(r - bgR)
            val gDiff = Math.abs(g - bgG)
            val bDiff = Math.abs(b - bgB)
            val avgDiff = (rDiff + gDiff + bDiff) / 3

            // Remove if: similar to background OR pure white/light gray
            if (avgDiff < threshold ||
                (r > 230 && g > 230 && b > 230) ||  // White
                (r > 200 && g > 200 && b > 200 && Math.abs(r - g) < 20 && Math.abs(g - b) < 20) // Light gray
            ) {
                pixels[i] = 0x00000000
                transparentCount++
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        Log.d(TAG, "First pass: made $transparentCount/${pixels.size} pixels transparent")

        // Flood fill from edges to remove any remaining connected background
        floodFillBackground(result)

        // Count final transparent pixels
        result.getPixels(pixels, 0, width, 0, 0, width, height)
        val finalTransparent = pixels.count { (it shr 24) == 0 }
        Log.d(TAG, "Final: $finalTransparent/${pixels.size} pixels transparent")

        // Feather edges for smoother transparency
        featherEdges(result)

        Log.d(TAG, "Background removed successfully")
        return result
    }

    /**
     * Flood fill from edges to remove connected background pixels
     */
    private fun floodFillBackground(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Queue for flood fill
        val queue = ArrayDeque<Int>()

        // Add all edge pixels to queue
        for (x in 0 until width) {
            queue.add(x) // Top edge
            queue.add((height - 1) * width + x) // Bottom edge
        }
        for (y in 0 until height) {
            queue.add(y * width) // Left edge
            queue.add(y * width + width - 1) // Right edge
        }

        // Track visited pixels
        val visited = BooleanArray(width * height)

        // Get background color from edge
        val bgColor = pixels[0]
        val bgR = (bgColor shr 16) and 0xFF
        val bgG = (bgColor shr 8) and 0xFF
        val bgB = bgColor and 0xFF

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            if (idx < 0 || idx >= pixels.size || visited[idx]) continue

            val pixel = pixels[idx]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val alpha = (pixel shr 24) and 0xFF

            // Check if this pixel is background-like
            val isBgLike = alpha < 128 ||
                    (Math.abs(r - bgR) < 50 && Math.abs(g - bgG) < 50 && Math.abs(b - bgB) < 50) ||
                    (r > 220 && g > 220 && b > 220)

            if (isBgLike) {
                visited[idx] = true
                pixels[idx] = 0x00000000 // Make transparent

                // Add neighbors
                val x = idx % width
                val y = idx / width

                if (x > 0) queue.add(idx - 1)
                if (x < width - 1) queue.add(idx + 1)
                if (y > 0) queue.add(idx - width)
                if (y < height - 1) queue.add(idx + width)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Feather edges for smoother transparency
     */
    private fun featherEdges(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Find boundary between transparent and opaque pixels
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val alpha = (pixels[idx] shr 24) and 0xFF

                // If pixel is opaque but neighbors are transparent, feather it
                if (alpha > 200) {
                    val neighbors = listOf(
                        pixels[(y - 1) * width + x], // up
                        pixels[(y + 1) * width + x], // down
                        pixels[y * width + (x - 1)], // left
                        pixels[y * width + (x + 1)]  // right
                    )

                    val transparentNeighbors = neighbors.count { ((it shr 24) and 0xFF) < 128 }

                    if (transparentNeighbors > 0) {
                        // Reduce alpha at boundary
                        val newAlpha = (alpha * (4 - transparentNeighbors) / 4)
                        pixels[idx] = (pixels[idx] and 0x00FFFFFF) or (newAlpha shl 24)
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Convert bitmap to base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Save generated frame to internal storage
     */
    private fun saveFrame(petType: PetType, frameIndex: Int, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "generated_frames/${petType.name.lowercase()}")
        dir.mkdirs()

        val file = File(dir, "frame_${frameIndex}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        Log.d(TAG, "Saved frame to: ${file.absolutePath}")
        return file.absolutePath
    }

    /**
     * Load a previously generated frame
     */
    fun loadGeneratedFrame(petType: PetType, frameIndex: Int): Bitmap? {
        val file = File(context.filesDir, "generated_frames/${petType.name.lowercase()}/frame_${frameIndex}.png")
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else null
    }

    /**
     * Data classes
     */
    data class FrameDefinition(
        val index: Int,
        val action: String,
        val description: String
    )

    data class GeneratedFrame(
        val index: Int,
        val bitmap: Bitmap,
        val savedPath: String
    )
}
