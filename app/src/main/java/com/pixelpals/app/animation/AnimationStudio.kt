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
                FrameDefinition(0, "sit idle", "corgi sitting attentively"),
                FrameDefinition(1, "walk left", "corgi walking left with wagging tail"),
                FrameDefinition(2, "walk right", "corgi walking right with wagging tail"),
                FrameDefinition(3, "happy jump", "corgi jumping excitedly"),
                FrameDefinition(4, "bark", "corgi barking with open mouth"),
                FrameDefinition(5, "lick", "corgi licking screen"),
                FrameDefinition(6, "run", "corgi running fast"),
                FrameDefinition(7, "play bow", "corgi in play bow position"),
                FrameDefinition(8, "spin", "corgi spinning in circles"),
                FrameDefinition(9, "shake", "corgi shaking water off"),
                FrameDefinition(10, "sleep", "corgi sleeping curled up"),
                FrameDefinition(11, "alert", "corgi alert with ears up")
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
     * Build the generation prompt
     */
    private fun buildPrompt(petType: PetType, frameDef: FrameDefinition): String {
        val style = when (petType) {
            PetType.BLOOP -> "cute ghost character, translucent, ethereal, purple glow"
            PetType.NUBE_MICHI -> "fluffy cloud cat, soft, dreamy, white/cream colors"
            PetType.JELLY -> "bouncy slime creature, neon green, translucent, wobbly"
            PetType.CORGI -> "cute corgi puppy, orange/white, round face, short legs"
            PetType.GINGER -> "elegant orange cat with pink bow, graceful, sophisticated"
            PetType.PATITO -> "cute yellow duck, rubber duck style, round, curious"
            PetType.DIABLILLO -> "mischievous imp, small horns, devil tail, red/purple, chaotic"
        }

        return """
            Generate a pixel art animation frame for a $style
            
            Frame description: ${frameDef.description}
            Action: ${frameDef.action}
            
            Style requirements:
            - 128x128 pixels
            - Transparent background (PNG)
            - Pixel art style with clear outlines
            - Cute and expressive
            - Consistent with the character design
            - No text or watermarks
            
            Return ONLY the image, no text.
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
     * Extract image from Gemini response
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
                        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
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
