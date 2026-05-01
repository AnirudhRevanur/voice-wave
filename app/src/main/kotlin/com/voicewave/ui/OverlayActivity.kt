package com.voicewave.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.voicewave.R
import com.voicewave.handlers.*
import com.voicewave.parser.IntentParser
import com.voicewave.parser.VoiceCommand
import com.voicewave.service.VoiceCommandService
import kotlinx.coroutines.launch

class OverlayActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var recognizedText: TextView
    private lateinit var waveformView: WaveformView
    private var voiceService: VoiceCommandService? = null

    companion object {
        private const val MIC_PERMISSION_REQUEST = 100
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

        findViewById<View>(R.id.overlay_root).setOnClickListener { finish() }

        if (hasMicPermission()) startListening() else requestMicPermission()
    }

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
                    statusText.text = "Couldn't hear that"
                    finishWithDelay(1200)
                }
            )
        }
    }

    private fun handleCommand(text: String) {
        val command = IntentParser.parse(text)

        when (command) {
            is VoiceCommand.OpenApp -> {
                val ok = AppLaunchHandler.handle(this, command.appName)
                if (!ok) statusText.text = "App not found: \"${command.appName}\""
                finishWithDelay(800)
            }
            is VoiceCommand.Call -> {
                val ok = CallHandler.handle(this, command.contactName)
                if (!ok) statusText.text = "Contact not found: \"${command.contactName}\""
                finishWithDelay(800)
            }
            is VoiceCommand.WhatsApp -> {
                val ok = WhatsAppHandler.handle(this, command.contactName)
                if (!ok) statusText.text = "Contact not found: \"${command.contactName}\""
                finishWithDelay(800)
            }
            is VoiceCommand.YouTube -> {
                WebHandler.youtube(this, command.query)
                finishWithDelay(600)
            }
            is VoiceCommand.Wikipedia -> {
                WebHandler.wikipedia(this, command.query)
                finishWithDelay(600)
            }
            is VoiceCommand.WebSearch -> {
                WebHandler.webSearch(this, command.query)
                finishWithDelay(600)
            }
            is VoiceCommand.FileSearch -> {
                val ok = FileSearchHandler.handle(this, command.query)
                if (!ok) statusText.text = "No files found for \"${command.query}\""
                finishWithDelay(800)
            }
            is VoiceCommand.Calculate -> {
                // Math answers show IN the overlay — no need to open anything
                val result = MathHandler.handle(command.expression)
                if (result != null) {
                    recognizedText.text = result.expression
                    statusText.text = "= ${result.answer}"
                    // Stay open longer so user can read the answer
                    finishWithDelay(4000)
                } else {
                    statusText.text = "Couldn't calculate that"
                    finishWithDelay(1500)
                }
            }
            is VoiceCommand.PlayMusic     -> { MediaHandler.play(this);     finishWithDelay(600) }
            is VoiceCommand.PauseMusic    -> { MediaHandler.pause(this);    finishWithDelay(600) }
            is VoiceCommand.NextTrack     -> { MediaHandler.next(this);     finishWithDelay(600) }
            is VoiceCommand.PreviousTrack -> { MediaHandler.previous(this); finishWithDelay(600) }
            is VoiceCommand.Unknown -> {
                statusText.text = "Didn't understand: \"${command.rawInput}\""
                finishWithDelay(1500)
            }
        }
    }

    private fun finishWithDelay(ms: Long) {
        window.decorView.postDelayed({ finish() }, ms)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceService?.stop()
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), MIC_PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            statusText.text = "Mic permission needed"
            finishWithDelay(2000)
        }
    }
}
