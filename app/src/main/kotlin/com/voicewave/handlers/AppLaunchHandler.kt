package com.voicewave.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.voicewave.parser.FuzzyMatcher

object AppLaunchHandler {
    fun handle(context: Context, appName: String): Boolean {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val appMap = installedApps.mapNotNull { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            label to appInfo.packageName
        }.toMap()

        val query = appName.lowercase()

        // 1. Exact match
        appMap[query]?.let { return launchPackage(context, pm, it) }

        // 2. Contains match
        appMap.entries.firstOrNull { (label, _) ->
            label.contains(query) || query.contains(label)
        }?.let { return launchPackage(context, pm, it.value) }

        // 3. Fuzzy match — THIS is what was missing
        // "what's up" → "whatsapp", "spotty" → "spotify", etc.
        val bestMatch = FuzzyMatcher.bestMatch(
            input = query,
            candidates = appMap.keys.toList(),
            threshold = 4  // tweak this if it's too loose or too strict
        )
        bestMatch?.let { return launchPackage(context, pm, appMap[it]!!) }

        return false
    }

    private fun launchPackage(context: Context, pm: PackageManager, packageName: String): Boolean {
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }
}
