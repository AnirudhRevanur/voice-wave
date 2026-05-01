package com.voicewave.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.WakeWordModel
import com.voicewave.R
import com.voicewave.handlers.*
import com.voicewave.parser.IntentParser
import com.voicewave.parser.VoiceCommand
import com.voicewave.service.SpeakerVerifier
import com.voicewave.service.VoiceCommandService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class OverlayActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var recognizedText: TextView
    private lateinit var waveformView: WaveformView
    private var voiceService: VoiceCommandService? = null
    private lateinit var suggestionBar: View
    private lateinit var suggestionLabel: TextView
    private lateinit var yesButton: Button
    private lateinit var noButton: Button

    // Wake word engine — only lives while the overlay is open
    private var wakeWordEngine: WakeWordEngine? = null
    private var wakeWindowJob: Job? = null
    private lateinit var speakerVerifier: SpeakerVerifier

    // How long after a command completes we keep listening for a follow-up
    private val WAKE_WINDOW_MS = 30_000L

    companion object {
        private const val MIC_PERMISSION_REQUEST = 100
        private const val TAG = "OverlayActivity"

        // Set to false if you haven\'t put the ONNX models in assets/ yet.
        // When false, the wake-word re-trigger window is skipped entirely.
        private const val WAKE_WORD_ENABLED = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContentView(R.layout.activity_overlay)

        statusText = findViewById(R.id.status_text)
        recognizedText = findViewById(R.id.recognized_text)
        waveformView = findViewById(R.id.waveform)
        speakerVerifier = SpeakerVerifier(this)
        suggestionBar   = findViewById(R.id.suggestion_bar)
        suggestionLabel = findViewById(R.id.suggestion_label)
        yesButton       = findViewById(R.id.btn_yes)
        noButton        = findViewById(R.id.btn_no)
        suggestionBar.visibility = View.GONE

        findViewById<View>(R.id.overlay_root).setOnClickListener { finish() }

        if (hasMicPermission()) startListening() else requestMicPermission()
    }

    // ── Voice Command Listening ───────────────────────────────────────────────

    private fun startListening() {
        statusText.text = "Listening..."
        recognizedText.text = ""
        voiceService = VoiceCommandService(this)

        lifecycleScope.launch {
            voiceService?.listen(
                onPartialResult = { partial -> recognizedText.text = partial },
                onFinalResult = { final ->
                    recognizedText.text = final
                    statusText.text = "Got it!"
                    handleCommand(final)
                },
                onError = {
                    statusText.text = "Couldn\'t hear that"
                    finishWithDelay(1200)
                }
            )
        }
    }

    // ── Wake Word Re-trigger Window ───────────────────────────────────────────

    /**
     * After a command is handled, open a short window where saying the wake word
     * triggers another listening session — without needing to shake again.
     *
     * The mic is only active here while the overlay is visible. When the window
     * expires (or the overlay is dismissed), the engine is released immediately.
     */
    private fun startWakeWordWindow() {
        if (!WAKE_WORD_ENABLED) return

        // Don\'t start if models aren\'t present in assets
        val hasModels = try {
            assets.open("melspectrogram.onnx").close()
            assets.open("embedding_model.onnx").close()
            assets.open("hey_wave.onnx").close()
            true
        } catch (e: Exception) {
            Log.d(TAG, "Wake word models not in assets — skipping re-trigger window")
            false
        }
        if (!hasModels) return

        statusText.text = "Say \"Hey Wave\" for another command\n(or tap to dismiss)"

        val models = listOf(
            WakeWordModel(name = "Hey Wave", modelPath = "hey_wave.onnx", threshold = 0.5f)
        )

        try {
            wakeWordEngine = WakeWordEngine(
                context = this,
                models = models,
                detectionCooldownMs = 2000L
            )
            wakeWordEngine?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word engine", e)
            return
        }

        // Collect detections
        lifecycleScope.launch {
            wakeWordEngine?.detections
                ?.catch { e -> Log.e(TAG, "Wake word error", e) }
                ?.collect {
                    Log.d(TAG, "Wake word detected in overlay window")
                    onWakeWordInWindow()
                }
        }

        // Auto-close the window after WAKE_WINDOW_MS
        wakeWindowJob = lifecycleScope.launch {
            delay(WAKE_WINDOW_MS)
            Log.d(TAG, "Wake word window expired")
            stopWakeWordWindow()
            finishWithDelay(0)
        }
    }

    private fun onWakeWordInWindow() {
        lifecycleScope.launch {
            // Verify speaker if enrolled
            if (speakerVerifier.isEnrolled) {
                statusText.text = "Verifying..."
                val isOwner = speakerVerifier.verify()
                if (!isOwner) {
                    Log.d(TAG, "Speaker verification failed in window")
                    statusText.text = "Listening..."
                    return@launch
                }
            }
            stopWakeWordWindow()
            // Brief pause so the wake word audio doesn\'t bleed into the command listen
            delay(300)
            startListening()
        }
    }

    private fun stopWakeWordWindow() {
        wakeWindowJob?.cancel()
        wakeWindowJob = null
        wakeWordEngine?.release()
        wakeWordEngine = null
    }

    // ── Command Handling ─────────────────────────────────────────────────────

    private fun handleCommand(text: String) {
        handleParsedCommand(IntentParser.parse(text))
    }

    private fun handleParsedCommand(command: VoiceCommand) {

        when (command) {
            is VoiceCommand.OpenApp -> {
                val ok = AppLaunchHandler.handle(this, command.appName)
                if (!ok) statusText.text = "App not found: \"${command.appName}\""
                afterCommand()
            }
            is VoiceCommand.Call -> {
                val ok = CallHandler.handle(this, command.contactName)
                if (!ok) statusText.text = "Contact not found: \"${command.contactName}\""
                afterCommand()
            }
            is VoiceCommand.WhatsApp -> {
                val ok = WhatsAppHandler.handle(this, command.contactName)
                if (!ok) statusText.text = "Contact not found: \"${command.contactName}\""
                afterCommand()
            }
            is VoiceCommand.YouTube -> {
                WebHandler.youtube(this, command.query)
                afterCommand()
            }
            is VoiceCommand.Wikipedia -> {
                WebHandler.wikipedia(this, command.query)
                afterCommand()
            }
            is VoiceCommand.WebSearch -> {
                WebHandler.webSearch(this, command.query)
                afterCommand()
            }
            is VoiceCommand.FileSearch -> {
                val ok = FileSearchHandler.handle(this, command.query)
                if (!ok) statusText.text = "No files found for \"${command.query}\""
                afterCommand()
            }
            is VoiceCommand.Calculate -> {
                val result = MathHandler.handle(command.expression)
                if (result != null) {
                    recognizedText.text = result.expression
                    statusText.text = "= ${result.answer}"
                    // Stay open longer for math — user needs to read the answer,
                    // then we open the wake word window so they can chain a command
                    lifecycleScope.launch {
                        delay(3000)
                        startWakeWordWindow()
                    }
                } else {
                    statusText.text = "Couldn\'t calculate that"
                    afterCommand()
                }
            }
            is VoiceCommand.PlayMusic     -> { MediaHandler.play(this);     afterCommand() }
            is VoiceCommand.PauseMusic    -> { MediaHandler.pause(this);    afterCommand() }
            is VoiceCommand.NextTrack     -> { MediaHandler.next(this);     afterCommand() }
            is VoiceCommand.PreviousTrack -> { MediaHandler.previous(this); afterCommand() }
            is VoiceCommand.Suggestion -> {
                // Parser wasn't sure — show "Did you mean X?" and wait for user tap
                showSuggestion(command)
            }
            is VoiceCommand.Unknown -> {
                statusText.text = "Didn\'t understand: \"${command.rawInput}\""
                afterCommand()
            }
        }
    }

    /**
     * Shows the "Did you mean X?" bar and wires up Yes/No buttons.
     * Yes → execute the suggested command. No → show unknown and open wake window.
     */
    private fun showSuggestion(suggestion: VoiceCommand.Suggestion) {
        statusText.text = "Did you mean..."
        suggestionLabel.text = suggestion.displayText
        suggestionBar.visibility = View.VISIBLE

        yesButton.setOnClickListener {
            suggestionBar.visibility = View.GONE
            handleCommand(suggestion.command)   // re-enter handleCommand with the resolved command
        }
        noButton.setOnClickListener {
            suggestionBar.visibility = View.GONE
            statusText.text = "Didn't understand — try again"
            afterCommand()
        }
    }

    // Overload so we can pass a pre-parsed VoiceCommand directly (used by suggestion confirm)
    private fun handleCommand(command: VoiceCommand) {
        // Delegate to the string version by re-using the existing when block via a shim
        handleParsedCommand(command)
    }

    /**
     * Called after every command. Instead of immediately closing the overlay,
     * we open the wake word window for 30 seconds. The user can chain another
     * command by saying "Hey Wave", or tap anywhere to dismiss.
     */
    private fun afterCommand() {
        lifecycleScope.launch {
            delay(600)            // brief pause so the action has time to dispatch
            startWakeWordWindow()
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    private fun finishWithDelay(ms: Long) {
        window.decorView.postDelayed({ finish() }, ms)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceService?.stop()
        stopWakeWordWindow()
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.RECORD_AUDIO), MIC_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            statusText.text = "Mic permission needed"
            finishWithDelay(2000)
        }
    }
}
