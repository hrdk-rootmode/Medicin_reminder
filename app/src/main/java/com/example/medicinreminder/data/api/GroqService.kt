package com.example.medicinreminder.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

interface GroqService {
    @POST("chat/completions")
    suspend fun createChatCompletion(@Body request: GroqChatCompletionRequest): GroqChatCompletionResponse
}

data class GroqChatCompletionRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.2,
    @SerializedName("max_tokens") val maxTokens: Int = 300
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqChatCompletionResponse(
    val choices: List<GroqChoice> = emptyList()
)

data class GroqChoice(
    val message: GroqMessage? = null
)