package com.voicewave.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Finds an installed app by name and launches it.
 *
 * HOW IT WORKS:
 * Android's PackageManager knows every installed app.
 * We ask it for the full list, then find the best fuzzy match
 * for what the user said. "spotty" → Spotify, "you tube" → YouTube, etc.
 */
object AppLaunchHandler {

    fun handle(context: Context, appName: String): Boolean {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // Build a map of: lowercase app label → package name
        // e.g. "spotify" → "com.spotify.music"
        val appMap = installedApps.mapNotNull { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            label to appInfo.packageName
        }.toMap()

        // Try exact match first
        val exactMatch = appMap[appName.lowercase()]
        if (exactMatch != null) {
            return launchPackage(context, pm, exactMatch)
        }

        // Try "contains" match — user said "spotify", app is "Spotify Music"
        val containsMatch = appMap.entries.firstOrNull { (label, _) ->
            label.contains(appName.lowercase()) || appName.lowercase().contains(label)
        }
        if (containsMatch != null) {
            return launchPackage(context, pm, containsMatch.value)
        }

        // Nothing found
        return false
    }

    private fun launchPackage(context: Context, pm: PackageManager, packageName: String): Boolean {
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }
}
