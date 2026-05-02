package com.voicewave.service

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GemmaService(context: Context) {

    private val modelPath = "/data/local/tmp/llm/gemma3-1b-it-int4.task"

    private val llmInference: LlmInference = LlmInference.createFromOptions(
        context,
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(200)
            .build()
    )

    private val SYSTEM_PROMPT = """
        You are a minimal phone assistant called Wave.
        Rules:
        - Answer in ONE short sentence only. Two max if absolutely necessary.
        - No markdown, no bullet points, no headers.
        - No "As an AI..." or "I'm just an assistant..." nonsense.
        - Be direct and slightly dry. Like old Siri.
        - If you don't know something, say so in one sentence.
        User: 
    """.trimIndent()

    suspend fun ask(query: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = "$SYSTEM_PROMPT$query\nWave:"
            llmInference.generateResponse(prompt)
                .trim()
                .lines()
                .first()
                .trim()
        } catch (e: Exception) {
            "Sorry, I couldn't think of an answer."
        }
    }

    fun close() {
        llmInference.close()
    }
}
