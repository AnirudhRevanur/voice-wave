# VoiceWave

VoiceWave is a lightweight, background-running Android voice assistant that you trigger simply by **shaking your phone**. It completely bypasses the need for wake words like "Hey Google" or "Alexa", offering a quick, physical shortcut to hands-free voice commands.

With a beautiful full-screen overlay UI (featuring waveform visualizations and a glowing border), VoiceWave listens to your speech and parses it locally. It uses **fuzzy matching** to figure out what you meant, even if you stammered or the speech-to-text slightly misheard you.

## Features

- **Shake to Wake**: A background `ShakeDetectorService` constantly monitors the accelerometer with an optimized low-battery footprint. Just shake the device to pop open VoiceWave over any app you’re currently using.
- **Fuzzy Intent Parsing**: Uses custom Levenshtein distance-based fuzzy matching. You don't have to speak like a robot; it understands "utube" just as well as "youtube".
- **App Launching**: "Open Spotify", "Launch Settings".
- **Communication**: "Call John", "WhatsApp Sarah".
- **Media Controls**: "Play music", "Next track", "Pause".
- **Web & Knowledge**: "Search YouTube for lofi", "Wikipedia Quantum physics", "Google latest news".
- **File Search**: "Find my file resume.pdf" (Supports local storage search mapping in newer Android versions).
- **Math & Conversions**: "What's the square root of 144", "Convert 50 to...". 

## How it Works

1. **Shake Detection**: `ShakeDetectorService` computes the total G-force from the accelerometer. If it spikes beyond a threshold, it triggers a vibration and launches the...
2. **Overlay UI**: `OverlayActivity` pops up seamlessly as a transparent glowing overlay on top of whatever you are doing.
3. **Speech Recognition**: Voice input is captured and passed to...
4. **Intent Parser**: The `IntentParser` ranks and matches your phrase against a hierarchy of commands.
5. **Handlers**: A specific Handler (`CallHandler`, `MathHandler`, `FileSearchHandler`, etc.) executes the action.

## Setup & Installation

1. Clone this repository.
2. Open the project in Android Studio.
3. Build and install the APK on your device.
4. Open the VoiceWave app and click **Grant Overlay Permission** (Requires "Display over other apps" permission to show the floating UI).
5. Click **Start** to enable Shake Detection.
6. Shake your phone and speak!

## Permissions

VoiceWave requires a few permissions to function as a powerful assistant:
- `RECORD_AUDIO` (For speech recognition)
- `SYSTEM_ALERT_WINDOW` (To display the VoiceWave UI seamlessly over other apps)
- `READ_CONTACTS` & `CALL_PHONE` (For calling contacts via voice)
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` (For local file search commands)
- `QUERY_ALL_PACKAGES` (To know which apps you have installed for launching)

## Built With
- **Kotlin**
- Android SDK (Services, SensorManager, Intents)