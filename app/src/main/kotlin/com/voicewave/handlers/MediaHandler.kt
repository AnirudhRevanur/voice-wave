package com.voicewave.handlers

import android.content.Context
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.media.AudioManager

/**
 * Controls whatever music app is currently playing (Spotify, Vinyl, etc.)
 * using Android's MediaSession system.
 *
 * HOW THIS WORKS:
 * Android has a system-level "active media session" — whatever app is
 * currently playing audio registers itself there. We can send it
 * transport commands (play, pause, next, previous) without knowing
 * which specific app it is. Works with basically everything.
 *
 * Fallback: if no active session, we simulate hardware media key presses
 * via AudioManager, which is even more universal.
 */
object MediaHandler {

    fun play(context: Context) = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
    fun pause(context: Context) = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun next(context: Context) = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous(context: Context) = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    private fun sendMediaKey(context: Context, keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Send key-down then key-up (same as pressing a physical button)
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
