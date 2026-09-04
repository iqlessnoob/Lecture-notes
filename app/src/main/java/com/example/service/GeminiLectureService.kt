package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.DiagramItem
import com.example.data.model.FormulaItem
import com.example.data.model.GeneratedLectureResult
import com.example.data.model.TimestampMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GeminiLectureService(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val systemInstructionText = """
        You are an elite academic study-notes assistant. You are given a lecture video recording, visual keyframes, or a lecture reference.
        Carefully analyze the lecture topic, instructor presentation, slide diagrams, and derivations, then produce comprehensive, highly structured study notes strictly formatted as JSON matching this schema:

        {
          "title": "string - inferred or specific lecture topic",
          "summary": "string - 3-5 sentence rigorous conceptual overview",
          "key_concepts": [
            "string - key concept with bold definition and explanation"
          ],
          "formulas": [
            {
              "name": "string - formula name",
              "latex": "string - formula in standard LaTeX without wrapping dollars, e.g. \\Delta G = \\Delta H - T\\Delta S",
              "explanation": "string - meaning of variables and conceptual significance"
            }
          ],
          "diagrams": [
            {
              "label": "string - what the diagram represents",
              "timestamp": "MM:SS - timestamp in the lecture",
              "description": "string - detailed description of all entities, arrows, states, or components",
              "diagram_type": "flowchart | cycle | graph | hierarchy | other",
              "mermaid_code": "string - clean Mermaid.js diagram definition (e.g. graph TD\n A --> B). Omit if not applicable."
            }
          ],
          "timestamps": [
            { "time": "MM:SS", "topic": "string - what begins being taught or derived at this timestamp" }
          ]
        }

        Requirements:
        1. Formulas: Provide accurate, readable LaTeX expressions.
        2. Diagrams: Recreate actual procedural cycles, flowcharts, or system hierarchies with valid Mermaid.js code.
        3. Notes must be entirely relevant and specific to the requested lecture topic and frames.
        4. Output ONLY valid JSON matching this schema.
    """.trimIndent()

    suspend fun generateNotesForYouTube(
        youtubeUrl: String,
        subjectSlotName: String,
        customApiKey: String? = null
    ): Result<GeneratedLectureResult> = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey(customApiKey)
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please configure your API key in settings.")
            )
        }

        // Fetch real YouTube metadata (video title, creator, thumbnail) via oEmbed
        val meta = fetchYouTubeMetadata(youtubeUrl)
        val videoTitle = meta?.first ?: ""
        val authorName = meta?.second ?: ""
        val thumbnailUrl = meta?.third ?: ""

        val frames = mutableListOf<Bitmap>()
        if (thumbnailUrl.isNotBlank()) {
            fetchThumbnailBitmap(thumbnailUrl)?.let { frames.add(it) }
        }

        val prompt = buildString {
            appendLine("Subject / Course: $subjectSlotName")
            if (videoTitle.isNotBlank()) {
                appendLine("Lecture Video Title: $videoTitle")
            }
            if (authorName.isNotBlank()) {
                appendLine("Instructor / Channel: $authorName")
            }
            appendLine("Video URL: $youtubeUrl")
            appendLine("Please analyze this lecture. Extract and synthesize deep, topic-specific study notes with core conceptual breakdowns, mathematical formulas in LaTeX, structural diagram recreations in Mermaid.js, and topic timestamps.")
        }

        callGeminiApi(apiKey, prompt, frames)
    }

    suspend fun generateNotesForUploadedVideo(
        videoUri: Uri,
        subjectSlotName: String,
        customApiKey: String? = null
    ): Result<GeneratedLectureResult> = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey(customApiKey)
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please configure your API key in settings.")
            )
        }

        val frames = extractSampleFrames(videoUri, frameCount = 4)
        val prompt = buildString {
            appendLine("Subject / Course: $subjectSlotName")
            appendLine("Here are sequential visual keyframes sampled across the uploaded lecture video recording.")
            appendLine("Inspect the chalkboard, slides, whiteboard notes, and diagrams visible in these frames.")
            appendLine("Produce comprehensive structured study notes containing summary, key concepts, formulas in proper LaTeX, recreated diagrams with Mermaid.js syntax, and topic timestamps.")
        }

        callGeminiApi(apiKey, prompt, frames)
    }

    private fun callGeminiApi(
        apiKey: String,
        prompt: String,
        frames: List<Bitmap>
    ): Result<GeneratedLectureResult> {
        // Preferred modern models: gemini-3.6-flash, with fallback to gemini-3.5-flash-lite if 503 or transient error occurs
        val models = listOf("gemini-3.6-flash", "gemini-3.5-flash-lite")
        var lastError: Exception? = null

        for (model in models) {
            val result = executeGeminiRequest(apiKey, model, prompt, frames)
            if (result.isSuccess) {
                return result
            } else {
                lastError = result.exceptionOrNull() as? Exception
                val msg = lastError?.message.orEmpty()
                // Retry with next model on 503 (high demand), 429 (rate limit), or 404 (model unavailable)
                if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("429") || msg.contains("404")) {
                    continue
                } else {
                    return result
                }
            }
        }
        return Result.failure(lastError ?: Exception("Failed to generate notes with Gemini API"))
    }

    private fun executeGeminiRequest(
        apiKey: String,
        modelName: String,
        prompt: String,
        frames: List<Bitmap>
    ): Result<GeneratedLectureResult> {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val partsArray = JSONArray()
            partsArray.put(JSONObject().put("text", prompt))

            // Add image frames if available
            for (frame in frames) {
                val base64 = bitmapToBase64(frame)
                if (base64.isNotEmpty()) {
                    val inlineData = JSONObject()
                    inlineData.put("mimeType", "image/jpeg")
                    inlineData.put("data", base64)
                    partsArray.put(JSONObject().put("inlineData", inlineData))
                }
            }

            val contentsArray = JSONArray().put(JSONObject().put("parts", partsArray))
            val sysParts = JSONArray().put(JSONObject().put("text", systemInstructionText))
            val sysInstructionObj = JSONObject().put("parts", sysParts)

            val genConfig = JSONObject()
            genConfig.put("temperature", 0.2)
            genConfig.put("topP", 0.95)
            genConfig.put("responseMimeType", "application/json")

            val rootJson = JSONObject()
            rootJson.put("contents", contentsArray)
            rootJson.put("systemInstruction", sysInstructionObj)
            rootJson.put("generationConfig", genConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val respBodyStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return Result.failure(Exception("Gemini API ($modelName) error ${response.code}: $respBodyStr"))
            }

            val parsedResult = parseGeminiResponse(respBodyStr)
            Result.success(parsedResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchYouTubeMetadata(youtubeUrl: String): Triple<String, String, String>? {
        return try {
            val encoded = URLEncoder.encode(youtubeUrl, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val title = json.optString("title", "")
                val author = json.optString("author_name", "")
                val thumbnail = json.optString("thumbnail_url", "")
                Triple(title, author, thumbnail)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchThumbnailBitmap(thumbnailUrl: String): Bitmap? {
        return try {
            val req = Request.Builder().url(thumbnailUrl).build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val bytes = resp.body?.bytes() ?: return null
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                scaleBitmap(bmp, maxDimension = 640)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseGeminiResponse(rawJsonResponse: String): GeneratedLectureResult {
        val root = JSONObject(rawJsonResponse)
        val candidates = root.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)
        val content = candidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val textPart = parts?.optJSONObject(0)?.optString("text") ?: ""

        val cleanedJson = cleanJsonString(textPart)
        val json = JSONObject(cleanedJson)

        val title = json.optString("title", "Lecture Notes")
        val summary = json.optString("summary", "Summary not available.")

        val keyConcepts = mutableListOf<String>()
        val kcArray = json.optJSONArray("key_concepts")
        if (kcArray != null) {
            for (i in 0 until kcArray.length()) {
                keyConcepts.add(kcArray.optString(i))
            }
        }

        val formulas = mutableListOf<FormulaItem>()
        val fArray = json.optJSONArray("formulas")
        if (fArray != null) {
            for (i in 0 until fArray.length()) {
                val item = fArray.optJSONObject(i) ?: continue
                formulas.add(
                    FormulaItem(
                        name = item.optString("name", "Formula ${i + 1}"),
                        latex = item.optString("latex", ""),
                        explanation = item.optString("explanation", "")
                    )
                )
            }
        }

        val diagrams = mutableListOf<DiagramItem>()
        val dArray = json.optJSONArray("diagrams")
        if (dArray != null) {
            for (i in 0 until dArray.length()) {
                val item = dArray.optJSONObject(i) ?: continue
                diagrams.add(
                    DiagramItem(
                        label = item.optString("label", "Diagram ${i + 1}"),
                        timestamp = item.optString("timestamp", "00:00"),
                        description = item.optString("description", ""),
                        diagramType = item.optString("diagram_type", "flowchart"),
                        mermaidCode = item.optString("mermaid_code", "")
                    )
                )
            }
        }

        val timestamps = mutableListOf<TimestampMarker>()
        val tArray = json.optJSONArray("timestamps")
        if (tArray != null) {
            for (i in 0 until tArray.length()) {
                val item = tArray.optJSONObject(i) ?: continue
                timestamps.add(
                    TimestampMarker(
                        time = item.optString("time", "00:00"),
                        topic = item.optString("topic", "")
                    )
                )
            }
        }

        return GeneratedLectureResult(
            title = title,
            summary = summary,
            keyConcepts = keyConcepts,
            formulas = formulas,
            diagrams = diagrams,
            timestamps = timestamps
        )
    }

    private fun cleanJsonString(input: String): String {
        var str = input.trim()
        val jsonStart = str.indexOf('{')
        val jsonEnd = str.lastIndexOf('}')
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return str.substring(jsonStart, jsonEnd + 1)
        }
        return str
    }

    fun extractFrameAtTimestamp(videoUri: Uri, timestamp: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            try {
                val pfd = context.contentResolver.openFileDescriptor(videoUri, "r")
                if (pfd != null) {
                    retriever.setDataSource(pfd.fileDescriptor)
                } else {
                    retriever.setDataSource(context, videoUri)
                }
            } catch (_: Exception) {
                retriever.setDataSource(context, videoUri)
            }
            val seconds = parseTimestampToSeconds(timestamp)
            val timeUs = seconds * 1_000_000L
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun extractSampleFrames(videoUri: Uri, frameCount: Int = 4): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        val retriever = MediaMetadataRetriever()
        try {
            var setSuccess = false
            try {
                val pfd = context.contentResolver.openFileDescriptor(videoUri, "r")
                if (pfd != null) {
                    retriever.setDataSource(pfd.fileDescriptor)
                    setSuccess = true
                }
            } catch (_: Exception) {}

            if (!setSuccess) {
                retriever.setDataSource(context, videoUri)
            }

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 60000L
            val step = durationMs / (frameCount + 1)

            for (i in 1..frameCount) {
                val timeUs = (i * step) * 1000L
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaled = scaleBitmap(frame, maxDimension = 640)
                    bitmaps.add(scaled)
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
        return bitmaps
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (ratio > 1) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun parseTimestampToSeconds(timestamp: String): Long {
        return try {
            val parts = timestamp.split(":").map { it.trim().toLongOrNull() ?: 0L }
            if (parts.size == 2) {
                parts[0] * 60 + parts[1]
            } else if (parts.size >= 3) {
                parts[0] * 3600 + parts[1] * 60 + parts[2]
            } else {
                parts.firstOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun getActiveApiKey(customKey: String?): String {
        if (!customKey.isNullOrBlank()) return customKey.trim()

        val prefsKey = context.getSharedPreferences("lecturescribe_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "")
            ?.trim()
        if (!prefsKey.isNullOrBlank()) return prefsKey

        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }
}
