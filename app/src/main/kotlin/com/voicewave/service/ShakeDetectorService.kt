package com.voicewave.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.voicewave.ui.OverlayActivity
import kotlin.math.sqrt

/**
 * Runs in the background and listens to the accelerometer.
 * When it detects a shake, it fires OverlayActivity (the listening UI).
 *
 * HOW SHAKE DETECTION WORKS (simple version):
 * The accelerometer gives us X/Y/Z forces on the phone every few milliseconds.
 * We calculate the total force (magnitude). If it spikes above our threshold
 * AND enough time has passed since the last trigger, it's a shake.
 */
class ShakeDetectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Tune these if shake is too sensitive or not sensitive enough
    private val SHAKE_THRESHOLD = 2.7f   // G-force above which we call it a shake
    private val SHAKE_COOLDOWN_MS = 1500L // Don't re-trigger for 1.5 seconds

    private var lastShakeTime = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        // Start listening to the accelerometer
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // If the service is killed by the system, restart it automatically
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent): IBinder? = null

    // Called every time the accelerometer has new data
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate total G-force (magnitude of the 3D vector), minus gravity (9.8)
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

        if (gForce > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now
                onShakeDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // We don't care about this for shake detection
    }

    private fun onShakeDetected() {
        // Buzz the phone so the user knows it triggered
        vibrate()

        // Launch the listening overlay
        val intent = Intent(this, OverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun vibrate() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // Android requires a notification to show when a foreground service is running.
    // This is how the user knows VoiceWave is listening for shakes in the background.
    private fun buildNotification(): Notification {
        val channelId = "voicewave_shake"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "VoiceWave",
            NotificationManager.IMPORTANCE_MIN  // Silent, no sound, minimal UI
        ).apply {
            description = "Shake detection is active"
        }
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("VoiceWave")
            .setContentText("Shake to talk")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
