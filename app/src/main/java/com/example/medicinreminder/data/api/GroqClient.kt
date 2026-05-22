package com.example.medicinreminder.data.api

import android.util.Log
import com.example.medicinreminder.BuildConfig
import com.example.medicinreminder.data.model.MedicineInfo
import com.example.medicinreminder.data.model.MedicineNameSuggestion
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GroqClient {
    private const val BASE_URL = "https://api.groq.com/openai/v1/"
    private const val TAG = "GroqClient"
    private const val MODEL = "llama-3.1-8b-instant"

    fun isConfigured(): Boolean = BuildConfig.GROQ_API_KEY.isNotBlank()

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private fun createService(): GroqService? {
        val apiKey = BuildConfig.GROQ_API_KEY.trim()
        if (apiKey.isBlank()) return null

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqService::class.java)
    }

    suspend fun suggestMedicineNames(query: String, languageTag: String, limit: Int = 5): List<String> {
        val service = createService() ?: return emptyList()
        val prompt = """
            You help a medicine reminder app.
            User language: $languageTag.
            User query: "$query".

            Return STRICT JSON only in this exact shape:
            {"suggestions":[{"name":"medicine name"}]}

            Rules:
            - Suggest only common real medicine names or the most likely corrected spelling.
            - Be conservative. If unsure, return an empty suggestions array.
            - Do not add any explanation or markdown.
        """.trimIndent()

        val response = runCatching {
            service.createChatCompletion(
                GroqChatCompletionRequest(
                    model = MODEL,
                    messages = listOf(
                        GroqMessage(role = "system", content = "Return valid JSON only."),
                        GroqMessage(role = "user", content = prompt)
                    ),
                    temperature = 0.1,
                    maxTokens = 200
                )
            )
        }.getOrNull() ?: return emptyList()

        val content = response.choices.firstOrNull()?.message?.content.orEmpty()
        return parseSuggestionNames(content).take(limit)
    }

    suspend fun summarizeMedicineInfo(info: MedicineInfo, languageTag: String, preferHinglish: Boolean = false, detailed: Boolean = false): MedicineInfo? {
        val service = createService() ?: return null
        val extra = buildString {
            if (detailed) append("Provide a more detailed explanation in addition to short point-wise items.\n")
            if (preferHinglish) append("When outputting Hindi, use Romanized (Hinglish) script instead of Devanagari.\n")
        }

        val prompt = """
            You help a medicine reminder app.
            User language: $languageTag.

            ${if (detailed) "Provide a detailed summary followed by short, clear, point-wise wording for older adults." else "Rewrite the medicine information below into short, clear, point-wise wording for older adults."}
            Keep the meaning based only on the provided trusted text.
            Do not add new facts.
            If the user's language is not English, produce the output in the user's language unless told otherwise. If asked to use Romanized Hindi, output in Hinglish.
            Return STRICT JSON only in this exact shape:
            {
              "displayName":"...",
              "commonUses":["..."],
              "commonSideEffects":["..."],
              "warnings":["..."],
              "storageGuidance":"...",
              "sourceName":"Groq summary of trusted source",
              "disclaimer":"..."
            }

            $extra

            Trusted text:
            displayName: ${info.displayName}
            commonUses: ${info.commonUses.joinToString(" | ")}
            commonSideEffects: ${info.commonSideEffects.joinToString(" | ")}
            warnings: ${info.warnings.joinToString(" | ")}
            storageGuidance: ${info.storageGuidance}
            sourceName: ${info.sourceName}
            disclaimer: ${info.disclaimer}
        """.trimIndent()

        val response = runCatching {
            service.createChatCompletion(
                GroqChatCompletionRequest(
                    model = MODEL,
                    messages = listOf(
                        GroqMessage(role = "system", content = "Return valid JSON only."),
                        GroqMessage(role = "user", content = prompt)
                    ),
                    temperature = 0.15,
                    maxTokens = 400
                )
            )
        }.getOrNull() ?: return null

        val content = response.choices.firstOrNull()?.message?.content.orEmpty()
        return parseMedicineInfo(content, info)
    }

    suspend fun summarizeOcrText(
        ocrText: String,
        medicineName: String,
        languageTag: String,
        preferHinglish: Boolean = false,
        detailed: Boolean = true
    ): MedicineInfo? {
        val service = createService() ?: return null
        val fallbackName = medicineName.trim().ifBlank {
            firstMeaningfulLine(ocrText).ifBlank { "Medicine" }
        }
        val fallback = MedicineInfo(
            displayName = fallbackName,
            commonUses = emptyList(),
            commonSideEffects = emptyList(),
            warnings = emptyList(),
            storageGuidance = "Follow the label or pharmacist guidance.",
            sourceName = "OCR + Groq",
            disclaimer = "Check the pack label and confirm with a pharmacist or doctor before use."
        )

        val prompt = """
            You help a medicine reminder app.
            User language: $languageTag.

            Rewrite the OCR text into a clear medicine note for older adults.
            Use the OCR text as the primary source.
            If trusted medicine names or clues appear, organize them into a clean summary.
            Do not invent facts that are not supported by the text.
            If the OCR text is messy, keep only the most useful label facts.
            If the user's language is not English, produce the output in the user's language unless told otherwise.
            If asked to use Romanized Hindi, output in Hinglish.
            Return STRICT JSON only in this exact shape:
            {
              "displayName":"...",
              "commonUses":["..."],
              "commonSideEffects":["..."],
              "warnings":["..."],
              "storageGuidance":"...",
              "sourceName":"Groq summary of OCR text",
              "disclaimer":"..."
            }

            ${if (detailed) "Give a detailed but easy-to-read summary with short bullets." else "Give a short, easy-to-read summary with bullets."}
            ${if (preferHinglish) "When outputting Hindi, use Romanized Hindi (Hinglish)." else ""}

            OCR text:
            $ocrText

            Medicine name guess:
            $fallbackName
        """.trimIndent()

        val response = runCatching {
            service.createChatCompletion(
                GroqChatCompletionRequest(
                    model = MODEL,
                    messages = listOf(
                        GroqMessage(role = "system", content = "Return valid JSON only."),
                        GroqMessage(role = "user", content = prompt)
                    ),
                    temperature = 0.2,
                    maxTokens = 500
                )
            )
        }.getOrNull() ?: return null

        val content = response.choices.firstOrNull()?.message?.content.orEmpty()
        return parseMedicineInfo(content, fallback)
    }

    private fun parseSuggestionNames(raw: String): List<String> {
        val cleaned = sanitizeJson(raw)
        val names = mutableListOf<String>()

        runCatching {
            val json = JSONObject(cleaned)
            val suggestions = json.optJSONArray("suggestions") ?: JSONArray()
            for (index in 0 until suggestions.length()) {
                val item = suggestions.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isNotBlank()) {
                    names += name
                }
            }
        }.getOrElse {
            cleaned.lines().forEach { line ->
                val candidate = line.replace(Regex("^[\\\"'\\-•\\s]+|[\\\"'\\s]+$"), "").trim()
                if (candidate.isNotBlank()) {
                    names += candidate
                }
            }
        }

        return names.distinctBy { it.lowercase() }
    }

    private fun parseMedicineInfo(raw: String, fallback: MedicineInfo): MedicineInfo? {
        val cleaned = sanitizeJson(raw)
        return runCatching {
            val json = JSONObject(cleaned)
            MedicineInfo(
                displayName = json.optString("displayName").ifBlank { fallback.displayName },
                commonUses = json.optJSONArray("commonUses").toStringList().ifEmpty { fallback.commonUses },
                commonSideEffects = json.optJSONArray("commonSideEffects").toStringList().ifEmpty { fallback.commonSideEffects },
                warnings = json.optJSONArray("warnings").toStringList().ifEmpty { fallback.warnings },
                storageGuidance = json.optString("storageGuidance").ifBlank { fallback.storageGuidance },
                sourceName = json.optString("sourceName").ifBlank { fallback.sourceName },
                disclaimer = json.optString("disclaimer").ifBlank { fallback.disclaimer }
            )
        }.getOrNull()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val values = mutableListOf<String>()
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotBlank()) {
                values += value
            }
        }
        return values
    }

    private fun sanitizeJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("``")) {
            return trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }
        return trimmed
    }

    private fun firstMeaningfulLine(raw: String): String {
        return raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it.length >= 3 }
            .orEmpty()
    }
}