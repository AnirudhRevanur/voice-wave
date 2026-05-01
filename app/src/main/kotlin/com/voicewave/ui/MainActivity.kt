package com.voicewave.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.voicewave.R
import com.voicewave.service.ShakeDetectorService

/**
 * The settings/setup screen. Nothing fancy.
 * Just a toggle to start/stop the shake detector service,
 * and a button to grant overlay permission (needed for the listening popup).
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.service_status)
        val toggleButton = findViewById<Button>(R.id.toggle_service)
        val overlayButton = findViewById<Button>(R.id.grant_overlay)

        // Show overlay permission button only if not already granted
        overlayButton.visibility = if (Settings.canDrawOverlays(this)) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }

        overlayButton.setOnClickListener {
            // Send user to system settings to grant "display over other apps"
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }

        toggleButton.setOnClickListener {
            val serviceIntent = Intent(this, ShakeDetectorService::class.java)
            // Simple toggle — in a real app you'd track running state properly
            try {
                startForegroundService(serviceIntent)
                statusText.text = "✓ Shake detection active\nShake your phone to test it!"
                toggleButton.text = "Stop"
            } catch (e: Exception) {
                statusText.text = "Failed to start: ${e.message}"
            }
        }
    }
}
