package com.voicewave.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Speaker verification using MFCC (Mel-Frequency Cepstral Coefficients) + cosine similarity.
 *
 * HOW IT WORKS:
 * 1. Enrollment: Record ~3 seconds of the owner's voice, extract MFCC features,
 *    average them into a single "voiceprint" vector, save to disk.
 * 2. Verification: Record ~2 seconds of audio after wake word fires, extract MFCCs,
 *    compare cosine similarity to the stored voiceprint.
 *    If similarity >= threshold, it's the owner. Otherwise, reject.
 *
 * MFCCs capture the shape of the vocal tract — essentially what makes your voice
 * sound like *you*, independent of what word you're saying. Two recordings of the
 * same person will have similar MFCC vectors even for different words.
 */
class SpeakerVerifier(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val NUM_MFCC_COEFFS = 13
        private const val NUM_MEL_FILTERS = 26
        private const val FFT_SIZE = 512
        private const val HOP_SIZE = 160           // 10ms at 16kHz
        private const val FRAME_SIZE = 400          // 25ms at 16kHz

        // Cosine similarity threshold: 1.0 = identical, 0.0 = completely different.
        // 0.82 gives a good balance — not so strict it rejects you after a cold,
        // not so loose someone else's voice passes.
        private const val SIMILARITY_THRESHOLD = 0.82f

        private const val ENROLLMENT_DURATION_MS = 3000L
        private const val VERIFICATION_DURATION_MS = 2000L

        private const val VOICEPRINT_FILE = "voiceprint.dat"
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** True if a voiceprint has been saved for this device. */
    val isEnrolled: Boolean
        get() = File(context.filesDir, VOICEPRINT_FILE).exists()

    /**
     * Record the owner's voice for [ENROLLMENT_DURATION_MS] ms, extract their
     * voiceprint, and save it to internal storage.
     *
     * Call this from a coroutine (it suspends while recording).
     * Show the user a "Speak now..." prompt before calling.
     */
    suspend fun enroll(): Boolean = withContext(Dispatchers.IO) {
        val pcm = recordAudio(ENROLLMENT_DURATION_MS)
        if (pcm.isEmpty()) return@withContext false

        val voiceprint = extractMeanMfcc(pcm)
        saveVoiceprint(voiceprint)
        true
    }

    /**
     * Record [VERIFICATION_DURATION_MS] ms of audio and check if it matches
     * the enrolled voiceprint.
     *
     * Returns true if the speaker is the owner, false otherwise.
     * Returns false (safe default) if no voiceprint is enrolled.
     */
    suspend fun verify(): Boolean = withContext(Dispatchers.IO) {
        val stored = loadVoiceprint() ?: return@withContext false
        val pcm = recordAudio(VERIFICATION_DURATION_MS)
        if (pcm.isEmpty()) return@withContext false

        val candidate = extractMeanMfcc(pcm)
        val similarity = cosineSimilarity(stored, candidate)
        similarity >= SIMILARITY_THRESHOLD
    }

    /** Delete the saved voiceprint (for re-enrollment). */
    fun clearEnrollment() {
        File(context.filesDir, VOICEPRINT_FILE).delete()
    }

    // ── Audio Recording ───────────────────────────────────────────────────────

    private fun recordAudio(durationMs: Long): ShortArray {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        val totalSamples = ((SAMPLE_RATE * durationMs) / 1000).toInt()
        val result = ShortArray(totalSamples)
        var offset = 0

        recorder.startRecording()
        while (offset < totalSamples) {
            val toRead = minOf(bufferSize, totalSamples - offset)
            val read = recorder.read(result, offset, toRead)
            if (read <= 0) break
            offset += read
        }
        recorder.stop()
        recorder.release()

        return result
    }

    // ── MFCC Extraction ───────────────────────────────────────────────────────

    /**
     * Extract MFCC vectors frame-by-frame and return their mean across all frames.
     * This collapses a variable-length audio clip to a fixed-size vector — the voiceprint.
     */
    private fun extractMeanMfcc(pcm: ShortArray): FloatArray {
        val frames = mutableListOf<FloatArray>()
        var start = 0

        while (start + FRAME_SIZE <= pcm.size) {
            val frame = FloatArray(FRAME_SIZE) { pcm[start + it] / 32768f }
            applyHannWindow(frame)
            val spectrum = powerSpectrum(frame)
            val melEnergies = melFilterBank(spectrum)
            val mfcc = dct(melEnergies.map { ln(it + 1e-10f) }.toFloatArray())
            frames.add(mfcc)
            start += HOP_SIZE
        }

        if (frames.isEmpty()) return FloatArray(NUM_MFCC_COEFFS)

        // Average across all frames → single voiceprint vector
        val mean = FloatArray(NUM_MFCC_COEFFS)
        for (frame in frames) {
            for (i in mean.indices) mean[i] += frame[i]
        }
        return FloatArray(NUM_MFCC_COEFFS) { mean[it] / frames.size }
    }

    /** Hann window reduces spectral leakage at frame boundaries. */
    private fun applyHannWindow(frame: FloatArray) {
        val n = frame.size
        for (i in frame.indices) {
            frame[i] *= (0.5f * (1 - cos(2 * PI * i / (n - 1)))).toFloat()
        }
    }

    /** Real-valued power spectrum via DFT (simple O(n²) — fine for FRAME_SIZE=400). */
    private fun powerSpectrum(frame: FloatArray): FloatArray {
        val n = FFT_SIZE
        val padded = FloatArray(n).also { frame.copyInto(it, 0, 0, minOf(frame.size, n)) }
        val result = FloatArray(n / 2 + 1)

        for (k in result.indices) {
            var real = 0.0
            var imag = 0.0
            for (t in padded.indices) {
                val angle = 2 * PI * k * t / n
                real += padded[t] * cos(angle)
                imag -= padded[t] * kotlin.math.sin(angle)
            }
            result[k] = (real * real + imag * imag).toFloat()
        }
        return result
    }

    /** Apply triangular mel filter bank to compress the spectrum to NUM_MEL_FILTERS bands. */
    private fun melFilterBank(spectrum: FloatArray): FloatArray {
        val numBins = spectrum.size
        val minMel = hzToMel(80f)
        val maxMel = hzToMel(SAMPLE_RATE / 2f)
        val melPoints = FloatArray(NUM_MEL_FILTERS + 2) { i ->
            melToHz(minMel + i * (maxMel - minMel) / (NUM_MEL_FILTERS + 1))
        }
        val binPoints = FloatArray(melPoints.size) { i ->
            (melPoints[i] / (SAMPLE_RATE / 2f) * (numBins - 1)).toInt().toFloat()
        }

        return FloatArray(NUM_MEL_FILTERS) { m ->
            var energy = 0f
            val start = binPoints[m].toInt()
            val center = binPoints[m + 1].toInt()
            val end = binPoints[m + 2].toInt()

            for (k in start until center) {
                if (k < numBins) energy += spectrum[k] * (k - start).toFloat() / (center - start).coerceAtLeast(1)
            }
            for (k in center until end) {
                if (k < numBins) energy += spectrum[k] * (end - k).toFloat() / (end - center).coerceAtLeast(1)
            }
            energy
        }
    }

    /** Discrete Cosine Transform — extracts the NUM_MFCC_COEFFS most important coefficients. */
    private fun dct(input: FloatArray): FloatArray {
        val n = input.size
        return FloatArray(NUM_MFCC_COEFFS) { k ->
            var sum = 0f
            for (t in input.indices) {
                sum += input[t] * cos(PI * k * (2 * t + 1) / (2 * n)).toFloat()
            }
            sum
        }
    }

    private fun hzToMel(hz: Float) = 2595f * log10(1 + hz / 700f)
    private fun melToHz(mel: Float) = 700f * (Math.pow(10.0, mel / 2595.0).toFloat() - 1)

    // ── Similarity ────────────────────────────────────────────────────────────

    /**
     * Cosine similarity between two vectors.
     * Range: -1 (opposite) to 1 (identical). For voice, 0.82+ means same speaker.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom < 1e-10f) 0f else dot / denom
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun saveVoiceprint(voiceprint: FloatArray) {
        val file = File(context.filesDir, VOICEPRINT_FILE)
        file.outputStream().use { out ->
            val buf = java.nio.ByteBuffer.allocate(voiceprint.size * 4)
            voiceprint.forEach { buf.putFloat(it) }
            out.write(buf.array())
        }
    }

    private fun loadVoiceprint(): FloatArray? {
        val file = File(context.filesDir, VOICEPRINT_FILE)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val buf = java.nio.ByteBuffer.wrap(bytes)
        return FloatArray(bytes.size / 4) { buf.getFloat() }
    }
}
