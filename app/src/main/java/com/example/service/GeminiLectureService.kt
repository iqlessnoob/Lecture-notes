package com.example.service

import android.content.Context
import android.graphics.Bitmap
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
import java.util.concurrent.TimeUnit

class GeminiLectureService(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val systemInstructionText = """
        You are a study-notes assistant. You are given a lecture video or lecture reference. Watch, listen, and analyze the entire lecture, then produce structured study notes as JSON matching this schema:

        {
          "title": "string - inferred lecture topic",
          "summary": "string - 3-5 sentence overview",
          "key_concepts": ["string", ...],
          "formulas": [
            { "name": "string", "latex": "string - formula in LaTeX", "explanation": "string" }
          ],
          "diagrams": [
            {
              "label": "string - what the diagram represents",
              "timestamp": "MM:SS - where it appears in the video",
              "description": "string - detailed description of every element, label, and connection/arrow shown",
              "diagram_type": "flowchart | cycle | labeled_diagram | graph | hierarchy | other",
              "mermaid_code": "string - a Mermaid.js recreation of the diagram if it is a flowchart, cycle, hierarchy, or process diagram. Omit if not applicable."
            }
          ],
          "timestamps": [
            { "time": "MM:SS", "topic": "string - what starts being discussed here" }
          ]
        }

        Be precise with formulas — use correct LaTeX syntax (e.g. \\Delta G = \\Delta H - T\\Delta S, \\int_a^b f(x)dx). For diagrams, capture every label and connection so the recreation is faithful, not a vague paraphrase. Provide clean Mermaid syntax for flowcharts/cycles/hierarchies. If the lecture has no diagrams or no formulas, return empty arrays for those fields rather than omitting them. Output ONLY valid JSON matching the schema.
    """.trimIndent()

    suspend fun generateNotesForYouTube(
        youtubeUrl: String,
        subjectSlotName: String,
        customApiKey: String? = null
    ): Result<GeneratedLectureResult> = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey(customApiKey)
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please add your API key in settings or provide one.")
            )
        }

        val prompt = "Lecture video URL: $youtubeUrl\nSubject Course: $subjectSlotName\nPlease watch and analyze this lecture recording, extract all core concepts, formulas in LaTeX, recreated diagrams with Mermaid.js code, and topic timestamps as defined in your instructions."

        callGeminiApi(apiKey, prompt, emptyList())
    }

    suspend fun generateNotesForUploadedVideo(
        videoUri: Uri,
        subjectSlotName: String,
        customApiKey: String? = null
    ): Result<GeneratedLectureResult> = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey(customApiKey)
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please add your API key in settings or provide one.")
            )
        }

        val frames = extractSampleFrames(videoUri, frameCount = 4)
        val prompt = "Subject Course: $subjectSlotName\nHere are sequential key visual frames sampled across this lecture recording. Generate comprehensive structured study notes containing summary, key concepts, formulas in proper LaTeX, recreated diagrams with Mermaid.js syntax, and topic timestamps."

        callGeminiApi(apiKey, prompt, frames)
    }

    suspend fun processInTwoHalvesAndMerge(
        sourceDesc: String,
        subjectSlotName: String,
        customApiKey: String? = null
    ): Result<GeneratedLectureResult> = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey(customApiKey)
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured.")
            )
        }

        val promptPart1 = "Lecture: $sourceDesc (Course: $subjectSlotName) - Processing Part 1 (First Half, 0:00 to 30:00). Extract notes, formulas, diagrams."
        val promptPart2 = "Lecture: $sourceDesc (Course: $subjectSlotName) - Processing Part 2 (Second Half, 30:00 to 60:00). Extract notes, formulas, diagrams."

        val res1 = callGeminiApi(apiKey, promptPart1, emptyList())
        val res2 = callGeminiApi(apiKey, promptPart2, emptyList())

        if (res1.isSuccess && res2.isSuccess) {
            val r1 = res1.getOrThrow()
            val r2 = res2.getOrThrow()
            val merged = GeneratedLectureResult(
                title = r1.title.ifBlank { r2.title },
                summary = "${r1.summary} ${r2.summary}",
                keyConcepts = (r1.keyConcepts + r2.keyConcepts).distinct(),
                formulas = (r1.formulas + r2.formulas).distinctBy { it.name },
                diagrams = (r1.diagrams + r2.diagrams).distinctBy { it.label },
                timestamps = r1.timestamps + r2.timestamps
            )
            Result.success(merged)
        } else if (res1.isSuccess) {
            res1
        } else {
            res2
        }
    }

    private fun callGeminiApi(
        apiKey: String,
        prompt: String,
        frames: List<Bitmap>
    ): Result<GeneratedLectureResult> {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val partsArray = JSONArray()

            // Text prompt part
            val textPart = JSONObject()
            textPart.put("text", prompt)
            partsArray.put(textPart)

            // Image frame parts
            for (frame in frames) {
                val base64 = bitmapToBase64(frame)
                if (base64.isNotEmpty()) {
                    val inlineData = JSONObject()
                    inlineData.put("mimeType", "image/jpeg")
                    inlineData.put("data", base64)

                    val imgPart = JSONObject()
                    imgPart.put("inlineData", inlineData)
                    partsArray.put(imgPart)
                }
            }

            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)

            // System instruction
            val sysParts = JSONArray().put(JSONObject().put("text", systemInstructionText))
            val sysInstructionObj = JSONObject().put("parts", sysParts)

            // Generation config with JSON response format
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.2)
            genConfig.put("topP", 0.95)

            val responseFormat = JSONObject()
            val responseFormatText = JSONObject()
            responseFormatText.put("mimeType", "application/json")
            responseFormat.put("text", responseFormatText)
            genConfig.put("responseFormat", responseFormat)

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
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return Result.failure(Exception("Gemini API error ${response.code}: $errorBody"))
            }

            val respBodyStr = response.body?.string() ?: ""
            val parsedResult = parseGeminiResponse(respBodyStr)
            Result.success(parsedResult)
        } catch (e: Exception) {
            Result.failure(e)
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
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json")
        } else if (str.startsWith("```")) {
            str = str.removePrefix("```")
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```")
        }
        return str.trim()
    }

    fun extractFrameAtTimestamp(videoUri: Uri, timestamp: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
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
            retriever.setDataSource(context, videoUri)
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
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }
}
