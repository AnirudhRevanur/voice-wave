package com.voicewave.service

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.voicewave.parser.FuzzyMatcher
import com.voicewave.parser.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class VoiceCommandService(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isListening = false

    private val SAMPLE_RATE = 16000
    private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING) * 4

    // Commands that MIGHT need a second free-listen pass
    // Only actually triggers Pass 2 if nothing followed the command word in Pass 1
    private val NEEDS_QUERY = setOf(
        "open", "launch", "call", "phone", "dial",
        "whatsapp", "youtube", "wikipedia",
        "search", "google", "find", "look"
    )

    suspend fun listen(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val modelPath = ensureModelExtracted()
            val model = Model(modelPath)

            // ── Pass 1: Grammar-locked intent detection ──────────────────────
            val grammar = buildGrammar()
            val intentRecognizer = Recognizer(model, SAMPLE_RATE.toFloat(), grammar)
            val rawIntent = runRecognizer(intentRecognizer, onPartialResult) ?: run {
                withContext(Dispatchers.Main) { onError(Exception("No intent heard")) }
                model.close()
                return@withContext
            }
            intentRecognizer.close()

            // Normalize Pass 1 result immediately
            val intent = TextNormalizer.normalize(rawIntent)

            // ── Decide if we need Pass 2 ─────────────────────────────────────
            // Only trigger if the intent is a single command word with nothing after it
            // "open whatsapp" → already has query, skip Pass 2
            // "open"          → nothing after it, needs Pass 2
            val words = intent.trim().split(" ")
            val firstWord = words.first()
            val hasQuery = words.size > 1
            val needsQuery = !hasQuery && NEEDS_QUERY.any { keyword ->
                FuzzyMatcher.levenshtein(firstWord, keyword) <= 1
            }

            if (!needsQuery) {
                // Single-pass command — done
                withContext(Dispatchers.Main) { onFinalResult(intent) }
                model.close()
                return@withContext
            }

            // ── Pass 2: Free listen for the query ────────────────────────────
            withContext(Dispatchers.Main) {
                onPartialResult("Listening for your query...")
            }

            val freeRecognizer = Recognizer(model, SAMPLE_RATE.toFloat()) // no grammar = free
            val rawQuery = runRecognizer(freeRecognizer, onPartialResult) ?: ""
            freeRecognizer.close()
            model.close()

            // Normalize Pass 2 separately, then combine
            val query = TextNormalizer.normalize(rawQuery)
            val fullCommand = listOf(intent, query)
                .filter { it.isNotEmpty() }
                .joinToString(" ")

            withContext(Dispatchers.Main) { onFinalResult(fullCommand) }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e) }
        } finally {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
    }

    /**
     * Runs a single recognition pass with the given recognizer.
     * Reuses the same AudioRecord so the mic stays open between passes.
     * Returns the recognized text, or null if nothing was heard.
     */
    private suspend fun runRecognizer(
        recognizer: Recognizer,
        onPartialResult: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {

        if (audioRecord == null) {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL, ENCODING, BUFFER_SIZE
            )
            audioRecord?.startRecording()
        }

        isListening = true
        val buffer = ByteArray(BUFFER_SIZE)

        while (isListening) {
            val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: break
            if (bytesRead <= 0) continue

            if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                val result = JSONObject(recognizer.result).optString("text", "")
                if (result.isNotEmpty()) return@withContext result
            } else {
                val partial = JSONObject(recognizer.partialResult).optString("partial", "")
                if (partial.isNotEmpty()) {
                    withContext(Dispatchers.Main) { onPartialResult(partial) }
                }
            }
        }

        return@withContext null
    }

    /**
     * Builds the grammar for Pass 1.
     * Pulls your actual installed app names at runtime + command keywords.
     * Includes common STT variants of app names so Vosk doesn't mangle them.
     */
    private fun buildGrammar(): String {
        val appLabels = context.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .map { context.packageManager.getApplicationLabel(it).toString().lowercase() }

        val commandWords = listOf(
            "open", "launch", "call", "phone", "dial",
            "whatsapp", "what's app", "whatapp",   // STT variants
            "youtube", "you tube", "utube",         // STT variants
            "wikipedia", "google", "search", "find",
            "look", "play", "pause", "next", "previous",
            "back", "skip", "calculate", "convert",
            "what's", "compute", "spotify", "netflix"
        )

        val allWords = (appLabels + commandWords).distinct()
        return JSONArray(allWords).toString()
    }

    fun stop() {
        isListening = false
    }

    private fun ensureModelExtracted(): String {
        val modelDir = File(context.filesDir, "vosk-model")
        if (modelDir.exists() && modelDir.list()?.isNotEmpty() == true) {
            return modelDir.absolutePath
        }
        modelDir.mkdirs()
        copyAssetFolder(context, "model", modelDir.absolutePath)
        return modelDir.absolutePath
    }

    private fun copyAssetFolder(context: Context, assetPath: String, destPath: String) {
        val assets = context.assets.list(assetPath) ?: return
        if (assets.isEmpty()) {
            context.assets.open(assetPath).use { input ->
                File(destPath).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            File(destPath).mkdirs()
            for (asset in assets) {
                copyAssetFolder(context, "$assetPath/$asset", "$destPath/$asset")
            }
        }
    }
}
