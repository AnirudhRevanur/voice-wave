package com.voicewave.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Context
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
            if (isServiceRunning()) {
                stopWaveService()
            } else {
                if (hasMicPermission()) startWaveService()
                else ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_MIC
                )
            }
        }
    }

    // ── Called every time the activity becomes visible ────────────────────────
    // This is the key fix — onResume fires on first launch AND when you come back
    // to the app, so the UI always reflects actual service state
    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        if (isServiceRunning()) {
            statusText.text = "✓ Active — shake to talk!\nSay \"Hey Wave\" after a command to chain."
            toggleButton.text = "Stop"
        } else {
            statusText.text = "Service isn't active"
            toggleButton.text = "Start"
        }
    }

    private fun startWaveService() {
        try {
            startForegroundService(Intent(this, ShakeDetectorService::class.java))
            updateUI()
        } catch (e: Exception) {
            statusText.text = "Failed to start: ${e.message}"
        }
    }

    private fun stopWaveService() {
        stopService(Intent(this, ShakeDetectorService::class.java))
        updateUI()
    }

    // Checks if ShakeDetectorService is actually running in the OS
    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == ShakeDetectorService::class.java.name }
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
                startWaveService()
            } else {
                statusText.text = "Microphone permission is required"
            }
        }
    }
}
