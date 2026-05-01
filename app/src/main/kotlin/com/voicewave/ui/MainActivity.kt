package com.voicewave.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.voicewave.R
import com.voicewave.service.ShakeDetectorService

/**
 * The settings/setup screen.
 * Handles overlay permission, mic permission, and starting/stopping WakeWordService.
 *
 * On Android 14 (API 34), a foreground service of type "microphone" requires:
 *   1. FOREGROUND_SERVICE_MICROPHONE in the manifest  ✓ (already there)
 *   2. foregroundServiceType="microphone" on the <service> tag  ✓ (already there)
 *   3. RECORD_AUDIO granted at runtime BEFORE startForegroundService() is called  ← this
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_MIC = 101
    }

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.service_status)
        toggleButton = findViewById(R.id.toggle_service)
        val overlayButton = findViewById<Button>(R.id.grant_overlay)

        overlayButton.visibility = if (Settings.canDrawOverlays(this)) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }

        overlayButton.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        toggleButton.setOnClickListener {
            if (hasMicPermission()) {
                startWakeWordService()
            } else {
                // Ask for mic permission — startWakeWordService() is called in the result
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_MIC
                )
            }
        }
    }

    private fun startWakeWordService() {
        try {
            startForegroundService(Intent(this, ShakeDetectorService::class.java))
            statusText.text = "✓ Active — shake to talk!\nSay \"Hey Wave\" after a command to chain."
            toggleButton.text = "Stop"
        } catch (e: Exception) {
            statusText.text = "Failed to start: ${e.message}"
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startWakeWordService()
            } else {
                statusText.text = "Microphone permission is required for wake word detection"
            }
        }
    }
}
