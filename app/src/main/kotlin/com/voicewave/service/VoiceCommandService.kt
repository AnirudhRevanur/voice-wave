package com.voicewave.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
import java.io.File

/**
 * Wraps Vosk speech recognition into something easy to use.
 *
 * HOW VOSK WORKS (simple version):
 * 1. You load a Model from a folder in your assets (the ~50MB language model)
 * 2. You create a Recognizer with that model
 * 3. You feed it raw audio bytes from the microphone in a loop
 * 4. It gives you back partial results (while you're speaking) and
 *    a final result (when you stop)
 *
 * The model folder must be in: app/src/main/assets/model/
 * Download from: https://alphacephei.com/vosk/models
 * Recommended: vosk-model-small-en-us-0.15 (~40MB, fast, good enough)
 */
class VoiceCommandService(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isListening = false

    // Audio config — Vosk needs 16kHz mono 16-bit PCM
    private val SAMPLE_RATE = 16000
    private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING) * 4

    suspend fun listen(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // Copy model from assets to internal storage the first time
            // Vosk needs files on disk, not inside the APK zip
            val modelPath = ensureModelExtracted()

            val model = Model(modelPath)
            val recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                BUFFER_SIZE
            )

            val buffer = ByteArray(BUFFER_SIZE)
            audioRecord?.startRecording()
            isListening = true

            // Feed audio to Vosk until stop() is called or we get a final result
            while (isListening) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (bytesRead <= 0) continue

                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    // Got a final result (silence detected after speech)
                    val result = JSONObject(recognizer.result).optString("text", "")
                    if (result.isNotEmpty()) {
                        withContext(Dispatchers.Main) { onFinalResult(result) }
                        isListening = false
                    }
                } else {
                    // Partial result — still speaking
                    val partial = JSONObject(recognizer.partialResult).optString("partial", "")
                    if (partial.isNotEmpty()) {
                        withContext(Dispatchers.Main) { onPartialResult(partial) }
                    }
                }
            }

            recognizer.close()
            model.close()

        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e) }
        } finally {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
    }

    fun stop() {
        isListening = false
    }

    /**
     * Vosk models can't be read directly from APK assets — they need to be
     * real files on disk. This copies the model to internal storage once,
     * then reuses it on subsequent launches.
     *
     * Put your model folder at: app/src/main/assets/model/
     */
    private fun ensureModelExtracted(): String {
        val modelDir = File(context.filesDir, "vosk-model")
        if (modelDir.exists() && modelDir.list()?.isNotEmpty() == true) {
            return modelDir.absolutePath  // Already extracted, skip
        }

        modelDir.mkdirs()
        copyAssetFolder(context, "model", modelDir.absolutePath)
        return modelDir.absolutePath
    }

    private fun copyAssetFolder(context: Context, assetPath: String, destPath: String) {
        val assets = context.assets.list(assetPath) ?: return
        if (assets.isEmpty()) {
            // It's a file, copy it
            context.assets.open(assetPath).use { input ->
                File(destPath).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // It's a folder, recurse
            File(destPath).mkdirs()
            for (asset in assets) {
                copyAssetFolder(
                    context,
                    "$assetPath/$asset",
                    "$destPath/$asset"
                )
            }
        }
    }
}
